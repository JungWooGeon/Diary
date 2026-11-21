// Report insights from the user's own (confirmed) emotions: E8 감정 원인 찾기, E13 키워드 구름. Pure functions.
import { GROUPS, GROUP_OF, type GroupKey } from './light'
import { fillSlot } from './ai'
import type { EntryDoc } from './sync'

const WEEK = ['일', '월', '화', '수', '목', '금', '토']
const WEATHER: Record<string, string> = { clear: '맑은', cloudy: '흐린', fog: '안개 낀', rain: '비 오는', snow: '눈 오는', storm: '천둥 치는' }
const SLOT = (h: number) => (h >= 5 && h <= 10 ? '아침' : h >= 11 && h <= 17 ? '낮' : '밤')

function factors(e: EntryDoc): string[] {
  const f = new Set<string>()
  for (const t of e.tags) {
    if (t.kind === 'person') f.add(fillSlot('{person}(이)랑 함께한 날', 'person', t.value))
    else if (t.kind === 'place') f.add(`${t.value}에 간 날`)
    else f.add(`'${t.value}' 있던 날`)
  }
  const w = WEATHER[(e.weather ?? '').split('|')[0]]
  if (w) f.add(`${w} 날`)
  f.add(`${WEEK[new Date(e.date + 'T00:00').getDay()]}요일`)
  if (!e.timeUnknown) f.add(`${SLOT(parseInt(e.time, 10))}에 쓴 기록`)
  return [...f]
}

export interface Cause { label: string; group: GroupKey; diff: number; n: number }

/**
 * E8: for each (factor, light group), how much more often that group lit up when the factor was there than overall.
 * Needs ≥ [minN] entries with the factor and a +[minDiff] gap. ponytail: plain rate difference, no significance test.
 */
export function causes(entries: EntryDoc[], minN = 3, minDiff = 0.2, max = 3): Cause[] {
  const lit = entries.filter(e => e.emotions.some(x => GROUP_OF[x.key]))
  if (lit.length < 6) return []
  const groupsOf = (e: EntryDoc) => new Set(e.emotions.map(x => GROUP_OF[x.key]).filter(Boolean))
  const base = new Map<GroupKey, number>(), byF = new Map<string, EntryDoc[]>()
  for (const e of lit) {
    for (const g of groupsOf(e)) base.set(g, (base.get(g) ?? 0) + 1)
    for (const f of factors(e)) byF.set(f, [...(byF.get(f) ?? []), e])
  }
  const out: Cause[] = []
  for (const [label, es] of byF) {
    if (es.length < minN || es.length === lit.length) continue
    for (const g of GROUPS.map(g => g.key)) {
      const diff = es.filter(e => groupsOf(e).has(g)).length / es.length - (base.get(g) ?? 0) / lit.length
      if (diff >= minDiff) out.push({ label, group: g, diff, n: es.length })
    }
  }
  out.sort((a, b) => b.diff * Math.sqrt(b.n) - a.diff * Math.sqrt(a.n))
  const seen = new Set<string>()
  return out.filter(c => !seen.has(c.label) && seen.add(c.label)).slice(0, max)
}

const JOSA = /(이랑|랑|하고|에서|에게|한테|까지|부터|으로|로|은|는|이|가|을|를|에|의|도|와|과|만|이다|였다|했다|했어|해서|하고)$/
const STOP = new Set('오늘 내일 어제 그냥 진짜 정말 너무 완전 근데 그리고 그래서 하지만 그런데 이제 좀 많이 조금 다시 계속 하루 나는 내가 우리 그거 이거 저거 그게 이게 뭔가 같이 같은 이런 그런 저런 있었다 없었다 했다 했는데 하는 있는 없는 되는 것 거 때 때문 생각 기분 느낌 하나 정도'.split(' '))

// Body words that look like verbs/adjectives/adverbs (먹었다, 걸었고, 괜히…). ponytail: suffix heuristic, a morpheme analyser if it matters.
const PREDICATE = /(다|었|았|는데|고|게|서|며|면|지|히|라|어|아|요|니|던|운|은|는|해|했)$/

/** E13: words that came up often this month — tags first (they are the user's own words), then body words. */
export function keywords(entries: EntryDoc[], max = 18): { word: string; n: number; tag: boolean }[] {
  const count = new Map<string, { n: number; tag: boolean }>()
  const add = (w: string, tag: boolean) => { const c = count.get(w) ?? { n: 0, tag }; c.n++; c.tag ||= tag; count.set(w, c) }
  for (const e of entries) {
    for (const t of e.tags) add(t.value, true)
    const seen = new Set<string>()
    for (const raw of `${e.title} ${e.body}`.split(/[^가-힣A-Za-z0-9]+/)) {
      const w = raw.replace(JOSA, '')
      if ([...w].length >= 2 && !STOP.has(w) && !/\d/.test(w) && !PREDICATE.test(w) && !seen.has(w)) { seen.add(w); add(w, false) }
    }
  }
  return [...count].map(([word, c]) => ({ word, ...c })).filter(x => x.tag || x.n >= 2)
    .sort((a, b) => b.n - a.n || Number(b.tag) - Number(a.tag)).slice(0, max)
}
