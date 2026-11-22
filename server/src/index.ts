// oni-api — 계약서: docs/v2/SERVER.md. 일기 내용은 받지 않는다. 토큰·코드·비밀값은 절대 로그에 남기지 않는다.

export interface Env {
  ONI: KVNamespace;
  GOOGLE_WEB_CLIENT_ID: string;
  GOOGLE_WEB_CLIENT_SECRET: string;
  PLAY_SA_JSON: string;
  PACKAGE_NAME: string;
  PRODUCT_ID: string;
}

const ORIGINS = ["https://jungwoogeon.github.io", "http://localhost:5173"];
const GOOGLE_TOKEN = "https://oauth2.googleapis.com/token";
const GOOGLE_REVOKE = "https://oauth2.googleapis.com/revoke";
const DRIVE_ABOUT = "https://www.googleapis.com/drive/v3/about?fields=user(permissionId,emailAddress)";
const RECHECK_MS = 7 * 24 * 3600 * 1000;
const MAX_BODY = 8192;

type Sess = { uid: string; rt: string; email: string; createdAt: number };
type Ent = {
  premium: boolean;
  source: "play";
  orderId: string;
  tokenHash: string;
  purchaseToken: string; // 7일 재확인용 (SERVER.md "변경" 참고)
  since: number;
  checkedAt: number;
};

class HttpError extends Error {
  constructor(public status: number, public code: string) {
    super(code);
  }
}

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });

const b64url = (bytes: ArrayBuffer | Uint8Array) =>
  btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

async function sha256(s: string) {
  return b64url(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)));
}

function cors(res: Response, origin: string | null) {
  if (origin) {
    res.headers.set("access-control-allow-origin", origin);
    res.headers.set("vary", "Origin");
  }
  return res;
}

// ---- 입력 검증 ----

async function body(req: Request): Promise<Record<string, unknown>> {
  if (Number(req.headers.get("content-length") ?? 0) > MAX_BODY) throw new HttpError(413, "body_too_large");
  const text = await req.text();
  if (text.length > MAX_BODY) throw new HttpError(413, "body_too_large");
  let v: unknown;
  try {
    v = JSON.parse(text);
  } catch {
    throw new HttpError(400, "invalid_json");
  }
  if (!v || typeof v !== "object" || Array.isArray(v)) throw new HttpError(400, "invalid_json");
  return v as Record<string, unknown>;
}

function str(b: Record<string, unknown>, key: string, max: number, re = /^[\x21-\x7e]+$/) {
  const v = b[key];
  if (typeof v !== "string" || v.length === 0 || v.length > max || !re.test(v)) throw new HttpError(400, `invalid_${key}`);
  return v;
}

const sessionId = (b: Record<string, unknown>) => str(b, "session", 43, /^[A-Za-z0-9_-]{43}$/);

// ---- Google ----

async function googleToken(params: Record<string, string>) {
  return fetch(GOOGLE_TOKEN, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams(params),
  });
}

async function revoke(token: string) {
  try {
    await fetch(GOOGLE_REVOKE, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ token }),
    });
  } catch {
    // 철회 실패는 무시 (세션은 어차피 지운다)
  }
}

async function about(accessToken: string) {
  const r = await fetch(DRIVE_ABOUT, { headers: { authorization: `Bearer ${accessToken}` } });
  if (r.status === 401 || r.status === 403) throw new HttpError(401, "unauthorized");
  if (!r.ok) throw new HttpError(502, "google_unavailable");
  const d = (await r.json()) as { user?: { permissionId?: string; emailAddress?: string } };
  if (!d.user?.permissionId) throw new HttpError(401, "unauthorized");
  return { uid: d.user.permissionId, email: d.user.emailAddress ?? "" };
}

async function bearerUid(req: Request, env: Env) {
  const m = /^Bearer ([\x21-\x7e]{1,4096})$/.exec(req.headers.get("authorization") ?? "");
  if (!m) throw new HttpError(401, "unauthorized");
  const key = `uidc:${await sha256(m[1])}`;
  const cached = await env.ONI.get<{ uid: string }>(key, "json");
  if (cached) return { ...cached, cacheKey: key };
  const u = await about(m[1]);
  await env.ONI.put(key, JSON.stringify({ uid: u.uid }), { expirationTtl: 300 });
  return { uid: u.uid, cacheKey: key };
}

// 서비스 계정 JWT(RS256) → androidpublisher 액세스 토큰. isolate 메모리에 캐시.
let saCache: { token: string; exp: number } | null = null;

async function playAccessToken(env: Env) {
  if (saCache && saCache.exp > Date.now() + 60_000) return saCache.token;
  const sa = JSON.parse(env.PLAY_SA_JSON) as { client_email: string; private_key: string; token_uri?: string };
  const now = Math.floor(Date.now() / 1000);
  const enc = (o: object) => b64url(new TextEncoder().encode(JSON.stringify(o)));
  const unsigned = `${enc({ alg: "RS256", typ: "JWT" })}.${enc({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: GOOGLE_TOKEN,
    iat: now,
    exp: now + 3600,
  })}`;
  const der = Uint8Array.from(atob(sa.private_key.replace(/-----[^-]+-----|\s/g, "")), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const r = await googleToken({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${unsigned}.${b64url(sig)}` });
  if (!r.ok) throw new HttpError(502, "play_auth_failed");
  const d = (await r.json()) as { access_token: string; expires_in: number };
  saCache = { token: d.access_token, exp: Date.now() + d.expires_in * 1000 };
  return d.access_token;
}

type PlayPurchase = { purchaseState?: number; orderId?: string; purchaseTimeMillis?: string };

// null = 구글이 "그런 구매 없음"(400/404/410). 그 외 오류는 throw.
async function playPurchase(env: Env, productId: string, token: string): Promise<PlayPurchase | null> {
  const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(env.PACKAGE_NAME)}/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(token)}`;
  const r = await fetch(url, { headers: { authorization: `Bearer ${await playAccessToken(env)}` } });
  if (r.status === 400 || r.status === 404 || r.status === 410) return null;
  if (!r.ok) throw new HttpError(502, "play_unavailable");
  return (await r.json()) as PlayPurchase;
}

// ---- 세션 ----

async function addSession(env: Env, uid: string, sid: string) {
  // ponytail: KV read-modify-write, 같은 uid 동시 로그인 시 sid 하나 유실 가능(그 세션만 /v1/me 삭제 대상에서 빠짐). 문제되면 Durable Object로.
  const list = (await env.ONI.get<string[]>(`sessions:${uid}`, "json")) ?? [];
  list.push(sid);
  await env.ONI.put(`sessions:${uid}`, JSON.stringify(list));
}

async function removeSession(env: Env, sid: string, sess: Sess) {
  await env.ONI.delete(`sess:${sid}`);
  const list = (await env.ONI.get<string[]>(`sessions:${sess.uid}`, "json")) ?? [];
  const rest = list.filter((s) => s !== sid);
  if (rest.length) await env.ONI.put(`sessions:${sess.uid}`, JSON.stringify(rest));
  else await env.ONI.delete(`sessions:${sess.uid}`);
}

// ---- 핸들러 ----

async function authCode(req: Request, env: Env) {
  const code = str(await body(req), "code", 2048);
  const r = await googleToken({
    code,
    client_id: env.GOOGLE_WEB_CLIENT_ID,
    client_secret: env.GOOGLE_WEB_CLIENT_SECRET,
    redirect_uri: "postmessage",
    grant_type: "authorization_code",
  });
  if (r.status >= 500) throw new HttpError(502, "google_unavailable");
  if (!r.ok) throw new HttpError(400, "code_invalid");
  const t = (await r.json()) as { access_token: string; expires_in: number; refresh_token?: string };
  if (!t.refresh_token) throw new HttpError(400, "no_refresh_token");
  const { uid, email } = await about(t.access_token);
  const sid = b64url(crypto.getRandomValues(new Uint8Array(32)));
  const sess: Sess = { uid, rt: t.refresh_token, email, createdAt: Date.now() };
  await env.ONI.put(`sess:${sid}`, JSON.stringify(sess));
  await addSession(env, uid, sid);
  return json({ session: sid, access_token: t.access_token, expires_in: t.expires_in, email });
}

async function authToken(req: Request, env: Env) {
  const sid = sessionId(await body(req));
  const sess = await env.ONI.get<Sess>(`sess:${sid}`, "json");
  if (!sess) throw new HttpError(401, "session_invalid");
  const r = await googleToken({
    client_id: env.GOOGLE_WEB_CLIENT_ID,
    client_secret: env.GOOGLE_WEB_CLIENT_SECRET,
    refresh_token: sess.rt,
    grant_type: "refresh_token",
  });
  if (r.status === 400 || r.status === 401) {
    // invalid_grant: 사용자가 철회했거나 만료 → 세션 정리
    await removeSession(env, sid, sess);
    throw new HttpError(401, "session_invalid");
  }
  if (!r.ok) throw new HttpError(502, "google_unavailable");
  const t = (await r.json()) as { access_token: string; expires_in: number };
  return json({ access_token: t.access_token, expires_in: t.expires_in });
}

async function authLogout(req: Request, env: Env) {
  const sid = sessionId(await body(req));
  const sess = await env.ONI.get<Sess>(`sess:${sid}`, "json");
  if (sess) {
    await revoke(sess.rt);
    await removeSession(env, sid, sess);
  }
  return json({ ok: true });
}

async function entitlement(req: Request, env: Env) {
  const { uid } = await bearerUid(req, env);
  const ent = await env.ONI.get<Ent>(`ent:${uid}`, "json");
  if (!ent) return json({ premium: false, source: null, since: null });
  if (ent.premium && Date.now() - ent.checkedAt > RECHECK_MS) {
    try {
      const p = await playPurchase(env, env.PRODUCT_ID, ent.purchaseToken);
      ent.premium = p?.purchaseState === 0;
      ent.checkedAt = Date.now();
      await env.ONI.put(`ent:${uid}`, JSON.stringify(ent));
    } catch {
      // 구글 장애면 마지막 값 유지, 다음 요청 때 다시 확인
    }
  }
  return ent.premium ? json({ premium: true, source: "play", since: ent.since }) : json({ premium: false, source: null, since: null });
}

async function entitlementPlay(req: Request, env: Env) {
  const b = await body(req);
  const productId = str(b, "productId", 100);
  const purchaseToken = str(b, "purchaseToken", 1024);
  if (productId !== env.PRODUCT_ID) throw new HttpError(400, "product_invalid");
  const { uid } = await bearerUid(req, env);
  const tokenHash = await sha256(purchaseToken);
  const owner = await env.ONI.get(`tok:${tokenHash}`);
  if (owner && owner !== uid) throw new HttpError(409, "linked_elsewhere");

  const p = await playPurchase(env, productId, purchaseToken);
  if (!p) throw new HttpError(400, "purchase_invalid");
  if (p.purchaseState === 2) throw new HttpError(400, "purchase_pending");
  if (p.purchaseState !== 0) throw new HttpError(400, "purchase_cancelled");

  const orderId = p.orderId ?? `token:${tokenHash}`;
  const since = Number(p.purchaseTimeMillis) || Date.now();
  const ent: Ent = { premium: true, source: "play", orderId, tokenHash, purchaseToken, since, checkedAt: Date.now() };
  await env.ONI.put(`pay:${orderId}`, JSON.stringify({ productId, purchasedAt: since }));
  await env.ONI.put(`tok:${tokenHash}`, uid);
  await env.ONI.put(`ent:${uid}`, JSON.stringify(ent));
  return json({ premium: true, source: "play", since });
}

async function deleteMe(req: Request, env: Env) {
  const { uid, cacheKey } = await bearerUid(req, env);
  for (const sid of (await env.ONI.get<string[]>(`sessions:${uid}`, "json")) ?? []) {
    const sess = await env.ONI.get<Sess>(`sess:${sid}`, "json");
    if (sess) await revoke(sess.rt);
    await env.ONI.delete(`sess:${sid}`);
  }
  await env.ONI.delete(`sessions:${uid}`);
  const ent = await env.ONI.get<Ent>(`ent:${uid}`, "json");
  if (ent) await env.ONI.delete(`tok:${ent.tokenHash}`);
  await env.ONI.delete(`ent:${uid}`);
  await env.ONI.delete(cacheKey);
  // pay:<orderId> 는 uid 없이 5년 보관 (전자상거래법)
  return json({ ok: true });
}

const routes: Record<string, (req: Request, env: Env) => Promise<Response>> = {
  "POST /v1/auth/code": authCode,
  "POST /v1/auth/token": authToken,
  "POST /v1/auth/logout": authLogout,
  "GET /v1/entitlement": entitlement,
  "POST /v1/entitlement/play": entitlementPlay,
  "DELETE /v1/me": deleteMe,
};

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const origin = req.headers.get("origin");
    // Origin 없는 요청(Android 앱)은 허용, 브라우저는 허용 목록만.
    if (origin && !ORIGINS.includes(origin)) return json({ error: "origin_not_allowed" }, 403);
    const path = new URL(req.url).pathname;
    if (req.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "access-control-allow-origin": origin ?? ORIGINS[0],
          "access-control-allow-methods": "GET, POST, DELETE, OPTIONS",
          "access-control-allow-headers": "Authorization, Content-Type",
          "access-control-max-age": "86400",
          vary: "Origin",
        },
      });
    }
    const handler = routes[`${req.method} ${path}`];
    let res: Response;
    try {
      res = handler ? await handler(req, env) : json({ error: "not_found" }, 404);
    } catch (e) {
      if (e instanceof HttpError) res = json({ error: e.code }, e.status);
      else {
        console.error("internal", req.method, path, e instanceof Error ? e.name : "unknown"); // 메시지는 토큰이 섞일 수 있어 안 남김
        res = json({ error: "internal" }, 500);
      }
    }
    return cors(res, origin);
  },
};

export const _test = { resetSaCache: () => (saCache = null) };
