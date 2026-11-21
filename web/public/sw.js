// Offline shell: pages network-first (fresh deploys win), static assets cache-first. Google APIs are never cached.
// 감정 AI files (model, tokenizer, LiteRT wasm) are cached by emotion.worker.ts in 'oni-ai' — skipped here so they are stored once.
const CACHE = 'oni-v1'
self.addEventListener('install', e => { self.skipWaiting(); e.waitUntil(caches.open(CACHE).then(c => c.addAll(['./', './oni-mark.svg', './manifest.webmanifest']))) })
self.addEventListener('activate', e => e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE && k !== 'oni-ai').map(k => caches.delete(k)))).then(() => self.clients.claim())))
self.addEventListener('fetch', e => {
  const req = e.request, url = new URL(req.url)
  if (req.method !== 'GET' || url.origin !== location.origin || /\/(model|tokenizer|litert_wasm)[^/]*$/.test(url.pathname)) return
  const put = res => { if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)) } return res }
  if (req.mode === 'navigate') e.respondWith(fetch(req).then(put).catch(() => caches.match(req).then(r => r || caches.match('./'))))
  else e.respondWith(caches.match(req).then(r => r || fetch(req).then(put)))
})
