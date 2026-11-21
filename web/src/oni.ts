// Port of 일기장_캐릭터_시안/_source/oni.py oni() → SVG markup for any mixed light. 512×512 box.
import { mixHex as mix } from './light'

const INK = '#2E2622'
const FONT = "'Apple SD Gothic Neo','Pretendard',system-ui,sans-serif"
const WOOD_T = '#DDAE82', WOOD_B = '#B07A52', WOOD_S = '#8C5B3B'
const DOME = 'M104 292 C96 186 160 96 256 96 C352 96 416 186 408 292 C406 316 344 330 256 330 C168 330 106 316 104 292 Z'
const EX = 256, EY = 226
const f1 = (n: number) => n.toFixed(1)

export type Expr = 'default' | 'calm' | 'happy' | 'think' | 'comfort' | 'sad' | 'surprised' | 'sleep' | 'tired' | 'cheer' | 'letter' | 'flutter'
export interface Mood { light: string; glow: string; blush: string; off?: boolean }

export const BRAND: Mood = { light: '#FFF0CC', glow: '#F5A957', blush: '#F59A7C' }
export const BLUSH: Record<string, string> = { bright: '#F6A07A', calm: '#F2A28C', flutter: '#F27C92', low: '#E9A3AE', worn: '#E8A3B4' }
export const OFF_BLUSH = '#E9B8A8'

function defs(u: string, m: Mood, dim: boolean) {
  let { light, glow } = m
  let core = m.off ? '#E2DDD7' : '#FFFDF6'
  let edge = mix(light, glow, .5)
  if (dim) { light = mix(light, '#D9D3E0', .35); edge = mix(glow, '#A9A1B8', .45); core = mix(core, '#ECE6EE', .5) }
  const deep = mix(glow, '#5A3A2A', .45)
  const top = mix(mix(light, '#FFFFFF', .62), '#E9EAF2', .3)
  const shade = mix(glow, '#9C98B4', .7)
  const rimc = mix(glow, '#6E6A86', .55)
  const blur = (id: string, sd: number) => `<filter id="${id}${u}" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="${sd}"/></filter>`
  return `<defs>
<linearGradient id="dm${u}" x1="0" y1="96" x2="0" y2="330" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="${top}"/><stop offset=".42" stop-color="${mix(top, light, .45)}"/><stop offset=".8" stop-color="${light}"/><stop offset="1" stop-color="${edge}"/></linearGradient>
<radialGradient id="ig${u}" cx="256" cy="306" r="176" gradientUnits="userSpaceOnUse" gradientTransform="translate(256 306) scale(1 .82) translate(-256 -306)"><stop offset="0" stop-color="${core}"/><stop offset=".38" stop-color="${mix(core, light, .6)}" stop-opacity=".95"/><stop offset=".7" stop-color="${light}" stop-opacity=".45"/><stop offset="1" stop-color="${light}" stop-opacity="0"/></radialGradient>
<radialGradient id="vs${u}" cx="200" cy="168" r="250" gradientUnits="userSpaceOnUse"><stop offset=".55" stop-color="${shade}" stop-opacity="0"/><stop offset="1" stop-color="${shade}" stop-opacity=".42"/></radialGradient>
<linearGradient id="eg${u}" x1="0" y1="96" x2="0" y2="300" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="${rimc}" stop-opacity=".55"/><stop offset=".55" stop-color="${rimc}" stop-opacity=".18"/><stop offset="1" stop-color="${rimc}" stop-opacity="0"/></linearGradient>
<radialGradient id="ha${u}" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="${glow}" stop-opacity=".9"/><stop offset=".45" stop-color="${glow}" stop-opacity=".5"/><stop offset=".72" stop-color="${glow}" stop-opacity=".16"/><stop offset="1" stop-color="${glow}" stop-opacity="0"/></radialGradient>
<linearGradient id="hd${u}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${mix(light, '#FFFFFF', .5)}"/><stop offset=".55" stop-color="${light}"/><stop offset="1" stop-color="${mix(edge, glow, .55)}"/></linearGradient>
<linearGradient id="wn${u}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="${WOOD_S}"/><stop offset=".35" stop-color="${WOOD_T}"/><stop offset=".55" stop-color="${mix(WOOD_T, '#FFFFFF', .18)}"/><stop offset="1" stop-color="${WOOD_S}"/></linearGradient>
<linearGradient id="ws${u}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="${WOOD_S}"/><stop offset=".4" stop-color="${WOOD_B}"/><stop offset="1" stop-color="${mix(WOOD_S, '#3A2418', .25)}"/></linearGradient>
<radialGradient id="wt${u}" cx=".5" cy=".3" r=".7"><stop offset="0" stop-color="${mix(WOOD_T, light, .55)}"/><stop offset=".55" stop-color="${WOOD_T}"/><stop offset="1" stop-color="${WOOD_B}"/></radialGradient>
<linearGradient id="br${u}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F3D9A4"/><stop offset="1" stop-color="#C9A063"/></linearGradient>
<linearGradient id="tp${u}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".38"/><stop offset=".5" stop-color="#fff" stop-opacity="0"/></linearGradient>
<clipPath id="dc${u}"><path d="${DOME}"/></clipPath>
${blur("bs", 2.5)}
${blur("bm", 5)}
${blur("bl", 9)}
${blur("bg", 16)}
<filter id="ds${u}" x="-50%" y="-50%" width="200%" height="200%"><feDropShadow dx="0" dy="2.5" stdDeviation="2.5" flood-color="${deep}" flood-opacity=".38"/></filter>
</defs>`
}

function face(u: string, expr: Expr, blush: string, dim: boolean) {
  const s = 1, gap = 44, ex = EX, ey = EY, rx = 13.5, ry = 18.5
  const out: string[] = []
  const eye = (x: number, y: number, look: [number, number] = [0, 0], k = 1, wet = false) =>
    `<g class="eye" style="transform-origin:${x}px ${y}px;transform-box:view-box">` +
    `<ellipse cx="${x}" cy="${y}" rx="${f1(rx * k)}" ry="${f1(ry * k)}" fill="${INK}"/>` +
    `<circle cx="${f1(x + 4 * s * k + look[0])}" cy="${f1(y - 6.5 * s * k + look[1])}" r="${f1(rx * (wet ? .44 : .38) * k)}" fill="#fff"/>` +
    `<circle cx="${f1(x - 3.8 * s * k + look[0])}" cy="${f1(y + 6.5 * s * k + look[1])}" r="${f1((wet ? 3 : 2.1) * s)}" fill="#fff" opacity="${wet ? .85 : .6}"/></g>`
  const arc = (x: number, y: number, up = true, w = 13) => {
    const k = up ? 1 : -1
    return `<path d="M${f1(x - w * s)} ${f1(y + 5 * s * k)} Q${x} ${f1(y - 12 * s * k)} ${f1(x + w * s)} ${f1(y + 5 * s * k)}" stroke="${INK}" stroke-width="${f1(6.5 * s)}" stroke-linecap="round" fill="none"/>`
  }
  const brow = (x: number, y: number, tilt: number) => {
    const d = x < ex ? 1 : -1
    return `<path d="M${f1(x - 11)} ${f1(y + tilt * d)} L${f1(x + 11)} ${f1(y - tilt * d)}" stroke="${INK}" stroke-width="4.5" stroke-linecap="round" opacity=".75"/>`
  }
  const smile = `<path d="M${f1(ex - 10)} ${f1(ey + 26)} Q${ex} ${f1(ey + 35)} ${f1(ex + 10)} ${f1(ey + 26)}" stroke="${INK}" stroke-width="5.5" stroke-linecap="round" fill="none"/>`
  const openm = `<path d="M${ex - 14} ${ey + 23} Q${ex} ${ey + 21} ${ex + 14} ${ey + 23} Q${ex + 12} ${ey + 43} ${ex} ${ey + 43} Q${ex - 12} ${ey + 43} ${ex - 14} ${ey + 23}Z" fill="${INK}"/>` +
    `<ellipse cx="${ex}" cy="${ey + 37}" rx="7.5" ry="4.5" fill="#F07C86"/>`
  const bop = dim ? .35 : .62
  for (const k of [-1, 1]) out.push(`<ellipse cx="${f1(ex + k * (gap + 26))}" cy="${f1(ey + 28)}" rx="20.0" ry="10.5" fill="${blush}" opacity="${expr !== 'flutter' ? bop : .85}" filter="url(#bs${u})"/>`)
  const L = ex - gap, R = ex + gap
  switch (expr) {
    case 'default': out.push(eye(L, ey), eye(R, ey), smile); break
    case 'calm': out.push(arc(L, ey + 2), arc(R, ey + 2), smile); break
    case 'happy': out.push(arc(L, ey), arc(R, ey), openm); break
    case 'think': out.push(eye(L + 4, ey - 2, [3, -4]), eye(R + 4, ey - 2, [3, -4]),
      `<path d="M${ex - 7} ${ey + 29} Q${ex + 2} ${ey + 24} ${ex + 11} ${ey + 28}" stroke="${INK}" stroke-width="5.5" stroke-linecap="round" fill="none"/>`); break
    case 'comfort': out.push(arc(L, ey + 2), arc(R, ey + 2), brow(L, ey - 26, 3), brow(R, ey - 26, 3), smile); break
    case 'sad': out.push(eye(L, ey + 2, [0, 0], .95, true), eye(R, ey + 2, [0, 0], .95, true), brow(L, ey - 28, 5), brow(R, ey - 28, 5),
      `<path d="M${ex - 11} ${ey + 33} Q${ex - 5.5} ${ey + 28} ${ex} ${ey + 32} Q${ex + 5.5} ${ey + 36} ${ex + 11} ${ey + 31}" stroke="${INK}" stroke-width="5" stroke-linecap="round" fill="none"/>`,
      `<path d="M${R + 14} ${ey + 14} C${R + 9} ${ey + 24} ${R + 9} ${ey + 31} ${R + 15} ${ey + 32} C${R + 21} ${ey + 31} ${R + 20} ${ey + 24} ${R + 14} ${ey + 14}Z" fill="#9CC8F2"/><circle cx="${R + 13}" cy="${ey + 27}" r="2" fill="#fff" opacity=".9"/>`); break
    case 'surprised': out.push(eye(L - 3, ey - 4, [0, 0], 1.12), eye(R + 3, ey - 4, [0, 0], 1.12), `<ellipse cx="${ex}" cy="${ey + 34}" rx="8" ry="10" fill="${INK}"/>`); break
    case 'sleep': out.push(arc(L, ey + 4, false), arc(R, ey + 4, false), `<ellipse cx="${ex}" cy="${ey + 30}" rx="5.0" ry="4.0" fill="${INK}" opacity=".85"/>`); break
    case 'tired': out.push(
      `<path d="M${L - 13} ${ey + 1} Q${L} ${ey + 9} ${L + 13} ${ey + 4}" stroke="${INK}" stroke-width="6" stroke-linecap="round" fill="none"/>`,
      `<path d="M${R - 13} ${ey + 4} Q${R} ${ey + 9} ${R + 13} ${ey + 1}" stroke="${INK}" stroke-width="6" stroke-linecap="round" fill="none"/>`,
      `<ellipse cx="${ex}" cy="${ey + 33}" rx="7" ry="8.5" fill="${INK}"/><ellipse cx="${ex}" cy="${ey + 37}" rx="4" ry="3" fill="#F07C86"/><circle cx="${R + 18}" cy="${ey + 8}" r="3.5" fill="#9CC8F2"/>`); break
    case 'cheer': out.push(arc(L, ey), `<path d="M${R - 12} ${ey - 8} L${R + 10} ${ey} L${R - 12} ${ey + 8}" stroke="${INK}" stroke-width="6.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>`, openm); break
    case 'letter': out.push(eye(L, ey + 3, [0, 5]), eye(R, ey + 3, [0, 5]), smile); break
    case 'flutter': out.push(eye(L, ey, [0, 0], 1.05, true), eye(R, ey, [0, 0], 1.05, true), smile); break
  }
  return `<g class="face">${out.join('')}</g>`
}

type HandSpec = [number, number, number, number, [number, number] | null]
function hand([x, y, rot, flip, arm]: HandSpec, u: string) {
  let a = ''
  if (arm) {
    const [ax, ay] = arm, dx = x - ax, dy = y - ay, len = Math.hypot(dx, dy), nx = -dy / len, ny = dx / len
    const r0 = 30, r1 = 24, bend = 6 * flip
    const mx = (ax + x) / 2 + nx * bend, my = (ay + y) / 2 + ny * bend, rm = (r0 + r1) / 2 + 1.5
    const pts = (k: number) => [`${f1(ax + nx * r0 * k)} ${f1(ay + ny * r0 * k)}`,
      `${f1(mx + nx * rm * k * 2 - nx * (r0 + r1) / 2 * k)} ${f1(my + ny * rm * k * 2 - ny * (r0 + r1) / 2 * k)}`, `${f1(x + nx * r1 * k)} ${f1(y + ny * r1 * k)}`]
    const [p0, c0, p1] = pts(1), [q0, cq, q1] = pts(-1)
    a = `<g filter="url(#ds${u})"><path d="M${p0} Q${c0} ${p1} A${r1} ${r1} 0 0 0 ${q1} Q${cq} ${q0} A${r0} ${r0} 0 0 0 ${p0} Z" fill="url(#hd${u})"/>` +
      `<path d="M${f1(ax + nx * r0 * .45)} ${f1(ay + ny * r0 * .45)} Q${f1(mx + nx * rm * .5)} ${f1(my + ny * rm * .5)} ${f1(x + nx * r1 * .4)} ${f1(y + ny * r1 * .4)}" stroke="#fff" stroke-opacity=".45" stroke-width="7" stroke-linecap="round" fill="none" filter="url(#bs${u})"/></g>`
  }
  return a + `<g transform="translate(${x} ${y}) rotate(${rot}) scale(${flip * 1.12} 1.12)" filter="url(#ds${u})">` +
    `<path d="M-11 19 C-19 8 -20 -12 -4 -19 C12 -24 21 -10 18 4 C16 14 10 21 2 23 C-4 24 -8 23 -11 19 Z" fill="url(#hd${u})"/>` +
    `<ellipse cx="3" cy="-11" rx="6" ry="4.5" fill="#fff" opacity=".6" filter="url(#bs${u})"/></g>`
}

const pencil = (x: number, y: number, rot: number, s: number) => `<g transform="translate(${x} ${y}) rotate(${rot}) scale(${s})">` +
  `<rect x="-6" y="-62" width="12" height="80" rx="3" fill="#F5C451"/><rect x="-6" y="-62" width="4" height="80" rx="2" fill="#fff" opacity=".4"/>` +
  `<rect x="-6" y="-75" width="12" height="15" rx="4" fill="#F09AA0"/><rect x="-6" y="-62" width="12" height="4" fill="#C9C2B8"/>` +
  `<path d="M-6 18 L0 36 L6 18 Z" fill="#F3D9B5"/><path d="M-2.2 29.5 L0 36 L2.2 29.5 Z" fill="${INK}"/></g>`
const envelope = (x: number, y: number, rot: number, u: string) => `<g transform="translate(${x} ${y}) rotate(${rot})" filter="url(#ds${u})">` +
  `<rect x="-50" y="-32" width="100" height="66" rx="8" fill="#FFFDF8"/><path d="M-50 -26 L0 8 L50 -26" stroke="#E7DCCD" stroke-width="3" fill="none" stroke-linejoin="round"/>` +
  `<path d="M-46 30 L-12 4 M46 30 L12 4" stroke="#EFE6DA" stroke-width="2.5"/><path d="M0 16 c-6 -5 -11 -9 -6 -14 c3 -3 6 -1 6 2 c0 -3 3 -5 6 -2 c5 5 0 9 -6 14z" fill="#F27C92"/></g>`
const heart = (x: number, y: number, r: number, col: string, op = 1) =>
  `<path transform="translate(${x} ${y}) scale(${r / 10})" d="M0 9 C-7 3 -12 -1 -10 -6 C-8 -11 -2 -11 0 -6 C2 -11 8 -11 10 -6 C12 -1 7 3 0 9Z" fill="${col}" opacity="${op}"/>`
const sparkle = (x: number, y: number, r: number, c: string, op = 1) => {
  const k = r * .22
  return `<path d="M${x} ${y - r} Q${x + k} ${y - k} ${x + r} ${y} Q${x + k} ${y + k} ${x} ${y + r} Q${x - k} ${y + k} ${x - r} ${y} Q${x - k} ${y - k} ${x} ${y - r}Z" fill="${c}" opacity="${op}"/>`
}
const zzz = (x: number, y: number, col: string) => `<g font-family="${FONT}" font-weight="800" fill="${col}" opacity=".7">` +
  `<text x="${x}" y="${y}" font-size="38">Z</text><text x="${x + 36}" y="${y - 34}" font-size="26">z</text><text x="${x + 62}" y="${y - 60}" font-size="17">z</text></g>`

const HANDS: Record<string, HandSpec[]> = {
  rest: [[140, 298, 40, 1, null], [372, 298, -40, -1, null]],
  up: [[92, 186, -24, 1, [132, 246]], [420, 186, 24, -1, [380, 246]]],
  cheer: [[140, 298, 40, 1, null], [424, 172, 20, -1, [382, 240]]],
  cheek: [[140, 298, 40, 1, null], [352, 270, -10, -1, null]],
  pat: [[140, 298, 40, 1, null], [430, 238, -50, -1, [386, 270]]],
  face: [[170, 262, 10, 1, null], [342, 262, -10, -1, null]],
  hold: [[206, 316, 60, 1, null], [306, 316, -60, -1, null]],
}
const POSE: Record<Expr, string> = { default: 'rest', calm: 'rest', happy: 'up', think: 'cheek', comfort: 'pat', sad: 'rest', surprised: 'face', sleep: 'rest', tired: 'rest', cheer: 'cheer', letter: 'hold', flutter: 'rest' }

/** Full Oni (lamp, hands, face, effects). [u] must be unique per SVG on the page (gradient ids). */
export function oniSvg(m: Mood, expr: Expr, u: string, chain = true): string {
  const off = !!m.off, dim = expr === 'sleep' && !off, glow = m.glow
  const deep = mix(glow, '#5A3A2A', .45)
  const d = [defs(u, m, dim)]
  if (!off) d.push(`<circle class="halo" cx="256" cy="232" r="250" fill="url(#ha${u})" opacity="${dim ? .45 : 1}"/>`)
  d.push(`<ellipse cx="256" cy="420" rx="140" ry="13" fill="#5A3A28" opacity=".22" filter="url(#bl${u})"/>`)
  if (!off) d.push(`<ellipse cx="256" cy="414" rx="190" ry="26" fill="${glow}" opacity="${dim ? .12 : .28}" filter="url(#bl${u})"/>`)
  d.push(`<path d="M140 388 L140 404 A116 25 0 0 0 372 404 L372 388 Z" fill="url(#ws${u})"/>` +
    `<ellipse cx="256" cy="388" rx="116" ry="25" fill="url(#wt${u})"/>` +
    `<ellipse cx="256" cy="388" rx="102" ry="19" fill="none" stroke="#fff" stroke-opacity=".16" stroke-width="2"/>` +
    `<ellipse cx="222" cy="380" rx="40" ry="6" fill="#fff" opacity=".2" filter="url(#bs${u})"/>` +
    `<ellipse cx="256" cy="384" rx="34" ry="8" fill="${WOOD_S}" opacity=".35" filter="url(#bs${u})"/>` +
    `<path d="M236 318 L236 380 Q256 388 276 380 L276 318 Z" fill="url(#wn${u})"/>` +
    `<path d="M224 322 L224 336 Q256 346 288 336 L288 322 Z" fill="url(#br${u})"/>` +
    `<ellipse cx="256" cy="322" rx="32" ry="7" fill="#FBE8C2"/>`)
  if (!off) d.push(`<ellipse cx="256" cy="380" rx="86" ry="16" fill="${m.light}" opacity="${dim ? .25 : .55}" filter="url(#bm${u})"/>`)
  if (chain) {
    let beads = ''; for (let y = 330; y < 366; y += 7) beads += `<circle cx="352" cy="${y}" r="2.6" fill="#C9A063"/>`
    d.push(`<g class="chain">${beads}<path d="M352 366 L352 372" stroke="#C9A063" stroke-width="2"/><ellipse cx="352" cy="380" rx="7" ry="9" fill="url(#br${u})"/><ellipse cx="350" cy="377" rx="2.2" ry="3" fill="#fff" opacity=".7"/></g>`)
  }
  const hands = HANDS[POSE[expr]]
  d.push(hands.filter(h => h[4]).map(h => hand(h, u)).join(''))
  if (!off) d.push(`<path class="bloom" d="${DOME}" transform="translate(0 14)" fill="${glow}" opacity="${dim ? .3 : .72}" filter="url(#bg${u})"/>`)
  d.push(`<g class="dome"><path d="${DOME}" fill="url(#dm${u})"/><g clip-path="url(#dc${u})">` +
    (off ? '' : `<rect width="512" height="512" fill="url(#ig${u})"/>`) +
    `<rect width="512" height="512" fill="url(#vs${u})"/><rect width="512" height="512" fill="url(#tp${u})"/>` +
    (off ? '' : `<ellipse class="core" cx="256" cy="284" rx="112" ry="56" fill="#FFFFFF" opacity="${dim ? .18 : .5}" filter="url(#bl${u})"/>`) +
    `<path d="${DOME}" stroke="#fff" stroke-opacity=".75" stroke-width="16" fill="none" filter="url(#bm${u})"/>` +
    `<path d="${DOME}" stroke="#fff" stroke-opacity=".7" stroke-width="4" fill="none"/>` +
    `<path d="M122 300 C170 282 342 282 390 300" stroke="#fff" stroke-opacity=".45" stroke-width="4" fill="none" filter="url(#bs${u})"/>` +
    `<path d="M110 300 C130 322 190 328 256 328 C322 328 382 322 402 300" stroke="#fff" stroke-opacity=".7" stroke-width="7" fill="none" filter="url(#bs${u})"/>` +
    `<path d="M128 236 C124 170 168 118 236 110 C188 132 156 176 150 238 C146 252 130 252 128 236 Z" fill="#fff" opacity=".85" filter="url(#bs${u})"/>` +
    `<path d="M384 168 C396 190 402 214 402 240" stroke="#fff" stroke-opacity=".55" stroke-width="7" stroke-linecap="round" fill="none" filter="url(#bs${u})"/>` +
    `</g><path d="${DOME}" stroke="url(#eg${u})" stroke-width="2.2" fill="none"/>` +
    `<circle cx="222" cy="122" r="7" fill="#fff" opacity=".95"/><circle cx="204" cy="134" r="3.5" fill="#fff" opacity=".8"/>` +
    `<path d="M104 292 C106 316 168 330 256 330 C344 330 406 316 408 292" stroke="${deep}" stroke-opacity=".18" stroke-width="2.5" fill="none"/></g>`)
  d.push(face(u, expr, m.blush, dim || off))
  if (expr === 'think') d.push(pencil(372, 236, 24, .9))
  if (expr === 'letter') d.push(envelope(256, 318, -4, u))
  if (expr === 'comfort') d.push(heart(462, 160, 15, '#F48A9C') + `<path d="M470 214 q10 2 10 14 M480 196 q16 4 16 24" stroke="${deep}" stroke-opacity=".45" stroke-width="4" stroke-linecap="round" fill="none"/>`)
  d.push(hands.filter(h => !h[4]).map(h => hand(h, u)).join(''))
  if (expr === 'sleep') d.push(zzz(372, 132, mix(glow, '#8C82AA', .6)))
  if (expr === 'happy' || expr === 'cheer') d.push(sparkle(58, 272, 14, glow) + sparkle(448, 96, 19, mix(glow, '#FFFFFF', .3)) + sparkle(464, 220, 9, glow) + sparkle(64, 120, 8, mix(glow, '#FFFFFF', .3)))
  if (expr === 'surprised') d.push(`<g stroke="${deep}" stroke-width="7" stroke-linecap="round" opacity=".7"><path d="M396 96 L410 70"/><path d="M420 116 L446 100"/><path d="M370 84 L372 56"/></g>`)
  if (expr === 'flutter') d.push(heart(420, 120, 18, glow) + heart(450, 70, 11, mix(glow, '#FFFFFF', .25)) + heart(84, 140, 12, glow, .8))
  if (expr === 'think') d.push(`<g fill="${deep}" opacity=".55"><circle cx="410" cy="120" r="7"/><circle cx="434" cy="96" r="9"/><circle cx="462" cy="68" r="11"/></g>`)
  if (expr === 'tired') d.push(`<g fill="${mix(glow, '#FFFFFF', .35)}" opacity=".9"><circle cx="420" cy="150" r="14"/><circle cx="440" cy="140" r="17"/><circle cx="458" cy="154" r="12"/><circle cx="436" cy="158" r="12"/></g>`)
  if (expr === 'sad') d.push(`<path d="M410 120 q14 -16 28 0 q14 -16 28 0" stroke="${deep}" stroke-opacity=".35" stroke-width="5" stroke-linecap="round" fill="none"/>`)
  return d.join('')
}
