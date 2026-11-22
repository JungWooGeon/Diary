import { test, expect, vi, beforeEach, afterEach } from 'vitest'

const API = 'https://api.test'
const mem = new Map<string, string>()
const storage = { getItem: (k: string) => mem.get(k) ?? null, setItem: (k: string, v: string) => void mem.set(k, String(v)), removeItem: (k: string) => void mem.delete(k) }
const json = (status: number, body: object) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
type Route = (url: string, init?: RequestInit) => Response | Promise<Response>
let route: Route
const calls: string[] = []

/** Fresh drive.ts module (its state lives at module level), with env + fake fetch/localStorage/window. */
async function load(env: Record<string, string> = { VITE_API_BASE: API, VITE_GOOGLE_CLIENT_ID: 'cid' }) {
  vi.resetModules()
  for (const [k, v] of Object.entries(env)) vi.stubEnv(k, v)
  return import('../src/drive')
}

beforeEach(() => {
  mem.clear(); calls.length = 0
  route = () => { throw new TypeError('Failed to fetch') }
  vi.stubGlobal('localStorage', storage)
  vi.stubGlobal('fetch', vi.fn(async (u: string, init?: RequestInit) => { calls.push(`${init?.method ?? 'GET'} ${u}`); return route(u, init) }))
  vi.stubGlobal('window', {})
})
afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); vi.useRealTimers() })

test('startup: session → silent token, Drive calls use it; refresh ~1 min before expiry', async () => {
  vi.useFakeTimers()
  mem.set('oni-session', 'S1')
  let n = 0
  route = (u, init) => {
    if (u === `${API}/v1/auth/token`) { expect(JSON.parse(String(init!.body))).toEqual({ session: 'S1' }); return json(200, { access_token: 'AT' + ++n, expires_in: 3600 }) }
    if (u.startsWith('https://www.googleapis.com/drive/v3/about')) { expect((init!.headers as any).Authorization).toBe('Bearer AT' + n); return json(200, { user: { emailAddress: 'a@b.c' } }) }
    throw new Error(u)
  }
  const d = await load()
  expect(d.connected()).toBe(true)
  await d.restore()
  expect(d.hasToken()).toBe(true)
  expect(await d.accountEmail()).toBe('a@b.c')
  await vi.advanceTimersByTimeAsync(3540_000) // 59 min: scheduled refresh
  expect(n).toBe(2)
})

test('dead session (401 session_invalid) → no session, reconnect needed', async () => {
  mem.set('oni-session', 'S1')
  route = () => json(401, { error: 'session_invalid' })
  const d = await load()
  await d.restore()
  expect(d.connected()).toBe(false)
  expect(mem.has('oni-session')).toBe(false)
  await expect(d.accountEmail()).rejects.toBeInstanceOf(d.AuthError)
})

test('server unreachable: cached 1h token still works, session kept', async () => {
  mem.set('oni-session', 'S1')
  mem.set('oni-drive-token', JSON.stringify({ value: 'CACHED', exp: Date.now() + 600_000 }))
  route = u => {
    if (u.startsWith(API)) throw new TypeError('Failed to fetch')
    return json(200, { user: { emailAddress: 'a@b.c' } })
  }
  const d = await load()
  await d.restore()
  expect(await d.accountEmail()).toBe('a@b.c')
  expect(mem.get('oni-session')).toBe('S1')
})

test('server unreachable at login → next click falls back to the token model', async () => {
  const codeClient = vi.fn((c: any) => ({ requestCode: () => c.callback({ code: 'C' }) }))
  const tokenClient = vi.fn((c: any) => ({ requestAccessToken: () => c.callback({ access_token: 'TOK', expires_in: 3600 }) }))
  vi.stubGlobal('window', { google: { accounts: { oauth2: { initCodeClient: codeClient, initTokenClient: tokenClient, hasGrantedAllScopes: () => true } } } })
  route = () => { throw new TypeError('Failed to fetch') }
  const d = await load()
  await expect(d.signIn()).rejects.toThrow('한 번 더')
  await d.signIn()
  expect(codeClient).toHaveBeenCalledTimes(1)
  expect(tokenClient).toHaveBeenCalledTimes(1)
  expect(d.hasToken()).toBe(true)
  expect(JSON.parse(mem.get('oni-drive-token')!).value).toBe('TOK')
})

test('no VITE_API_BASE: token model only, server never called', async () => {
  const tokenClient = vi.fn((c: any) => ({ requestAccessToken: () => c.callback({ access_token: 'TOK', expires_in: 3600 }) }))
  vi.stubGlobal('window', { google: { accounts: { oauth2: { initTokenClient: tokenClient, hasGrantedAllScopes: () => true } } } })
  const d = await load({ VITE_API_BASE: '', VITE_GOOGLE_CLIENT_ID: 'cid' })
  await d.signIn()
  expect(await d.refreshPremium()).toBe(false)
  expect(calls).toEqual([])
})

test('code login: no_refresh_token → retried once with prompt=consent, session stored', async () => {
  const configs: any[] = []
  vi.stubGlobal('window', { google: { accounts: { oauth2: { initCodeClient: (c: any) => { configs.push(c); return { requestCode: () => c.callback({ code: 'C' + configs.length }) } } } } } })
  route = (_u, init) => JSON.parse(String(init!.body)).code === 'C1' ? json(400, { error: 'no_refresh_token' }) : json(200, { session: 'S9', access_token: 'AT', expires_in: 3600, email: 'a@b.c' })
  const d = await load()
  await d.signIn()
  expect(configs.map(c => c.prompt)).toEqual([undefined, 'consent'])
  expect(configs[0].select_account).toBe(true)
  expect(mem.get('oni-session')).toBe('S9')
})

test('entitlement: cached, kept when the server is down, cleared on sign-out', async () => {
  mem.set('oni-drive-token', JSON.stringify({ value: 'AT', exp: Date.now() + 600_000 }))
  route = (_u, init) => { expect((init!.headers as any).Authorization).toBe('Bearer AT'); return json(200, { premium: true, source: 'play', since: 1 }) }
  let d = await load()
  expect(d.cachedPremium()).toBe(false)
  expect(await d.refreshPremium()).toBe(true)
  route = () => { throw new TypeError('Failed to fetch') }
  d = await load() // reload: comes from cache
  expect(d.cachedPremium()).toBe(true)
  expect(await d.refreshPremium()).toBe(true)
  d.signOut()
  expect(d.cachedPremium()).toBe(false)
})

test('ads: off when env unset, premium or demo', async () => {
  vi.resetModules()
  const { adsOn } = await import('../src/ads')
  expect(adsOn(false, false)).toBe(false) // env unset
  vi.stubEnv('VITE_ADSENSE_CLIENT', 'ca-pub-1'); vi.stubEnv('VITE_ADSENSE_SLOT_BANNER', '123')
  expect(adsOn(false, false)).toBe(true)
  expect(adsOn(true, false)).toBe(false)
  expect(adsOn(false, true)).toBe(false)
})
