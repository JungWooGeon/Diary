import { test, expect, afterEach } from 'vitest'
import { causes, keywords } from '../src/insights'
import { checkReply, webAiReply } from '../src/webAi'
import { newEntry, type EntryDoc } from '../src/sync'

const e = (date: string, key: string, tags: string[] = [], body = ''): EntryDoc =>
  ({ ...newEntry(date, '21:00'), emotions: [{ key, intensity: 3 }], tags: tags.map(value => ({ kind: 'person', value })), body })

test('E8 causes: 친구 만난 날 설렘', () => {
  const es = [
    e('2026-11-02', 'flutter', ['민준']), e('2026-11-03', 'flutter', ['민준']), e('2026-11-04', 'flutter', ['민준']),
    e('2026-11-05', 'tired'), e('2026-11-06', 'tired'), e('2026-11-07', 'sad'), e('2026-11-08', 'tired'), e('2026-11-09', 'joy'),
  ]
  const c = causes(es)
  expect(c[0]).toMatchObject({ label: '민준이랑 함께한 날', group: 'flutter', n: 3 })
  expect(c[0].diff).toBeCloseTo(1 - 3 / 8)
  expect(causes(es.slice(0, 4))).toEqual([]) // too few entries
})

test('E13 keywords: tags and repeated body words, josa stripped', () => {
  const k = keywords([e('2026-11-02', 'joy', ['엄마'], '오늘 떡볶이를 먹었다'), e('2026-11-03', 'joy', [], '떡볶이는 역시 최고. 오늘도')])
  expect(k.map(x => x.word)).toEqual(['떡볶이', '엄마'])
})

test('W2 reply filter: 반말, 2~3문장, 질문 1개로 끝, 훈계·진단·지어낸 숫자 금지', () => {
  const diary = '오늘 민준이랑 떡볶이 먹었다. 3시간이나 수다 떨었음'
  expect(checkReply('민준이랑 3시간이나 수다 떨었구나. 떡볶이도 맛있었겠다. 무슨 얘기가 제일 재밌었어?', diary)).not.toBeNull()
  expect(checkReply('민준 님과 즐거운 시간을 보내셨네요. 어떤 얘기를 하셨어요?', diary)).toBeNull() // 존댓말
  expect(checkReply('떡볶이 먹었구나. 내일은 일찍 자야 해. 오늘 어땠어?', diary)).toBeNull() // 훈계
  expect(checkReply('5시간이나 놀았구나. 재밌었겠다. 뭐 했어?', diary)).toBeNull() // 없는 숫자
  expect(checkReply('재밌었겠다. 또 만나?', diary)).not.toBeNull()
  expect(checkReply('재밌었겠다. 정말 좋았겠다.', diary)).toBeNull() // 질문 없음
})

afterEach(() => { delete (globalThis as Record<string, unknown>).LanguageModel })

test('W2 never creates a session (= never downloads) unless the model is already available', async () => {
  let created = 0
  for (const avail of ['unavailable', 'downloadable', 'downloading']) {
    (globalThis as Record<string, unknown>).LanguageModel = { availability: async () => avail, create: async () => { created++; throw new Error() } }
    expect(await webAiReply('오늘 좋았다', [])).toBeNull()
  }
  expect(created).toBe(0)
  ;(globalThis as Record<string, unknown>).LanguageModel = {
    availability: async (o: { expectedInputs: { languages: string[] }[] }) => (o.expectedInputs[0].languages[0] === 'ko' ? 'available' : 'unavailable'),
    create: async () => ({ prompt: async () => '좋은 하루였구나. 뭐가 제일 좋았어?', destroy() {} }),
  }
  expect(await webAiReply('오늘 좋았다', [])).toBe('좋은 하루였구나. 뭐가 제일 좋았어?')
})
