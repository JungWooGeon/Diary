// Demo data (no Google account needed): lives in memory only, never synced.
import { Store, newEntry, type EntryDoc } from './sync'
import { reply } from './ai'

const LINES: [string, string, [string, number][], { kind: string; value: string }[]][] = [
  ['한강 따라 걸은 날', '점심 먹고 회사 앞 한강 공원을 30분쯤 걸었다. 바람이 차가웠는데 머리가 맑아졌다.', [['comfy', 3], ['proud', 2]], [{ kind: 'place', value: '한강' }, { kind: 'activity', value: '산책' }]],
  ['민지랑 붕어빵', '첫눈 온다고 해서 괜히 창밖만 보다가, 결국 민지랑 붕어빵 사 먹었다.', [['flutter', 4], ['joy', 3]], [{ kind: 'person', value: '민지' }]],
  ['발표 끝', '드디어 발표 끝났다. 떨렸지만 준비한 만큼은 했다.', [['proud', 5], ['relieved', 3]], [{ kind: 'activity', value: '발표' }]],
  ['', '그냥 좀 지친 하루. 일찍 자야겠다.', [['tired', 4]], []],
  ['비 오는 날', '하루 종일 비. 창문에 맺힌 빗방울 보면서 음악 들었다.', [['serene', 3], ['miss', 2]], [{ kind: 'activity', value: '음악' }]],
  ['엄마 생일', '엄마 생일이라 케이크 사 들고 집에 갔다. 엄마가 많이 웃었다.', [['grateful', 5], ['love', 3]], [{ kind: 'person', value: '엄마' }]],
  ['', '괜히 서운한 말을 들었다. 별거 아닌데 자꾸 생각난다.', [['hurt', 3], ['down', 2]], []],
  ['카페에서', '좋아하는 카페 창가 자리에서 책 반 권 읽었다.', [['comfy', 4]], [{ kind: 'place', value: '카페' }, { kind: 'activity', value: '독서' }]],
  ['', '시험 기간이라 불안하다. 그래도 오늘 할 건 했다.', [['anxious', 3], ['proud', 2]], [{ kind: 'activity', value: '공부' }]],
  ['운동', '오랜만에 헬스장. 몸은 힘든데 기분은 가볍다.', [['light', 4], ['tired', 2]], [{ kind: 'activity', value: '운동' }]],
]

const iso = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)

export function demoStore(): Store {
  const s = new Store()
  let seed = 7
  const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647
  const today = new Date()
  for (let back = 75; back >= 0; back--) {
    if (rnd() < 0.25 && back > 0) continue
    const day = new Date(today); day.setDate(today.getDate() - back)
    const n = rnd() < 0.3 ? 2 : 1
    for (let k = 0; k < n; k++) {
      const [title, body, emos, tags] = LINES[Math.floor(rnd() * LINES.length)]
      const h = n === 2 ? (k ? 21 : 8 + Math.floor(rnd() * 5)) : 19 + Math.floor(rnd() * 4)
      const time = `${String(h).padStart(2, '0')}:${String(Math.floor(rnd() * 60)).padStart(2, '0')}`
      const at = new Date(`${iso(day)}T${time}:00`).getTime()
      const e: EntryDoc = { ...newEntry(iso(day), time, at), title, body, emotions: emos.map(([key, intensity]) => ({ key, intensity })), tags }
      const r = reply(body, e.emotions, tags, new Set(), rnd)
      if (r) e.reply = { text: r.text, source: 'template', templateId: r.id, createdAt: at }
      s.put({ doc: e })
    }
  }
  s.set('entitlement', { premium: false, updatedAt: 0 })
  return s
}
