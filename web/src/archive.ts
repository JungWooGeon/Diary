// 내보내기 (JSON + HTML zip), same entries.json as Android's DiaryArchive.kt. Photos stay on Drive (the web never stores them).
import { mix } from './light'
import type { EntryDoc } from './sync'

const CRC = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0 })
export const crc32 = (b: Uint8Array) => { let c = ~0; for (const x of b) c = CRC[(c ^ x) & 0xff] ^ (c >>> 8); return ~c >>> 0 }

/** Minimal zip writer (stored, no compression): a few text files don't need a library. */
export function zip(files: { name: string; data: Uint8Array }[]): Uint8Array<ArrayBuffer> {
  const enc = new TextEncoder(), parts: Uint8Array[] = [], central: Uint8Array[] = []
  let offset = 0
  for (const f of files) {
    const name = enc.encode(f.name), crc = crc32(f.data)
    const head = (sig: number, extra: number) => {
      const h = new DataView(new ArrayBuffer(extra + name.length))
      h.setUint32(0, sig, true)
      return h
    }
    const local = head(0x04034b50, 30)
    // version 20, flag bit 11 = UTF-8 names, method 0 (stored), time/date 0
    local.setUint16(4, 20, true); local.setUint16(6, 0x0800, true); local.setUint32(14, crc, true)
    local.setUint32(18, f.data.length, true); local.setUint32(22, f.data.length, true); local.setUint16(26, name.length, true)
    new Uint8Array(local.buffer).set(name, 30)
    const cen = head(0x02014b50, 46)
    cen.setUint16(4, 20, true); cen.setUint16(6, 20, true); cen.setUint16(8, 0x0800, true); cen.setUint32(16, crc, true)
    cen.setUint32(20, f.data.length, true); cen.setUint32(24, f.data.length, true); cen.setUint16(28, name.length, true); cen.setUint32(42, offset, true)
    new Uint8Array(cen.buffer).set(name, 46)
    parts.push(new Uint8Array(local.buffer), f.data); central.push(new Uint8Array(cen.buffer))
    offset += local.byteLength + f.data.length
  }
  const size = central.reduce((n, c) => n + c.length, 0)
  const end = new DataView(new ArrayBuffer(22))
  end.setUint32(0, 0x06054b50, true); end.setUint16(8, files.length, true); end.setUint16(10, files.length, true)
  end.setUint32(12, size, true); end.setUint32(16, offset, true)
  const all = [...parts, ...central, new Uint8Array(end.buffer)]
  const out = new Uint8Array(all.reduce((n, p) => n + p.length, 0))
  let at = 0; for (const p of all) { out.set(p, at); at += p.length }
  return out
}

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
const WEEK = ['일', '월', '화', '수', '목', '금', '토']

export function diaryHtml(docs: EntryDoc[], names: Record<string, string>): string {
  const days = new Map<string, EntryDoc[]>()
  for (const d of [...docs].sort((a, b) => (a.date + a.time).localeCompare(b.date + b.time))) days.set(d.date, [...(days.get(d.date) ?? []), d])
  let h = `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>온이 일기</title><style>${CSS}</style></head><body><h1>온이 일기</h1><p class="meta">${docs.filter(d => !d.deleted).length}개의 기록</p>`
  for (const [date, list] of days) {
    const dt = new Date(date + 'T00:00')
    h += `<section class="day"><h2>${dt.getFullYear()}년 ${dt.getMonth() + 1}월 ${dt.getDate()}일 ${WEEK[dt.getDay()]}요일</h2>`
    for (const d of list) {
      const l = mix(d.emotions)
      h += `<article style="border-color:${l.glow};background:${l.light}22"><div class="time"><span class="dot" style="background:${l.glow}"></span>${esc(d.time)}${d.trashedAt ? ' · 휴지통' : ''}</div>`
      if (d.title.trim()) h += `<h3>${esc(d.title)}</h3>`
      if (d.emotions.length) h += `<div class="chips">${d.emotions.map(e => `<span class="chip" style="background:${mix([e]).light}">${esc(names[e.key] ?? e.key)} ${'●'.repeat(e.intensity)}</span>`).join('')}</div>`
      if (d.body.trim()) h += `<p class="body">${esc(d.body)}</p>`
      if (d.photos.length) h += `<p class="tags">사진 ${d.photos.length}장 (앱 내보내기에 들어 있어요)</p>`
      if (d.tags.length) h += `<p class="tags">${d.tags.map(t => '#' + esc(t.value)).join(' ')}</p>`
      if (d.reply) h += `<blockquote>온이: ${esc(d.reply.text)}</blockquote>`
      h += '</article>'
    }
    h += '</section>'
  }
  return h + '</body></html>'
}

const README = `온이 일기 내보내기 (웹)
==================

diary.html   브라우저로 열면 날짜별로 읽을 수 있어요. 인쇄(PDF로 저장)도 돼요.
entries.json 일기 원본 데이터예요 (구글 드라이브 백업과 같은 형식).

사진 원본은 Android 앱의 마이 › 내보내기에서 함께 저장할 수 있어요.
Android 앱의 마이 › 내보내기 › 파일에서 가져오기 로 이 파일을 고르면 일기를 되살릴 수 있어요.

이 파일에는 일기 내용이 그대로 들어 있어요. 다른 사람과 공유하지 않도록 조심해 주세요.
`

export function exportZip(docs: EntryDoc[], names: Record<string, string>, now = Date.now()): Uint8Array<ArrayBuffer> {
  const enc = new TextEncoder()
  return zip([
    { name: 'entries.json', data: enc.encode(JSON.stringify({ format: 'oni-diary-export', version: 1, exportedAt: now, entries: docs })) },
    { name: 'diary.html', data: enc.encode(diaryHtml(docs, names)) },
    { name: 'README.txt', data: enc.encode(README) },
  ])
}

const CSS = "body{font-family:-apple-system,'Apple SD Gothic Neo','Noto Sans KR',sans-serif;max-width:720px;margin:0 auto;padding:24px 16px;color:#2b2630;background:#fbf8f4;line-height:1.7}" +
  'h1{font-size:28px;margin:0}.meta{color:#8a8290;margin:4px 0 24px}h2{font-size:17px;margin:32px 0 10px;color:#5b5363}' +
  'article{border-left:4px solid;border-radius:12px;padding:12px 16px;margin:0 0 12px;break-inside:avoid}' +
  '.time{font-size:13px;color:#8a8290;display:flex;align-items:center;gap:6px}.dot{width:10px;height:10px;border-radius:50%;display:inline-block}' +
  'h3{margin:6px 0 2px;font-size:17px}.chips{display:flex;flex-wrap:wrap;gap:6px;margin:6px 0}.chip{font-size:12px;padding:2px 10px;border-radius:999px}' +
  '.body{white-space:pre-wrap;margin:6px 0}.tags{color:#8a8290;font-size:13px}blockquote{margin:8px 0 0;padding:8px 12px;border-radius:10px;background:#00000008;font-size:14px}' +
  '@media print{body{background:#fff}article{-webkit-print-color-adjust:exact;print-color-adjust:exact}}'
