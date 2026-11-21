// 1:1 port of core/light LightMixer.kt (spec in its KDoc). Pinned by shared/light-test-vectors.json.
import emotions from '../../shared/emotions.json'

export type GroupKey = 'bright' | 'calm' | 'flutter' | 'low' | 'worn'
export interface Emo { key: string; intensity: number }
export interface Light { light: string; glow: string; strength: number; off: boolean }

export const GROUPS = emotions.groups as { key: GroupKey; name: string; light: string; glow: string; emotions: { key: string; name: string }[] }[]
export const UNSURE = emotions.unsure
export const QUICK_MOODS = emotions.quickMoods
export const GROUP_OF: Record<string, GroupKey> = Object.fromEntries(GROUPS.flatMap(g => g.emotions.map(e => [e.key, g.key])))
export const DEFAULT_NAMES: Record<string, string> = Object.fromEntries([...GROUPS.flatMap(g => g.emotions.map(e => [e.key, e.name])), [UNSURE.key, UNSURE.name]])
export const groupOf = (k: GroupKey) => GROUPS.find(g => g.key === k)!

export const OFF: Light = { light: UNSURE.light, glow: UNSURE.glow, strength: 0, off: true }
const FULL_STRENGTH_TOTAL = 5

const parse = (hex: string) => parseInt(hex.replace('#', ''), 16)
export const hex = (rgb: number) => '#' + (rgb & 0xffffff).toString(16).toUpperCase().padStart(6, '0')

export function mix(list: Emo[]): Light {
  const lit = list.filter(e => e.key !== 'unsure' && GROUP_OF[e.key])
  const total = lit.reduce((s, e) => s + e.intensity, 0)
  if (total === 0) return OFF
  const s = Math.min(1, total / FULL_STRENGTH_TOTAL)
  const blend = (color: (g: GroupKey) => string) => {
    let l = 0, a = 0, b = 0, c = 0
    for (const e of lit) {
      const lab = fromRgb(parse(color(GROUP_OF[e.key])))
      const w = e.intensity
      l += w * lab[0]; a += w * lab[1]; b += w * lab[2]; c += w * Math.hypot(lab[1], lab[2])
    }
    const hue = Math.atan2(b, a)
    const chroma = Math.hypot(a, b) < 1e-9 ? 0 : c / total * (0.6 + 0.4 * s)
    const lightness = l / total * (0.92 + 0.08 * s)
    return hex(toRgb(lightness, chroma * Math.cos(hue), chroma * Math.sin(hue)))
  }
  return { light: blend(g => groupOf(g).light), glow: blend(g => groupOf(g).glow), strength: Math.fround(s), off: false }
}

const ORDER = GROUPS.map(g => g.key)
/** Largest total intensity; ties go to the earlier group. null when nothing is lit. */
export function dominantGroup(list: Emo[]): GroupKey | null {
  const sums = new Map<GroupKey, number>()
  for (const e of list) { const g = GROUP_OF[e.key]; if (g) sums.set(g, (sums.get(g) ?? 0) + e.intensity) }
  let best: GroupKey | null = null
  for (const [g, v] of sums) {
    const bv = best ? sums.get(best)! : -1
    if (v > bv || (v === bv && ORDER.indexOf(g) < ORDER.indexOf(best!))) best = g
  }
  return best
}

export const mixDay = (entries: Emo[][]) => mix(entries.flat())

export function dayGradient(entries: { time: string; emotions: Emo[] }[]): { position: number; light: Light }[] {
  return entries.map(e => ({ position: Math.fround(minuteOfDay(e.time) / 1440), light: mix(e.emotions) })).sort((a, b) => a.position - b.position)
}

function minuteOfDay(time: string) {
  const [h, m] = time.split(':').map(x => parseInt(x.trim(), 10))
  return Math.min(1439, Math.max(0, h * 60 + m))
}

// Björn Ottosson's OKLab
function fromRgb(rgb: number): number[] {
  const r = toLinear(((rgb >> 16) & 0xff) / 255), g = toLinear(((rgb >> 8) & 0xff) / 255), b = toLinear((rgb & 0xff) / 255)
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
  return [
    0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
  ]
}

function toRgb(L: number, a: number, b: number): number {
  const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
  const m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
  const s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
  const r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
  const g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
  const bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
  return (channel(r) << 16) | (channel(g) << 8) | channel(bl)
}

const channel = (lin: number) => Math.min(255, Math.max(0, Math.round(fromLinear(Math.min(1, Math.max(0, lin))) * 255)))
const toLinear = (c: number) => c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
const fromLinear = (c: number) => c <= 0.0031308 ? c * 12.92 : 1.055 * c ** (1 / 2.4) - 0.055

// Python's round(): ties to even (the character sheet was generated in Python).
const roundHalfEven = (v: number) => (Math.abs(v % 1) === 0.5 ? 2 * Math.round(v / 2) : Math.round(v))

/** sRGB straight mix (character sheet's mix()), t in 0..1. */
export function mixHex(a: string, b: string, t: number) {
  const A = parse(a), B = parse(b)
  const ch = (sh: number) => { const x = (A >> sh) & 255, y = (B >> sh) & 255; return Math.max(0, Math.min(255, roundHalfEven(x + (y - x) * t))) }
  return hex((ch(16) << 16) | (ch(8) << 8) | ch(0))
}
