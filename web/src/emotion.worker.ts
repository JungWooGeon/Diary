/// <reference lib="webworker" />
// W1 감정 AI worker: LiteRT.js + shared/ai/classifier/model.tflite, off the main thread.
// Files are fetched once into Cache Storage ('oni-ai'); asset URLs carry a content hash, so a new model = new entry
// and stale ones are pruned. Diary text never leaves the browser: only these static files are downloaded.
import { loadLiteRt, loadAndCompile, Tensor, supportsFeature } from '@litertjs/core'
import jsUrl from '@litertjs/core/wasm/litert_wasm_internal.js?url'
import wasmUrl from '@litertjs/core/wasm/litert_wasm_internal.wasm?url'
import compatJsUrl from '@litertjs/core/wasm/litert_wasm_compat_internal.js?url'
import compatWasmUrl from '@litertjs/core/wasm/litert_wasm_compat_internal.wasm?url'
import modelUrl from '../../shared/ai/classifier/model.tflite?url'
import tokenizerUrl from '../../shared/ai/classifier/tokenizer.json?url'
import { makeTokenizer, runClassifier, analysis, type LrtModel, type TensorCtor } from './emotion'

declare const self: DedicatedWorkerGlobalScope & Record<string, unknown>
export const AI_CACHE = 'oni-ai'

async function cachedBytes(url: string, onProgress?: (got: number, total: number) => void): Promise<ArrayBuffer> {
  const cache = await caches.open(AI_CACHE).catch(() => null)
  const hit = await cache?.match(url)
  if (hit) return hit.arrayBuffer()
  const res = await fetch(url)
  if (!res.ok || !res.body) throw new Error(`${url}: HTTP ${res.status}`)
  const total = Number(res.headers.get('content-length')) || 0, chunks: Uint8Array[] = []
  let got = 0
  for (const reader = res.body.getReader(); ;) {
    const { done, value } = await reader.read()
    if (done) break
    chunks.push(value); got += value.length; onProgress?.(got, total)
  }
  const buf = await new Blob(chunks as BlobPart[]).arrayBuffer()
  await cache?.put(url, new Response(buf, { headers: { 'content-type': res.headers.get('content-type') ?? '' } })).catch(() => {})
  return buf
}

let ready: Promise<{ model: LrtModel; encode: (t: string) => number[] }> | null = null
const init = () => ready ??= (async () => {
  const relaxed = await supportsFeature('relaxedSimd')
  const [js, wasm] = relaxed ? [jsUrl, wasmUrl] : [compatJsUrl, compatWasmUrl]
  const keep = new Set([js, wasm, modelUrl, tokenizerUrl].map(u => new URL(u, self.location.href).href))
  const sizes = { m: 15.7e6, w: 11e6 }, got = { m: 0, w: 0 }
  const tick = () => self.postMessage({ progress: (got.m + got.w) / (sizes.m + sizes.w) })
  const [src, wasmBuf, modelBuf, tokBuf] = await Promise.all([
    cachedBytes(js), cachedBytes(wasm, (g, t) => { got.w = g; if (t) sizes.w = t; tick() }),
    cachedBytes(modelUrl, (g, t) => { got.m = g; if (t) sizes.m = t; tick() }), cachedBytes(tokenizerUrl)])
  caches.open(AI_CACHE).then(async c => { for (const r of await c.keys()) if (!keep.has(r.url)) c.delete(r) }).catch(() => {})
  // The emscripten loader is a classic script loaded with importScripts, which module workers lack: eval it as a global
  // script instead, and hand it the cached wasm as a blob URL.
  const code = new TextDecoder().decode(src)
  self.importScripts = () => { (0, eval)(code) }
  const blobUrl = URL.createObjectURL(new Blob([wasmBuf], { type: 'application/wasm' }))
  self.Module = { locateFile: () => blobUrl }
  await loadLiteRt(js)
  const model = await loadAndCompile(new Uint8Array(modelBuf), { accelerator: 'wasm' }) as unknown as LrtModel
  return { model, encode: makeTokenizer(JSON.parse(new TextDecoder().decode(tokBuf))) }
})()

// One request at a time keeps the single model's buffers safe.
let queue = Promise.resolve()
self.onmessage = (e: MessageEvent<{ id: number; texts: string[] }>) => {
  const { id, texts } = e.data
  queue = queue.then(async () => {
    try {
      const { model, encode } = await init().catch(err => { ready = null; throw err })
      const results = []
      for (const t of texts) {
        const out = await runClassifier(model, Tensor as unknown as TensorCtor, encode(t))
        results.push(analysis(t, out.logits, out.embedding))
      }
      self.postMessage({ id, results })
    } catch (err) {
      self.postMessage({ id, error: String(err) })
    }
  })
}
