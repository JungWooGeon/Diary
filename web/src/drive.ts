// Google sign-in + the slice of Drive REST v3 that sync needs. Diaries go browser → Google only.
// Login: GIS code client → oni-api (docs/v2/SERVER.md) keeps the refresh token, so the login survives the 1h access token.
// Fallback (no VITE_API_BASE, or the server is down): GIS token model, 1h token cached in localStorage.
import { HttpError, type DriveApi, type DriveFile } from './sync'

export const CLIENT_ID: string = import.meta.env.VITE_GOOGLE_CLIENT_ID ?? ''
export const API: string = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '')
const SCOPE = 'https://www.googleapis.com/auth/drive.appdata'
const BASE = 'https://www.googleapis.com/drive/v3'
const UPLOAD = 'https://www.googleapis.com/upload/drive/v3/files'
const FIELDS = 'id,name,md5Checksum,modifiedTime,appProperties'

export class AuthError extends Error {}
/** oni-api unreachable / 5xx. An AuthError: with no live token the user has to reconnect (token model). */
class ServerDown extends AuthError { constructor() { super('로그인 유지 서버에 연결하지 못했어요. 잠시 후 다시 해 주세요.') } }

declare global { interface Window { google?: any } }

const ls = {
  get: (k: string) => { try { return localStorage.getItem(k) } catch { return null } },
  set: (k: string, v: string | null) => { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v) } catch { /* private mode */ } },
}
const KEY = 'oni-drive-token', SKEY = 'oni-session', PKEY = 'oni-entitlement'
let token: { value: string; exp: number } | null = (() => {
  try { const t = JSON.parse(ls.get(KEY) ?? 'null'); return t && t.exp > Date.now() ? t : null } catch { return null }
})()
const keep = (t: typeof token) => { token = t; ls.set(KEY, t && JSON.stringify(t)) }
let session = ls.get(SKEY)
const keepSession = (s: string | null) => { session = s; ls.set(SKEY, s) }
let tokenOnly = false // server down / no refresh token this page load → next click uses the token model
let needConsent = false
let gisLoad: Promise<void> | null = null

function loadGis() {
  if (window.google?.accounts) return Promise.resolve()
  gisLoad ??= new Promise((ok, fail) => {
    const s = document.createElement('script')
    s.src = 'https://accounts.google.com/gsi/client'; s.async = true
    s.onload = () => ok(); s.onerror = () => { gisLoad = null; fail(new Error('Google 로그인 스크립트를 불러오지 못했어요')) }
    document.head.appendChild(s)
  })
  return gisLoad
}

async function api(path: string, init: RequestInit = {}): Promise<{ status: number; ok: boolean; body: any }> {
  if (!API) throw new ServerDown()
  let r: Response
  try { r = await fetch(API + path, { ...init, headers: { 'Content-Type': 'application/json', ...init.headers } }) } catch { tokenOnly = true; throw new ServerDown() }
  const body = await r.json().catch(() => ({}))
  // 5xx (502 google_unavailable …) or 403 origin_not_allowed: server unusable → token-model fallback
  if (r.status >= 500 || body.error === 'origin_not_allowed') { tokenOnly = true; throw new ServerDown() }
  return { status: r.status, ok: r.ok, body }
}
const bearer = () => ({ Authorization: `Bearer ${token!.value}` })
/** Bearer call to oni-api; a 401 unauthorized (token revoked early) gets one silent refresh. */
async function authed(path: string, init: RequestInit = {}, retry = true): ReturnType<typeof api> {
  await ensureToken()
  const r = await api(path, { ...init, headers: { ...init.headers, ...bearer() } })
  if (r.status === 401 && retry && session) { keep(null); return authed(path, init, false) }
  return r
}

let timer: ReturnType<typeof setTimeout> | undefined
const schedule = () => { clearTimeout(timer); if (session && token) timer = setTimeout(() => refresh().catch(() => {}), Math.max(0, token.exp - Date.now())) }
const setToken = (value: string, expiresIn: unknown) => { keep({ value, exp: Date.now() + (Number(expiresIn) - 60) * 1000 }); schedule() } // exp = 1 min early

let refreshing: Promise<void> | null = null
function refresh() {
  return refreshing ??= (async () => {
    const r = await api('/v1/auth/token', { method: 'POST', body: JSON.stringify({ session }) })
    if (r.status === 401 || r.status === 400) { keepSession(null); keep(null); throw new AuthError('다시 연결이 필요해요') } // session_invalid / invalid_*
    if (!r.ok || !r.body.access_token) throw new ServerDown()
    setToken(r.body.access_token, r.body.expires_in)
  })().finally(() => { refreshing = null })
}

async function ensureToken() {
  if (hasToken()) return
  if (session && API) return refresh()
  throw new AuthError('다시 연결이 필요해요')
}

/** On startup: get a fresh access token from the session (silent). Never throws. */
export async function restore() {
  if (!session || !API) return
  if (hasToken()) return schedule()
  await refresh().catch(() => {})
}

/** Must run from a click (popup). */
export async function signIn(hint?: string): Promise<void> {
  if (!CLIENT_ID) throw new AuthError('설정 필요: Google 클라이언트 ID가 없어요')
  await loadGis()
  if (API && !tokenOnly) {
    try { return await codeSignIn(hint) } catch (e) { if (!(e instanceof ServerDown)) throw e }
    // tokenOnly is now set; a second popup can't open without a new click
    throw new AuthError('지금은 로그인 유지 서버에 연결되지 않아요. 한 번 더 눌러 주세요(이번엔 1시간 동안 연결돼요).')
  }
  await tokenSignIn(hint)
}

const popupError = (e: any) => new AuthError(e?.type === 'popup_failed_to_open' ? '로그인 창이 열리지 않았어요. 한 번 더 눌러 주세요.' : e?.message || '로그인 창이 닫혔어요')

async function codeSignIn(hint?: string): Promise<void> {
  const code = await new Promise<string>((ok, fail) => {
    window.google.accounts.oauth2.initCodeClient({
      client_id: CLIENT_ID, scope: SCOPE, ux_mode: 'popup', login_hint: hint,
      select_account: !hint && !needConsent, ...(needConsent ? { prompt: 'consent' } : {}),
      callback: (r: any) => {
        if (r.error || !r.code) return fail(new AuthError(r.error_description || r.error || '로그인 실패'))
        if (r.scope && !String(r.scope).split(' ').includes(SCOPE)) return fail(new AuthError('드라이브 앱 데이터 권한을 허용해 주세요'))
        ok(r.code)
      },
      error_callback: (e: any) => fail(popupError(e)),
    }).requestCode()
  })
  const r = await api('/v1/auth/code', { method: 'POST', body: JSON.stringify({ code }) })
  if (r.body.error === 'no_refresh_token') {
    if (needConsent) { tokenOnly = true; throw new AuthError('로그인을 유지하지 못했어요. 한 번 더 눌러 주세요(이번엔 1시간 동안 연결돼요).') }
    needConsent = true // ask Google again with prompt=consent; if the popup is blocked the next click does it
    return codeSignIn(hint)
  }
  if (!r.ok || !r.body.session || !r.body.access_token) throw new AuthError('로그인하지 못했어요. 잠시 후 다시 해 주세요.')
  needConsent = false
  keepSession(r.body.session); setToken(r.body.access_token, r.body.expires_in)
}

function tokenSignIn(hint?: string) {
  return new Promise<void>((ok, fail) => {
    const client = window.google.accounts.oauth2.initTokenClient({
      client_id: CLIENT_ID, scope: SCOPE, login_hint: hint,
      callback: (r: any) => {
        if (r.error || !r.access_token) return fail(new AuthError(r.error_description || r.error || '로그인 실패'))
        if (!window.google.accounts.oauth2.hasGrantedAllScopes(r, SCOPE)) return fail(new AuthError('드라이브 앱 데이터 권한을 허용해 주세요'))
        setToken(r.access_token, r.expires_in)
        ok()
      },
      error_callback: (e: any) => fail(popupError(e)),
    })
    client.requestAccessToken({ prompt: hint ? '' : 'select_account' })
  })
}

export function signOut() {
  if (session) api('/v1/auth/logout', { method: 'POST', body: JSON.stringify({ session }) }).catch(() => {})
  if (token && window.google) window.google.accounts.oauth2.revoke(token.value, () => {})
  clearTimeout(timer); keepSession(null); keep(null); ls.set(PKEY, null)
}

export const hasToken = () => !!token && token.exp > Date.now()
/** Signed in now, or can get a token silently from the server session. */
export const connected = () => hasToken() || (!!session && !!API)

// ---------- 프리미엄 (account-linked, server only; Drive entitlement.json is user-writable and never trusted) ----------
export const cachedPremium = () => { try { return !!JSON.parse(ls.get(PKEY) ?? 'null')?.premium } catch { return false } }
/** GET /v1/entitlement; on any failure keeps the last answer. */
export async function refreshPremium(): Promise<boolean> {
  if (!API) return cachedPremium()
  try {
    const r = await authed('/v1/entitlement')
    if (r.ok && typeof r.body.premium === 'boolean') ls.set(PKEY, JSON.stringify(r.body))
  } catch { /* offline / server down: last value */ }
  return cachedPremium()
}

/** DELETE /v1/me: server session, refresh token and premium link go away. Drive diaries stay. */
export async function unlinkAccount() {
  const r = await authed('/v1/me', { method: 'DELETE' })
  if (!r.ok) throw new Error('연동을 해제하지 못했어요. 잠시 후 다시 해 주세요.')
  clearTimeout(timer); keepSession(null); ls.set(PKEY, null)
}

async function call(url: string, init: RequestInit = {}, retry = true): Promise<Response> {
  await ensureToken()
  const r = await fetch(url, { ...init, headers: { ...init.headers, ...bearer() } })
  if (r.status === 401) { keep(null); if (retry && session && API) return call(url, init, false); throw new AuthError('다시 연결이 필요해요') }
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
