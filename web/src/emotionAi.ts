// Main-thread side of 감정 AI (W1): lazy worker, per-text result cache, state for the UI.
import { useEffect, useState } from 'react'
import type { Analysis } from './emotion'

export type AiState = { on: boolean; phase: 'idle' | 'loading' | 'ready' | 'error'; progress: number; embedding: boolean }
/** First load: model.tflite ≈ 15.7 MB + LiteRT wasm ≈ 3 MB (gzip) + tokenizer ≈ 0.25 MB (gzip). */
export const FIRST_LOAD_MB = 19

const KEY = 'emotionAi'
const saveData = () => !!(navigator as { connection?: { saveData?: boolean } }).connection?.saveData
const readOn = () => { try { const v = localStorage.getItem(KEY); return v ? v === 'on' : !saveData() } catch { return true } }

let state: AiState = { on: readOn(), phase: 'idle', progress: 0, embedding: false }
const listeners = new Set<() => void>()
const emit = (p: Partial<AiState>) => { state = { ...state, ...p }; listeners.forEach(f => f()) }
export const aiState = () => state

export function setEmotionAi(on: boolean) {
  try { localStorage.setItem(KEY, on ? 'on' : 'off') } catch { /* private mode */ }
  emit({ on })
}

let worker: Worker | null = null, seq = 0
const pending = new Map<number, { ok: (r: Analysis[]) => void; fail: (e: Error) => void }>()
const cache = new Map<string, Analysis>()

function getWorker() {
  if (worker) return worker
  worker = new Worker(new URL('./emotion.worker.ts', import.meta.url), { type: 'module' })
  worker.onmessage = (e: MessageEvent<{ id?: number; results?: Analysis[]; error?: string; progress?: number }>) => {
    const d = e.data
    if (d.progress != null) return emit({ progress: d.progress })
    const p = pending.get(d.id!); pending.delete(d.id!)
    if (d.error) { emit({ phase: 'error' }); p?.fail(new Error(d.error)) }
    else { emit({ phase: 'ready', progress: 1, embedding: state.embedding || d.results!.some(r => r.embedding) }); p?.ok(d.results!) }
  }
  worker.onerror = () => { emit({ phase: 'error' }); for (const p of pending.values()) p.fail(new Error('worker')); pending.clear(); worker = null }
  return worker
}

/** Analyse texts (cached by exact text). Rejects when 감정 AI is off or failed to load. */
export async function analyze(texts: string[]): Promise<Analysis[]> {
  if (!state.on) throw new Error('off')
  const todo = [...new Set(texts.filter(t => !cache.has(t)))]
  if (todo.length) {
    if (state.phase === 'idle' || state.phase === 'error') emit({ phase: 'loading' })
    const id = ++seq
    const res = await new Promise<Analysis[]>((ok, fail) => { pending.set(id, { ok, fail }); getWorker().postMessage({ id, texts: todo }) })
    todo.forEach((t, i) => cache.set(t, res[i]))
    if (cache.size > 2000) for (const k of [...cache.keys()].slice(0, 500)) cache.delete(k) // ponytail: FIFO trim, LRU if it matters
  }
  return texts.map(t => cache.get(t)!)
}

export function useAiState() {
  const [, set] = useState(0)
  useEffect(() => { const f = () => set(v => v + 1); listeners.add(f); return () => { listeners.delete(f) } }, [])
  return state
}

/** Debounced analysis of [texts] (E1: ~1 s after typing stops). null while waiting / when off or failed. */
export function useAnalysis(texts: string[], delay = 1000) {
  const [out, setOut] = useState<Analysis[] | null>(null)
  const { on } = useAiState()
  const key = texts.join('\u0000')
  useEffect(() => {
    if (!on || !texts.length) { setOut(null); return }
    let alive = true
    const t = setTimeout(() => analyze(texts).then(r => alive && setOut(r), () => alive && setOut(null)), delay)
    return () => { alive = false; clearTimeout(t) }
  }, [key, on])
  return out
}
