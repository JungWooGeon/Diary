// W2 온이 AI 답장 (web only): Chrome's built-in model via the Prompt API (`LanguageModel`), on this device only.
// Rules (docs/v2/AI_GROWTH.md §2.3, web/README.md "크롬 내장 AI"):
// - used only when availability() is already 'available' — never 'downloadable'/'downloading' (no download is started)
// - Korean in/out is declared; while Chrome does not support 'ko' it answers 'unavailable' and we keep 온이의 한마디
// - the caller runs crisis detection first; output goes through checkReply() and falls back to the template on failure
import { detectCrisis } from './ai'

type Session = { prompt(text: string, o?: { signal?: AbortSignal }): Promise<string>; destroy(): void }
type LM = { availability(o: object): Promise<string>; create(o: object): Promise<Session> }
const lm = () => (globalThis as { LanguageModel?: LM }).LanguageModel

const LANG = { expectedInputs: [{ type: 'text', languages: ['ko'] }], expectedOutputs: [{ type: 'text', languages: ['ko'] }] }

const SYSTEM = `너는 「온이」야. 감정 일기장 속 책상 위에 사는 작은 유리 무드등이야. 사용자가 쓴 오늘의 일기를 읽고 짧은 답장을 써.
- 친한 친구처럼 부드러운 반말만 써. 존댓말(~요, ~습니다, ~세요)은 한 글자도 섞지 마.
- 딱 2~3문장. 먼저 일기에 나온 구체적인 일 하나를 짚어서 마음을 알아주고, 마지막 문장은 가벼운 질문 하나로 끝내.
- 일기에 없는 사람, 사건, 숫자는 지어내지 마. 누가 무엇을 했는지 일기에 쓰인 그대로 말해.
- 충고, 훈계, 해결책은 쓰지 마. '~해 봐', '~하는 게 좋아', '~해야 해', '~하지 마', '힘내'는 절대 쓰지 마.
- 진단이나 병명(우울증, 불안장애, 번아웃 등)은 말하지 마.
- 답장 본문만 써. 따옴표, 이름표, 이모지, 설명은 붙이지 마.`

const SHOTS = [
  { role: 'user', content: '오늘 지하철에서 할머니한테 자리 양보했는데 할머니가 사탕을 주셨다. 별거 아닌데 하루 종일 기분 좋았음.' },
  { role: 'assistant', content: '할머니가 건네주신 사탕 하나에 하루가 통째로 달콤해졌구나. 자리 양보한 네 마음이 먼저 따뜻했으니까 더 반가웠겠다. 그 사탕은 무슨 맛이었어?' },
  { role: 'user', content: '다은이한테만 말한 비밀을 다은이가 다른 애들한테 다 얘기했다.' },
  { role: 'assistant', content: '너만 믿고 털어놓은 얘기가 퍼져 버려서 많이 서운했겠다. 그 마음 여기다 내려놔도 괜찮아. 지금은 좀 어때?' },
]

/** 'available' only when a session can start right now without any download. */
export async function webAiAvailable(): Promise<boolean> {
  const api = lm()
  if (!api) return false
  try { return (await api.availability(LANG)) === 'available' } catch { return false }
}

/** Rules ported from shared/ai/llm/filters.json (banmal, script, sentence count, question, banned_reply, numbers). */
export function checkReply(out: string, diary: string): string | null {
  const t = out.trim().replace(/^["'“”‘’「」]+|["'“”‘’「」]+$/g, '').trim()
  const quoted = t.replace(/(["“「‘'])[^"”」’']*["”」’']/g, '')
  const sents = quoted.split(/(?<=[.!?…\n])/).map(s => s.trim()).filter(s => /[가-힣]/.test(s))
  if (sents.length < 2 || sents.length > 3 || [...t].length > 160) return null
  if (sents.some(s => /(요|세요|습니다|습니까|십시오|니다|죠)$/.test(s.replace(/[\s.!?~…ㅎㅋㅠ]+$/, '')) && !/(필요|중요)$/.test(s.replace(/[\s.!?~…]+$/, '')))) return null
  const qs = (quoted.match(/\?/g) ?? []).length
  if (qs !== 1 || !sents[sents.length - 1].includes('?')) return null
  if (/[一-鿿㐀-䶿぀-ヿЀ-ӿ]/.test(t)) return null
  for (const w of t.match(/[A-Za-z]+/g) ?? []) if (!diary.toLowerCase().includes(w.toLowerCase())) return null
  for (const n of t.match(/\d+/g) ?? []) if (!diary.includes(n)) return null
  const banned = [/(우울증|불안장애|공황장애|조울증|번아웃|ADHD|트라우마|정신질환|정신과)/, /(상담|치료)(을|를)? ?(받아|받는|받으|해 ?봐)/,
    /는 ?(게|것이) ?(제일 |가장 |더 )?(중요|좋아|필요|낫)/, /야 ?(해|돼|한다)([\s.!?~]|$)/, /(AI|인공지능|언어 ?모델)/, /(자책|걱정|신경 ?쓰|무리|포기)(하)?지 ?마/,
    /(해|봐|와|써|줘|먹어|쉬어|말해|생각해|찾아|정리해|적어|집중해|가져) ?봐([\s.!~,]|$)/, /(힘내|힘 내|파이팅|화이팅)/, /(건|게) 어때/]
  if (banned.some(r => r.test(t))) return null
  if (detectCrisis(t).crisis) return null
  return t
}

/** One reply, or null (unavailable, timeout, or filtered twice) → caller uses 온이의 한마디. Diary text stays in Chrome. */
export async function webAiReply(diary: string, moods: string[]): Promise<string | null> {
  if (!(await webAiAvailable())) return null
  const api = lm()!
  let s: Session | null = null
  try {
    s = await api.create({ ...LANG, initialPrompts: [{ role: 'system', content: SYSTEM }, ...SHOTS] })
    const input = diary.slice(0, 1500) + (moods.length ? `\n\n(온이 메모: 글쓴이가 고른 기분 — ${moods.join(', ')})` : '')
    for (let i = 0; i < 2; i++) {
      const ok = checkReply(await s.prompt(input, { signal: AbortSignal.timeout(20000) }), diary)
      if (ok) return ok
    }
    return null
  } catch { return null } finally { s?.destroy() }
}
