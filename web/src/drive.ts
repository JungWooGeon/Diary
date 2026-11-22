// Google Identity Services (token model) + the slice of Drive REST v3 that sync needs. Browser → Google only, no server.
import { HttpError, type DriveApi, type DriveFile } from './sync'

export const CLIENT_ID: string = import.meta.env.VITE_GOOGLE_CLIENT_ID ?? ''
const SCOPE = 'https://www.googleapis.com/auth/drive.appdata'
const BASE = 'https://www.googleapis.com/drive/v3'
const UPLOAD = 'https://www.googleapis.com/upload/drive/v3/files'
const FIELDS = 'id,name,md5Checksum,modifiedTime,appProperties'

export class AuthError extends Error {}

declare global { interface Window { google?: any } }

// Kept across reloads until it expires (Google gives 1h; no refresh token without a server).
// ponytail: 1h ceiling, a server-held refresh token removes the reconnect click entirely.
const KEY = 'oni-drive-token'
let token: { value: string; exp: number } | null = (() => {
  try { const t = JSON.parse(localStorage.getItem(KEY) ?? 'null'); return t && t.exp > Date.now() ? t : null } catch { return null }
})()
const keep = (t: typeof token) => { token = t; try { t ? localStorage.setItem(KEY, JSON.stringify(t)) : localStorage.removeItem(KEY) } catch { /* private mode */ } }
let gisLoad: Promise<void> | null = null

function loadGis() {
  gisLoad ??= new Promise((ok, fail) => {
    const s = document.createElement('script')
    s.src = 'https://accounts.google.com/gsi/client'; s.async = true
    s.onload = () => ok(); s.onerror = () => { gisLoad = null; fail(new Error('Google 로그인 스크립트를 불러오지 못했어요')) }
    document.head.appendChild(s)
  })
  return gisLoad
}

/** Must run from a click (popup). Resolves with an access token for drive.appdata. */
export async function signIn(hint?: string): Promise<void> {
  if (!CLIENT_ID) throw new AuthError('설정 필요: Google 클라이언트 ID가 없어요')
  await loadGis()
  await new Promise<void>((ok, fail) => {
    const client = window.google.accounts.oauth2.initTokenClient({
      client_id: CLIENT_ID, scope: SCOPE, login_hint: hint,
      callback: (r: any) => {
        if (r.error || !r.access_token) return fail(new AuthError(r.error_description || r.error || '로그인 실패'))
        if (!window.google.accounts.oauth2.hasGrantedAllScopes(r, SCOPE)) return fail(new AuthError('드라이브 앱 데이터 권한을 허용해 주세요'))
        keep({ value: r.access_token, exp: Date.now() + (Number(r.expires_in) - 60) * 1000 })
        ok()
      },
      error_callback: (e: any) => fail(new AuthError(e?.message || e?.type || '로그인 창이 닫혔어요')),
    })
    client.requestAccessToken({ prompt: hint ? '' : 'select_account' })
  })
}

export function signOut() {
  if (token && window.google) window.google.accounts.oauth2.revoke(token.value, () => {})
  keep(null)
}

export const hasToken = () => !!token && token.exp > Date.now()

async function call(url: string, init: RequestInit = {}): Promise<Response> {
  if (!hasToken()) throw new AuthError('다시 연결이 필요해요')
  const r = await fetch(url, { ...init, headers: { ...init.headers, Authorization: `Bearer ${token!.value}` } })
  if (r.status === 401) { keep(null); throw new AuthError('다시 연결이 필요해요') }
  if (!r.ok) throw new HttpError(r.status, (await r.text()).slice(0, 300))
  return r
}
const json = async <T>(url: string, init?: RequestInit) => (await call(url, init)).json() as Promise<T>
const qs = (o: Record<string, string | undefined>) => new URLSearchParams(Object.entries(o).filter(([, v]) => v != null) as [string, string][]).toString()

function multipart(meta: object, body: string) {
  const b = 'oni' + crypto.randomUUID().replace(/-/g, '')
  return {
    headers: { 'Content-Type': `multipart/related; boundary=${b}` },
    body: `--${b}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${JSON.stringify(meta)}\r\n--${b}\r\nContent-Type: application/json\r\n\r\n${body}\r\n--${b}--`,
  }
}

export const driveApi: DriveApi = {
  async findByEntityId(id) {
    const q = `appProperties has { key='entityId' and value='${id.replace(/'/g, "\\'")}' } and trashed=false`
    return (await json<{ files: DriveFile[] }>(`${BASE}/files?${qs({ spaces: 'appDataFolder', fields: `nextPageToken,files(${FIELDS})`, q })}`)).files[0] ?? null
  },
  listAll: pageToken => json(`${BASE}/files?${qs({ spaces: 'appDataFolder', fields: `nextPageToken,files(${FIELDS})`, pageSize: '1000', pageToken, q: 'trashed=false' })}`),
  create: (name, props, body) => json(`${UPLOAD}?${qs({ uploadType: 'multipart', fields: FIELDS })}`, { method: 'POST', ...multipart({ name, parents: ['appDataFolder'], appProperties: props }, body) }),
  update: (id, name, body) => json(`${UPLOAD}/${id}?${qs({ uploadType: 'multipart', fields: FIELDS })}`, { method: 'PATCH', ...multipart({ name }, body) }),
  metadata: id => json(`${BASE}/files/${id}?${qs({ fields: FIELDS })}`),
  download: async id => (await call(`${BASE}/files/${id}?alt=media`)).text(),
  startPageToken: async () => (await json<{ startPageToken: string }>(`${BASE}/changes/startPageToken`)).startPageToken,
  changes: t => json(`${BASE}/changes?${qs({ pageToken: t, spaces: 'appDataFolder', pageSize: '1000', fields: `nextPageToken,newStartPageToken,changes(fileId,removed,file(${FIELDS}))` })}`),
}

export const accountEmail = async () => (await json<{ user: { emailAddress: string } }>(`${BASE}/about?fields=user(emailAddress)`)).user.emailAddress

/** Photos are only shown on the web (never written): blob URL by photo id, or null. */
const photoCache = new Map<string, Promise<string | null>>()
export function photoUrl(id: string) {
  if (!photoCache.has(id)) photoCache.set(id, (async () => {
    const f = await driveApi.findByEntityId(id); if (!f) return null
    return URL.createObjectURL(await (await call(`${BASE}/files/${f.id}?alt=media`)).blob())
  })().catch(() => { photoCache.delete(id); return null }))
  return photoCache.get(id)!
}
