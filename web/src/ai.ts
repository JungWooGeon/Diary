// Basic AI on the web: crisis detection (port of core/ai CrisisDetector.kt) + library replies (TemplateReplyEngine.kt).
// No LLM here: 대화형 AI is app-only for now.
import crisis from '../../shared/ai/crisis.json'
import replies from '../../shared/library/replies.json'
import questions from '../../shared/library/questions.json'
import { GROUP_OF, type Emo } from './light'

// No 'u' flag: the shared patterns use identity escapes (\") that are syntax errors in unicode mode.
const remove = new RegExp(crisis.normalize.removeRegex, 'g')
const excludes = crisis.excludes.map(e => new RegExp(e.regex, 'g'))
const patterns = crisis.patterns.map(p => ({ id: p.id, re: new RegExp(p.regex), w: p.weight ?? 1 }))

export function normalize(text: string) {
  let t = text.normalize('NFC')
  if (crisis.normalize.lowercase) t = t.toLowerCase()
  t = t.replace(remove, '')
  for (const e of excludes) t = t.replace(e, '')
  return t
}

export function detectCrisis(text: string) {
  const t = normalize(text)
  const hits = patterns.filter(p => p.re.test(t))
  const score = hits.reduce((s, p) => s + p.w, 0)
  return { crisis: score >= crisis.threshold, score, hits: hits.map(h => h.id) }
}

export interface Tag { kind: string; value: string; auto?: boolean }
interface Item { id: string; text: string; group?: string; time?: string; minIntensity?: number; maxIntensity?: number; tags?: string[] }

const POSITIVE = new Set(['bright', 'calm', 'flutter'])
const IRONY = /\^\^|퍽이나|네 ?아주 좋|아주 잘~|고맙기도 하셔라|참 잘났/

export function reply(text: string, emotions: Emo[], tags: Tag[], recentIds: Set<string> = new Set(), rnd = Math.random) {
  const top = emotions[0]
  let group: string = (top && GROUP_OF[top.key]) || 'unsure'
  if (IRONY.test(text) && POSITIVE.has(group)) group = 'unsure'
  const intensity = top?.intensity ?? 3
  const pool = (replies as Item[]).filter(r => r.group === group && intensity >= (r.minIntensity ?? 1) && intensity <= (r.maxIntensity ?? 5))
  return pick(pool, tags, recentIds, rnd)
}

export function question(group: string | null, tags: Tag[], time: string | null, rnd = Math.random) {
  const pool = (questions as Item[]).filter(q => (q.group == null || q.group === group) && (q.time == null || q.time === time))
  return pick(pool, tags, new Set(), rnd)
}

export const timeSlot = (h: number) => (h >= 5 && h <= 10 ? 'morning' : h >= 11 && h <= 17 ? 'day' : 'night')

function pick(pool: Item[], tags: Tag[], recent: Set<string>, rnd: () => number) {
  const fillable = pool.filter(i => (i.tags ?? []).every(s => slotValue(s, tags) != null))
  let fresh = fillable.filter(i => !recent.has(i.id)); if (!fresh.length) fresh = fillable
  const withSlot = fresh.filter(i => (i.tags ?? []).length)
  const from = withSlot.length ? withSlot : fresh
  if (!from.length) return null
  const chosen = from[Math.floor(rnd() * from.length)]
  let t = chosen.text
  for (const s of chosen.tags ?? []) t = fillSlot(t, s, slotValue(s, tags)!)
  return { id: chosen.id, text: t }
}

const slotValue = (slot: string, tags: Tag[]) => tags.find(t => t.kind === slot && ['person', 'place', 'activity'].includes(slot))?.value ?? null

/** "{person}(이)랑" + 민준 → 민준이랑, + 엄마 → 엄마랑. */
export function fillSlot(text: string, slot: string, value: string) {
  const last = value.charCodeAt(value.length - 1)
  const hangul = last >= 0xac00 && last <= 0xd7a3
  const batchim = hangul && (last - 0xac00) % 28 !== 0
  const rieul = hangul && (last - 0xac00) % 28 === 8
  return text.replace(new RegExp(`\\{${slot}\\}(\\((이|을|은|과|으)\\)([가-힣]+))?`, 'g'), (_m, _g, open = '', rest = '') => {
    if (!open) return value
    if (open === '으') return value + (batchim && !rieul ? '으' + rest : rest)
    if (['을를', '은는', '과와'].includes(open + rest)) return value + (batchim ? open : rest)
    return value + (batchim ? open + rest : rest)
  })
}
