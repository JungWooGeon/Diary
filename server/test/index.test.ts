import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import worker, { _test, type Env } from "../src/index";

const WEB = "https://jungwoogeon.github.io";
const DAY = 24 * 3600 * 1000;

class MemKV {
  m = new Map<string, string>();
  async get(k: string, type?: string) {
    const v = this.m.get(k);
    return v === undefined ? null : type === "json" ? JSON.parse(v) : v;
  }
  async put(k: string, v: string) {
    this.m.set(k, v);
  }
  async delete(k: string) {
    this.m.delete(k);
  }
}

let saPem = "";
let saPublic: CryptoKey;
beforeAll(async () => {
  const kp = (await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  )) as CryptoKeyPair;
  saPublic = kp.publicKey;
  const der = new Uint8Array((await crypto.subtle.exportKey("pkcs8", kp.privateKey)) as ArrayBuffer);
  saPem = `-----BEGIN PRIVATE KEY-----\n${btoa(String.fromCharCode(...der))}\n-----END PRIVATE KEY-----\n`;
});

// ---- 가짜 구글 ----
type G = {
  users: Record<string, string>; // access token → permissionId
  refresh: Record<string, string>; // refresh token → 새 access token ("" = invalid_grant)
  codes: Record<string, { access_token: string; refresh_token?: string }>;
  purchases: Record<string, { purchaseState: number; orderId: string; purchaseTimeMillis: string }>;
  calls: string[];
  revoked: string[];
};
let g: G;
let kv: MemKV;
let env: Env;

function b64urlDecode(s: string) {
  return Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));
}

const ok = (d: unknown) => new Response(JSON.stringify(d), { status: 200 });
const err = (status: number, error = "x") => new Response(JSON.stringify({ error }), { status });

async function fakeFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const url = String(input);
  const headers = new Headers(init?.headers);
  g.calls.push(url.split("?")[0]);
  if (url === "https://oauth2.googleapis.com/token") {
    const p = new URLSearchParams(String(init?.body));
    const grant = p.get("grant_type");
    if (grant === "authorization_code") {
      expect(p.get("redirect_uri")).toBe("postmessage");
      const c = g.codes[p.get("code")!];
      return c ? ok({ ...c, expires_in: 3599 }) : err(400, "invalid_grant");
    }
    if (grant === "refresh_token") {
      const at = g.refresh[p.get("refresh_token")!];
      return at ? ok({ access_token: at, expires_in: 3599 }) : err(400, "invalid_grant");
    }
    if (grant === "urn:ietf:params:oauth:grant-type:jwt-bearer") {
      const [h, c, s] = p.get("assertion")!.split(".");
      const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", saPublic, b64urlDecode(s), new TextEncoder().encode(`${h}.${c}`));
      const claims = JSON.parse(new TextDecoder().decode(b64urlDecode(c)));
      if (!valid || !String(claims.scope).includes("androidpublisher")) return err(400);
      return ok({ access_token: "sa-token", expires_in: 3600 });
    }
  }
  if (url === "https://oauth2.googleapis.com/revoke") {
    g.revoked.push(new URLSearchParams(String(init?.body)).get("token")!);
    return ok({});
  }
  if (url.startsWith("https://www.googleapis.com/drive/v3/about")) {
    const uid = g.users[headers.get("authorization")!.slice(7)];
    return uid ? ok({ user: { permissionId: uid, emailAddress: `${uid}@gmail.com` } }) : err(401);
  }
  const m = /^https:\/\/androidpublisher\.googleapis\.com\/androidpublisher\/v3\/applications\/com\.pass\.diary\/purchases\/products\/premium\/tokens\/(.+)$/.exec(url);
  if (m) {
    if (headers.get("authorization") !== "Bearer sa-token") return err(401);
    const p = g.purchases[decodeURIComponent(m[1])];
    return p ? ok({ kind: "androidpublisher#productPurchase", ...p }) : err(404);
  }
  throw new Error(`unexpected fetch ${url}`);
}

beforeEach(() => {
  g = { users: {}, refresh: {}, codes: {}, purchases: {}, calls: [], revoked: [] };
  kv = new MemKV();
  env = {
    ONI: kv as unknown as KVNamespace,
    GOOGLE_WEB_CLIENT_ID: "cid",
    GOOGLE_WEB_CLIENT_SECRET: "secret",
    PLAY_SA_JSON: JSON.stringify({ client_email: "sa@x.iam.gserviceaccount.com", private_key: saPem }),
    PACKAGE_NAME: "com.pass.diary",
    PRODUCT_ID: "premium",
  };
  _test.resetSaCache();
  vi.stubGlobal("fetch", vi.fn(fakeFetch));
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

function call(method: string, path: string, opts: { body?: unknown; token?: string; origin?: string | null } = {}) {
  const headers: Record<string, string> = {};
  if (opts.origin !== null) headers.origin = opts.origin ?? WEB;
  if (opts.token) headers.authorization = `Bearer ${opts.token}`;
  if (opts.body !== undefined) headers["content-type"] = "application/json";
  const req = new Request(`https://oni-api.test${path}`, {
    method,
    headers,
    body: opts.body === undefined ? undefined : typeof opts.body === "string" ? opts.body : JSON.stringify(opts.body),
  });
  return worker.fetch(req, env);
}
const j = async (r: Response) => (await r.json()) as any;

function user(uid: string, at = `at-${uid}`) {
  g.users[at] = uid;
  return at;
}

describe("auth", () => {
  it("code → session → token refresh → logout", async () => {
    user("u1", "at1");
    user("u1", "at2");
    g.codes["c1"] = { access_token: "at1", refresh_token: "rt1" };
    g.refresh["rt1"] = "at2";

    const r1 = await call("POST", "/v1/auth/code", { body: { code: "c1" } });
    expect(r1.status).toBe(200);
    expect(r1.headers.get("access-control-allow-origin")).toBe(WEB);
    const s = await j(r1);
    expect(s).toMatchObject({ access_token: "at1", expires_in: 3599, email: "u1@gmail.com" });
    expect(s.session).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(await kv.get("sessions:u1", "json")).toEqual([s.session]);

    const r2 = await call("POST", "/v1/auth/token", { body: { session: s.session } });
    expect(await j(r2)).toEqual({ access_token: "at2", expires_in: 3599 });

    expect(await j(await call("POST", "/v1/auth/logout", { body: { session: s.session } }))).toEqual({ ok: true });
    expect(g.revoked).toEqual(["rt1"]);
    expect(kv.m.has(`sess:${s.session}`)).toBe(false);
    expect((await call("POST", "/v1/auth/token", { body: { session: s.session } })).status).toBe(401);
  });

  it("no refresh token → 400 no_refresh_token", async () => {
    user("u1", "at1");
    g.codes["c1"] = { access_token: "at1" };
    const r = await call("POST", "/v1/auth/code", { body: { code: "c1" } });
    expect(r.status).toBe(400);
    expect(await j(r)).toEqual({ error: "no_refresh_token" });
  });

  it("unknown / revoked session → 401 session_invalid", async () => {
    const r = await call("POST", "/v1/auth/token", { body: { session: "A".repeat(43) } });
    expect(r.status).toBe(401);
    expect(await j(r)).toEqual({ error: "session_invalid" });

    user("u1", "at1");
    g.codes["c1"] = { access_token: "at1", refresh_token: "rt-dead" };
    const { session } = await j(await call("POST", "/v1/auth/code", { body: { code: "c1" } }));
    const r2 = await call("POST", "/v1/auth/token", { body: { session } });
    expect(r2.status).toBe(401);
    expect(await j(r2)).toEqual({ error: "session_invalid" });
    expect(kv.m.has(`sess:${session}`)).toBe(false);
  });

  it("validates bodies", async () => {
    for (const body of ["not json", "[]", { code: 1 }, { code: "" }, { code: "x".repeat(3000) }]) {
      expect((await call("POST", "/v1/auth/code", { body })).status).toBe(400);
    }
    expect((await call("POST", "/v1/auth/token", { body: { session: "short" } })).status).toBe(400);
    expect((await call("POST", "/v1/auth/code", { body: { code: "x".repeat(9000) } })).status).toBe(413);
  });
});

describe("entitlement", () => {
  const buy = (token: string, purchaseState = 0, orderId = "GPA.1") =>
    (g.purchases[token] = { purchaseState, orderId, purchaseTimeMillis: "1700000000000" });

  it("no bearer / bad bearer → 401", async () => {
    expect((await call("GET", "/v1/entitlement")).status).toBe(401);
    expect((await call("GET", "/v1/entitlement", { token: "nope" })).status).toBe(401);
  });

  it("play verify OK → entitlement premium", async () => {
    const at = user("u1");
    buy("pt1");
    expect(await j(await call("GET", "/v1/entitlement", { token: at }))).toEqual({ premium: false, source: null, since: null });
    const r = await call("POST", "/v1/entitlement/play", { token: at, origin: null, body: { productId: "premium", purchaseToken: "pt1" } });
    expect(r.status).toBe(200);
    expect(await j(r)).toEqual({ premium: true, source: "play", since: 1700000000000 });
    expect(await kv.get("pay:GPA.1", "json")).toEqual({ productId: "premium", purchasedAt: 1700000000000 });
    expect(await j(await call("GET", "/v1/entitlement", { token: at }))).toEqual({ premium: true, source: "play", since: 1700000000000 });
    // 같은 uid가 다시 보내도 OK
    expect((await call("POST", "/v1/entitlement/play", { token: at, body: { productId: "premium", purchaseToken: "pt1" } })).status).toBe(200);
  });

  it("pending / unknown / wrong product rejected", async () => {
    const at = user("u1");
    buy("pt-pending", 2);
    let r = await call("POST", "/v1/entitlement/play", { token: at, body: { productId: "premium", purchaseToken: "pt-pending" } });
    expect(r.status).toBe(400);
    expect(await j(r)).toEqual({ error: "purchase_pending" });
    r = await call("POST", "/v1/entitlement/play", { token: at, body: { productId: "premium", purchaseToken: "nope" } });
    expect(await j(r)).toEqual({ error: "purchase_invalid" });
    r = await call("POST", "/v1/entitlement/play", { token: at, body: { productId: "other", purchaseToken: "pt-pending" } });
    expect(await j(r)).toEqual({ error: "product_invalid" });
    expect(kv.m.has("ent:u1")).toBe(false);
  });

  it("refunded → re-check after 7 days → premium false", async () => {
    const at = user("u1");
    buy("pt1");
    await call("POST", "/v1/entitlement/play", { token: at, body: { productId: "premium", purchaseToken: "pt1" } });
    g.purchases["pt1"].purchaseState = 1; // 환불
    const playCalls = () => g.calls.filter((c) => c.includes("androidpublisher")).length;

    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(Date.now() + 6 * DAY);
    expect((await j(await call("GET", "/v1/entitlement", { token: at }))).premium).toBe(true);
    expect(playCalls()).toBe(1);

    vi.setSystemTime(Date.now() + 2 * DAY);
    expect(await j(await call("GET", "/v1/entitlement", { token: at }))).toEqual({ premium: false, source: null, since: null });
    expect(playCalls()).toBe(2);
  });

  it("same purchase on another uid → 409 linked_elsewhere", async () => {
    const a = user("u1");
    const b = user("u2");
    buy("pt1");
    await call("POST", "/v1/entitlement/play", { token: a, body: { productId: "premium", purchaseToken: "pt1" } });
    const r = await call("POST", "/v1/entitlement/play", { token: b, body: { productId: "premium", purchaseToken: "pt1" } });
    expect(r.status).toBe(409);
    expect(await j(r)).toEqual({ error: "linked_elsewhere" });
  });
});

describe("DELETE /v1/me", () => {
  it("removes sessions, ent, tok but keeps pay:", async () => {
    user("u1", "at1");
    g.codes["c1"] = { access_token: "at1", refresh_token: "rt1" };
    g.codes["c2"] = { access_token: "at1", refresh_token: "rt2" };
    const s1 = (await j(await call("POST", "/v1/auth/code", { body: { code: "c1" } }))).session;
    const s2 = (await j(await call("POST", "/v1/auth/code", { body: { code: "c2" } }))).session;
    g.purchases["pt1"] = { purchaseState: 0, orderId: "GPA.9", purchaseTimeMillis: "1700000000000" };
    await call("POST", "/v1/entitlement/play", { token: "at1", body: { productId: "premium", purchaseToken: "pt1" } });

    expect(await j(await call("DELETE", "/v1/me", { token: "at1" }))).toEqual({ ok: true });
    expect([...kv.m.keys()]).toEqual(["pay:GPA.9"]);
    expect(g.revoked.sort()).toEqual(["rt1", "rt2"]);
    expect((await call("POST", "/v1/auth/token", { body: { session: s1 } })).status).toBe(401);
    expect((await call("POST", "/v1/auth/token", { body: { session: s2 } })).status).toBe(401);
    expect(JSON.stringify(await kv.get("pay:GPA.9", "json"))).not.toContain("u1");
  });
});

describe("CORS", () => {
  it("preflight for allowed origins", async () => {
    for (const origin of [WEB, "http://localhost:5173"]) {
      const r = await call("OPTIONS", "/v1/auth/code", { origin });
      expect(r.status).toBe(204);
      expect(r.headers.get("access-control-allow-origin")).toBe(origin);
      expect(r.headers.get("access-control-allow-headers")).toContain("Authorization");
      expect(r.headers.get("access-control-allow-methods")).toContain("DELETE");
    }
  });

  it("rejects other origins (preflight and actual)", async () => {
    for (const method of ["OPTIONS", "GET"]) {
      const r = await call(method, "/v1/entitlement", { origin: "https://evil.example" });
      expect(r.status).toBe(403);
      expect(r.headers.get("access-control-allow-origin")).toBeNull();
    }
    expect(g.calls).toEqual([]);
  });
});
