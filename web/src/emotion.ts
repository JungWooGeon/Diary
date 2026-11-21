// 감정 AI (W1), pure part: WordPiece tokenizer + decision rule, 1:1 port of core/ai EmotionClassifier.kt
// (pinned by core/ai/src/test/resources/classifier_reference.json), plus sentence/title helpers built on top.
// The model itself runs in emotion.worker.ts (LiteRT.js); this file has no browser dependencies.
import meta from '../../shared/ai/classifier/meta.json'
import labels from '../../shared/ai/classifier/labels.json'
import type { Emo } from './light'

export const LABELS = labels as string[]
export const MAX_LEN = meta.maxLen

interface TokenizerJson { model: { vocab: Record<string, number>; unk_token?: string; continuing_subword_prefix?: string; max_input_chars_per_word?: number }; normalizer?: { lowercase?: boolean } }

const PUNCT = /\p{P}/u, SPACE_SEP = /\p{Zs}/u, CONTROL = /[\p{Cc}\p{Cf}]/u
const isWs = (c: string) => c === ' ' || c === '\t' || c === '\n' || c === '\r' || SPACE_SEP.test(c)
const isCtl = (c: string) => c !== '\t' && c !== '\n' && c !== '\r' && CONTROL.test(c)
const isCjk = (cp: number) => (cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF) || (cp >= 0x20000 && cp <= 0x2A6DF) ||
  (cp >= 0x2A700 && cp <= 0x2CEAF) || (cp >= 0xF900 && cp <= 0xFAFF) || (cp >= 0x2F800 && cp <= 0x2FA1F)
const isPunct = (c: string) => { const cp = c.codePointAt(0)!; return (cp >= 33 && cp <= 47) || (cp >= 58 && cp <= 64) || (cp >= 91 && cp <= 96) || (cp >= 123 && cp <= 126) || PUNCT.test(c) }

/** BERT WordPiece (BertNormalizer lowercase=false, BertPreTokenizer, [CLS] A [SEP]) → token ids, length ≤ maxLen. */
export function makeTokenizer(tj: TokenizerJson, maxLen = MAX_LEN) {
  const vocab = new Map(Object.entries(tj.model.vocab))
  const unk = vocab.get(tj.model.unk_token ?? '[UNK]')!, prefix = tj.model.continuing_subword_prefix ?? '##'
  const maxChars = tj.model.max_input_chars_per_word ?? 100, cls = vocab.get('[CLS]')!, sep = vocab.get('[SEP]')!
  const lower = !!tj.normalizer?.lowercase

  const normalize = (text: string) => {
    let s = ''
    for (const c of text) {
      const cp = c.codePointAt(0)!
      if (cp === 0 || cp === 0xFFFD || (isCtl(c) && !isWs(c))) continue
      s += isWs(c) ? ' ' : isCjk(cp) ? ` ${c} ` : c
    }
    return lower ? s.toLowerCase() : s
  }
  const words = (text: string) => {
    const out: string[] = []; let cur = ''
    for (const c of text) {
      if (c === ' ') { if (cur) out.push(cur); cur = '' }
      else if (isPunct(c)) { if (cur) out.push(cur); cur = ''; out.push(c) }
      else cur += c
    }
    if (cur) out.push(cur)
    return out
  }
  const wordPiece = (word: string) => {
    const cps = [...word]
    if (cps.length > maxChars) return [unk]
    const out: number[] = []
    for (let start = 0; start < cps.length;) {
      let end = cps.length, found: number | undefined
      for (; start < end; end--) { found = vocab.get((start > 0 ? prefix : '') + cps.slice(start, end).join('')); if (found != null) break }
      if (found == null) return [unk]
      out.push(found); start = end
    }
    return out
  }
  return (text: string): number[] => {
    const ids: number[] = []
    for (const w of words(normalize(text))) { ids.push(...wordPiece(w)); if (ids.length >= maxLen - 2) break }
    return [cls, ...ids.slice(0, maxLen - 2), sep]
  }
}

const NEGATED = /(안|않|못|없)\s?$/
const lexicon = Object.entries(meta.lexicon).map(([k, v]) => [LABELS.indexOf(k), new RegExp(v)] as const)
const boostRe = new RegExp(meta.intensityRule.boost), softenRe = new RegExp(meta.intensityRule.soften)
const rint = (v: number) => (Math.abs(v % 1) === 0.5 ? 2 * Math.round(v / 2) : Math.round(v)) // Python round / Math.rint
const UNSURE = LABELS.indexOf('unsure')
export const sigmoid = (x: number) => 1 / (1 + Math.exp(-x))

/** meta.json lexiconRule: +lexiconBoost for emotion words in the text (not right after 안/않/못/없). */
export function boosted(text: string, probs: number[]) {
  const p = [...probs]
  for (const [i, re] of lexicon) {
    const m = re.exec(text)
    if (!m || i < 0 || NEGATED.test(text.slice(Math.max(0, m.index - 2), m.index))) continue
    p[i] = Math.min(0.99, p[i] + meta.lexiconBoost)
  }
  return p
}

/** meta.json decision + intensityRule (EmotionClassifier.decide). probs = sigmoid(logits), before the lexicon boost. */
export function decide(text: string, probs: number[]): Emo[] {
  const p = boosted(text, probs)
  let top = -1; for (let i = 0; i < p.length; i++) if (i !== UNSURE && (top < 0 || p[i] > p[top])) top = i
  if (p[top] < meta.unsureFloor || (UNSURE >= 0 && p[UNSURE] > p[top])) return [{ key: 'unsure', intensity: 3 }]
  const r = meta.intensityRule, th = meta.thresholds as Record<string, number>
  const adj = (boostRe.test(text) ? 1 : 0) - (softenRe.test(text) ? 1 : 0)
  return p.map((v, i) => i).filter(i => i !== UNSURE && (p[i] >= (th[LABELS[i]] ?? 0.5) || i === top))
    .sort((a, b) => p[b] - p[a]).slice(0, 3)
    .map(i => ({ key: LABELS[i], intensity: Math.min(5, Math.max(1, r.base + rint(r.span * Math.min(1, Math.max(0, (p[i] - r.pLow) / (r.pHigh - r.pLow)))) + adj)) }))
}

/** One analysed text: [emotions] = suggestion, [strength] = boosted p of the top emotion (0 for unsure). */
export interface Analysis { emotions: Emo[]; strength: number; embedding?: number[] }
export function analysis(text: string, logits: ArrayLike<number>, embedding?: number[]): Analysis {
  const probs = Array.from(logits, sigmoid), emotions = decide(text, probs)
  const p = boosted(text, probs), k = LABELS.indexOf(emotions[0].key)
  return { emotions, strength: emotions[0].key === 'unsure' ? 0 : p[k], embedding }
}

/** Sentences with their offsets in [text] (for E3 underline). Ends at . ! ? … ~ or a line break. */
export function sentences(text: string): { text: string; start: number }[] {
  const out: { text: string; start: number }[] = []
  for (const m of text.matchAll(/[^.!?…\n]+[.!?…~]*/g)) { const t = m[0].trim(); if ([...t].length >= 4) out.push({ text: t, start: m.index! + m[0].indexOf(t) }) }
  return out
}

/** E6: title from the sentence that carries the main emotion most strongly (else the first one), ≤ 20 chars. */
export function suggestTitle(parts: { text: string; a: Analysis }[], mainKey?: string): string {
  if (!parts.length) return ''
  const best = [...parts].sort((x, y) => score(y) - score(x))[0]
  function score(s: { a: Analysis }) { return (s.a.emotions.some(e => e.key === mainKey) ? 1 : 0) + s.a.strength }
  const t = best.text.replace(/[.!?…~\s]+$/, '').replace(/^["'“”‘’]+|["'“”‘’]+$/g, '').replace(/^(근데|그런데|그래서|그리고|하지만|그러나|아무튼|암튼)\s+/, '')
  const cps = [...t]
  return cps.length <= 20 ? t : cps.slice(0, 19).join('').replace(/\s+\S*$/, '') + '…'
}

// ---------- E4 / E5 / E7: sentence embeddings (only when the model has an embedding output) ----------
export const cosine = (a: number[], b: number[]) => { let d = 0, x = 0, y = 0; for (let i = 0; i < a.length; i++) { d += a[i] * b[i]; x += a[i] * a[i]; y += b[i] * b[i] } return d / (Math.sqrt(x * y) || 1) }
/** Top [k] items most similar to [q] with cosine ≥ [min]. */
export function nearest<T>(q: number[], items: { item: T; v: number[] }[], k = 3, min = 0.6) {
  return items.map(i => ({ item: i.item, s: cosine(q, i.v) })).filter(x => x.s >= min).sort((a, b) => b.s - a.s).slice(0, k)
}
/** E7 "이번 달의 한 줄": the vector closest to all others (highest mean cosine). */
export function central<T>(items: { item: T; v: number[] }[]): T | null {
  let best: T | null = null, bs = -Infinity
  for (const a of items) { let s = 0; for (const b of items) if (b !== a) s += cosine(a.v, b.v); if (s > bs) { bs = s; best = a.item } }
  return best
}

// ---------- model call (LiteRT.js CompiledModel; structural types so this file stays runtime-free) ----------
type LrtTensor = { toTypedArray(): ArrayLike<number>; delete(): void }
export type LrtModel = {
  signatures: Record<string, unknown>; getInputDetails(): { shape: ArrayLike<number> }[]
  run(sig: string, input: Record<string, LrtTensor>): Promise<Record<string, LrtTensor>>
}
export type TensorCtor = new (data: Int32Array, shape: number[]) => LrtTensor

/**
 * Signature "classify": int32 input_ids + attention_mask [1, len] → 21 logits (fixed len: right-pad with 0, mask 0).
 * Sentence embedding (tools/classifier, coming): any other output of "classify", or a separate "embed" signature.
 */
export async function runClassifier(model: LrtModel, Tensor: TensorCtor, ids: number[]): Promise<{ logits: number[]; embedding?: number[] }> {
  const len = model.getInputDetails()[0].shape[1]
  const tok = new Int32Array(len), mask = new Int32Array(len)
  for (let i = 0; i < Math.min(len, ids.length); i++) { tok[i] = ids[i]; mask[i] = 1 }
  const call = async (sig: string) => {
    const a = new Tensor(tok, [1, len]), b = new Tensor(mask, [1, len])
    try {
      const out = await model.run(sig, { input_ids: a, attention_mask: b })
      return Object.values(out).map(t => { const v = Array.from(t.toTypedArray()); t.delete(); return v })
    } finally { a.delete(); b.delete() }
  }
  const outs = await call('classify')
  const logits = outs.find(v => v.length === LABELS.length)!
  let embedding = outs.find(v => v !== logits)
  if (!embedding && 'embed' in model.signatures) embedding = (await call('embed'))[0]
  return { logits, embedding }
}
