// Share cards (W3 · AI_GROWTH §3): 오늘의 빛 / 월간 빛 캘린더 as a 1080×1920 (story) PNG, drawn from SVG on a canvas.
// Shows only colours, the date and emotion names — never diary text.
import { mix, mixDay, dayGradient, mixHex, GROUPS, type Emo } from './light'
import { oniSvg, exprFor, moodOf } from './oni'
import type { EntryDoc } from './sync'

const W = 1080, H = 1920
const FONT = "'Apple SD Gothic Neo','Pretendard','Noto Sans KR','Malgun Gothic',sans-serif"
export const WATERMARK = '온이 일기 · jungwoogeon.github.io/Diary'
const esc = (s: string) => s.replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!))

function frame(glow: string, body: string, watermark: boolean) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="${FONT}">
<defs><radialGradient id="bgx" cx=".5" cy=".36" r=".75"><stop offset="0" stop-color="${mixHex(glow, '#2A2340', .55)}"/><stop offset="1" stop-color="#17142A"/></radialGradient></defs>
<rect width="${W}" height="${H}" fill="url(#bgx)"/>${body}
${watermark ? `<text x="${W / 2}" y="${H - 80}" text-anchor="middle" font-size="30" fill="#FFFFFF" opacity=".55">${WATERMARK}</text>` : ''}</svg>`
}
const oni = (emos: Emo[], x: number, y: number, size: number) =>
  `<svg x="${x}" y="${y}" width="${size}" height="${size}" viewBox="0 0 512 512" overflow="visible">${oniSvg(moodOf(mix(emos), emos), exprFor(emos), 'card')}</svg>`

export function todayCard(dateLabel: string, todays: EntryDoc[], names: Record<string, string>, watermark: boolean) {
  const emos = todays.flatMap(e => e.emotions), light = mixDay(todays.map(e => e.emotions))
  const stops = dayGradient(todays.map(e => ({ time: e.time, emotions: e.emotions })))
  const words = [...new Set(emos.filter(e => e.key !== 'unsure').map(e => names[e.key]))].slice(0, 4).join(' · ') || '조용한 하루'
  const band = stops.map(s => `<stop offset="${s.position}" stop-color="${s.light.glow}"/>`).join('')
  return frame(light.glow, `
<text x="${W / 2}" y="250" text-anchor="middle" font-size="44" fill="#FFFFFF" opacity=".7">${esc(dateLabel)}</text>
<text x="${W / 2}" y="340" text-anchor="middle" font-size="76" font-weight="700" fill="#FFFFFF">오늘의 빛</text>
${oni(emos, 190, 430, 700)}
<text x="${W / 2}" y="1290" text-anchor="middle" font-size="54" font-weight="600" fill="#FFFFFF">${esc(words)}</text>
<defs><linearGradient id="band" x1="0" x2="1">${stops.length > 1 ? band : `<stop offset="0" stop-color="${light.glow}"/>`}</linearGradient></defs>
<rect x="140" y="1400" width="800" height="36" rx="18" fill="url(#band)"/>
<g font-size="28" fill="#FFFFFF" opacity=".55" text-anchor="middle"><text x="140" y="1490">0시</text><text x="540" y="1490">12시</text><text x="940" y="1490">24시</text></g>`, watermark)
}

export function monthCard(year: number, month: number, es: EntryDoc[], watermark: boolean) {
  const by = new Map<string, Emo[]>(); for (const e of es) by.set(e.date, [...(by.get(e.date) ?? []), ...e.emotions])
  const first = new Date(year, month - 1, 1).getDay(), days = new Date(year, month, 0).getDate()
  const cell = 120, gap = 12, x0 = (W - 7 * cell - 6 * gap) / 2, y0 = 560
  let cells = ''
  for (let d = 1; d <= days; d++) {
    const i = first + d - 1, x = x0 + (i % 7) * (cell + gap), y = y0 + Math.floor(i / 7) * (cell + gap)
    const em = by.get(`${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`)
    const l = em && mix(em)
    cells += `<rect x="${x}" y="${y}" width="${cell}" height="${cell}" rx="28" fill="${l ? (l.off ? '#4A4560' : l.glow) : '#FFFFFF'}" opacity="${l ? 1 : .08}"/>` +
      `<text x="${x + 18}" y="${y + 40}" font-size="26" fill="${l && !l.off ? '#2E2622' : '#FFFFFF'}" opacity=".7">${d}</text>`
  }
  const all = es.flatMap(e => e.emotions)
  const legend = GROUPS.map((g, k) => `<circle cx="${170 + k * 180}" cy="1500" r="16" fill="${g.glow}"/><text x="${196 + k * 180}" y="1511" font-size="30" fill="#FFFFFF" opacity=".75">${g.name}</text>`).join('')
  return frame(mix(all).glow, `
<text x="${W / 2}" y="250" text-anchor="middle" font-size="44" fill="#FFFFFF" opacity=".7">${year}년</text>
<text x="${W / 2}" y="350" text-anchor="middle" font-size="84" font-weight="700" fill="#FFFFFF">${month}월의 빛</text>
<text x="${W / 2}" y="440" text-anchor="middle" font-size="36" fill="#FFFFFF" opacity=".7">${new Set(es.map(e => e.date)).size}일 동안 빛을 켰어요</text>
${cells}${legend}
${oni(all, 780, 1560, 220)}`, watermark)
}

/** SVG → PNG; shares the file where the browser can (mobile), else downloads it. */
export async function shareImage(svg: string, filename: string) {
  const url = URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml' }))
  try {
    const img = new Image(); img.src = url; await img.decode()
    const c = document.createElement('canvas'); c.width = W; c.height = H
    c.getContext('2d')!.drawImage(img, 0, 0)
    const blob = await new Promise<Blob>((ok, fail) => c.toBlob(b => (b ? ok(b) : fail(new Error('png'))), 'image/png'))
    const file = new File([blob], filename, { type: 'image/png' })
    if (navigator.canShare?.({ files: [file] })) { try { await navigator.share({ files: [file] }); return } catch { /* cancelled → download */ } }
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = filename; a.click()
    setTimeout(() => URL.revokeObjectURL(a.href), 1000)
  } finally { URL.revokeObjectURL(url) }
}
