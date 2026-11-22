import { Fragment, useCallback, useEffect, useId, useMemo, useRef, useState } from 'react'
import { GROUPS, GROUP_OF, DEFAULT_NAMES, QUICK_MOODS, UNSURE, mix, mixDay, dayGradient, dominantGroup, groupOf, mixHex, type Emo, type Light, type GroupKey } from './light'
import { oniSvg, BRAND, exprFor, moodOf, type Expr } from './oni'
import { detectCrisis, reply as libraryReply, question, timeSlot, type Tag } from './ai'
import { Store, SyncEngine, newEntry, VerifyError, TRASH_KEEP_MS, type EntryDoc } from './sync'
import { driveApi, signIn, signOut, connected, accountEmail, AuthError, CLIENT_ID, API, photoUrl, restore, refreshPremium, cachedPremium, unlinkAccount } from './drive'
import { AdSlot, adsOn } from './ads'
import { loadStore, clearDb } from './db'
import { demoStore } from './demo'
import { exportZip } from './archive'
import { sentences, suggestTitle, nearest, central, type Analysis } from './emotion'
import { useAnalysis, useAiState, setEmotionAi, FIRST_LOAD_MB } from './emotionAi'
import { webAiAvailable, webAiReply } from './webAi'
import { causes, keywords } from './insights'
import { shareImage, todayCard, monthCard } from './share'

// ---------- helpers ----------
const BASE = import.meta.env.BASE_URL
export const PLAY_URL = 'https://play.google.com/store/apps/details?id=com.pass.diary'
const iso = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
const hhmm = (d: Date) => d.toTimeString().slice(0, 5)
const WEEK = ['일', '월', '화', '수', '목', '금', '토']
const fmtDay = (date: string) => { const d = new Date(date + 'T00:00'); return `${d.getMonth() + 1}월 ${d.getDate()}일 ${WEEK[d.getDay()]}요일` }
const ago = (t?: number) => { if (!t) return '아직 없음'; const m = Math.round((Date.now() - t) / 60000); return m < 1 ? '방금' : m < 60 ? `${m}분 전` : m < 1440 ? `${Math.round(m / 60)}시간 전` : new Date(t).toLocaleDateString('ko-KR') }
const byTime = (a: EntryDoc, b: EntryDoc) => (a.date + a.time).localeCompare(b.date + b.time)
const TAG_LABEL: Record<string, string> = { person: '사람', place: '장소', activity: '활동', free: '태그' }
type Status = 'local' | 'syncing' | 'ok' | 'error' | 'verify' | 'needAuth' | 'offline' | 'demo'

function Oni({ light, emotions, expr, className = '' }: { light: Light | null; emotions: Emo[]; expr?: Expr; className?: string }) {
  const u = useId().replace(/[^a-zA-Z0-9]/g, '')
  const e = expr ?? exprFor(emotions)
  const svg = useMemo(() => oniSvg(moodOf(light, emotions), e, u), [light?.light, light?.glow, light?.off, e, u])
  return <svg className={'oni ' + className} viewBox="0 0 512 512" role="img" aria-label="온이" dangerouslySetInnerHTML={{ __html: svg }} />
}

function DayBand({ entries }: { entries: EntryDoc[] }) {
  const stops = dayGradient(entries.map(e => ({ time: e.time, emotions: e.emotions })))
  if (!stops.length) return <div className="band empty"><span>아직 켜진 빛이 없어요</span></div>
  const first = stops[0].position, last = stops[stops.length - 1].position
  const grad = stops.length === 1 ? stops[0].light.glow : `linear-gradient(90deg, transparent ${Math.max(0, first * 100 - 8)}%, ${stops.map(s => `${s.light.glow} ${s.position * 100}%`).join(', ')}, transparent ${Math.min(100, last * 100 + 8)}%)`
  return (
    <div className="band">
      <div className="band-track" style={{ background: stops.length === 1 ? undefined : grad }} />
      {stops.map((s, i) => <span key={i} className="band-dot" style={{ left: `${s.position * 100}%`, background: s.light.glow }} />)}
      <div className="band-axis"><span>0시</span><span>6시</span><span>12시</span><span>18시</span><span>24시</span></div>
    </div>
  )
}

function cellBg(entries: EntryDoc[], dark: boolean) {
  const lights = entries.sort(byTime).map(e => mix(e.emotions))
  if (!lights.length) return undefined
  const c = (l: Light) => dark ? mixHex(l.glow, '#1C1830', l.off ? .45 : .12) : l.light
  return lights.length === 1 ? c(lights[0]) : `linear-gradient(160deg, ${lights.map(c).join(', ')})`
}

// ---------- app ----------
export default function App() {
  const [real, setReal] = useState<Store | null>(null)
  const [demo, setDemo] = useState<Store | null>(() => (new URLSearchParams(location.search).has('demo') ? demoStore() : null))
  const store = demo ?? real
  const [, setV] = useState(0)
  const bump = useCallback(() => setV(v => v + 1), [])
  const [tab, setTab] = useState<'today' | 'calendar' | 'report' | 'settings'>(() => (new URLSearchParams(location.search).get('tab') as never) ?? 'today')
  const [write, setWrite] = useState<{ doc?: EntryDoc; date?: string; title?: string } | null>(() => (new URLSearchParams(location.search).has('write') ? {} : null))
  const [replyFor, setReplyFor] = useState<ReplyFor | null>(null)
  const [webAi, setWebAi] = useState(false)
  const [intro, setIntro] = useState(false)
  const [status, setStatus] = useState<Status>('local')
  const [toast, setToast] = useState<{ text: string; undo?: () => void } | null>(null)
  const [theme, setTheme] = useState(() => localStorage.getItem('theme') ?? 'system')
  const [authErr, setAuthErr] = useState('')
  const [premium, setPremium] = useState(cachedPremium)
  const checkPremium = useCallback(() => { refreshPremium().then(setPremium) }, [])
  const engine = useMemo(() => real && new SyncEngine(driveApi, real), [real])

  useEffect(() => { loadStore().then(s => { s.purgeTrash(); setReal(s); setStatus(s.get('email') ? 'needAuth' : 'local'); setIntro(!s.get('introSeen') && !s.get('email') && !s.live().length) }) }, [])
  useEffect(() => { webAiAvailable().then(setWebAi) }, []) // never triggers a model download (availability only)
  useEffect(() => {
    localStorage.setItem('theme', theme)
    if (theme === 'system') delete document.documentElement.dataset.theme
    else document.documentElement.dataset.theme = theme
  }, [theme])
  useEffect(() => { if (!toast) return; const t = setTimeout(() => setToast(null), 4000); return () => clearTimeout(t) }, [toast])

  const doSync = useCallback(async () => {
    if (demo || !real || !engine || !real.get('email')) return
    if (!navigator.onLine) return setStatus('offline')
    if (!connected()) return setStatus('needAuth')
    setStatus('syncing')
    try { await engine.sync(); setStatus('ok') } catch (e) {
      setStatus(e instanceof AuthError ? 'needAuth' : e instanceof VerifyError ? 'verify' : 'error')
    } finally { bump() }
  }, [demo, real, engine, bump])

  useEffect(() => {
    const on = () => doSync()
    const vis = () => document.visibilityState === 'visible' && doSync()
    window.addEventListener('online', on); document.addEventListener('visibilitychange', vis); window.addEventListener('focus', checkPremium)
    const t = setInterval(doSync, 5 * 60000)
    return () => { window.removeEventListener('online', on); document.removeEventListener('visibilitychange', vis); window.removeEventListener('focus', checkPremium); clearInterval(t) }
  }, [doSync, checkPremium])
  useEffect(() => { if (engine) restore().then(() => { doSync(); checkPremium() }) }, [engine]) // silent: session → fresh token

  const connect = async () => {
    if (!real) return
    setAuthErr('')
    try {
      const prev = real.get<string>('email')
      await signIn(prev)
      const email = await accountEmail()
      if (prev && prev !== email) { real.entries.clear(); real.meta.clear(); await clearDb() } // another account: never mix diaries
      real.set('email', email)
      setDemo(null); bump(); checkPremium(); await doSync()
    } catch (e) { setAuthErr((e as Error).message); setStatus(real.get('email') ? 'needAuth' : 'local') }
  }
  const disconnect = async () => {
    if (!real) return
    const n = real.pending().length
    if (n && !confirm(`아직 드라이브에 올리지 못한 기록이 ${n}개 있어요. 로그아웃하면 이 기기에서 지워져요. 계속할까요?`)) return
    signOut(); setPremium(false); real.entries.clear(); real.meta.clear(); await clearDb(); setStatus('local'); bump()
  }
  const unlink = async () => { await unlinkAccount(); setPremium(false); setToast({ text: '계정 연동을 해제했어요' }) }

  if (!store) return <div className="boot"><Oni light={null} emotions={[]} expr="sleep" /></div>

  const names: Record<string, string> = { ...DEFAULT_NAMES }
  for (const d of store.get<{ emotionDefs?: { key: string; name: string }[] }>('settings')?.emotionDefs ?? []) if (d.name) names[d.key] = d.name
  const entries = store.live()
  const readOnly = !!store.get('readOnly')

  const persist = (doc: EntryDoc) => { const d = store.save(doc); bump(); doSync(); return d }

  const templateReply = (d: EntryDoc) => {
    const since = Date.now() - 30 * 86400000
    const recent = new Set(entries.filter(e => e.reply?.templateId && e.reply.createdAt > since).map(e => e.reply!.templateId!))
    const r = libraryReply(d.body, d.emotions, d.tags, recent)
    return r ? persist({ ...d, reply: { text: r.text, source: 'template', templateId: r.id, createdAt: Date.now(), reported: false } }) : d
  }
  const saveEntry = async (doc: EntryDoc) => {
    let d = persist(doc)
    setWrite(null)
    const crisis = detectCrisis(`${d.title}\n${d.body}`).crisis // always first: no reply (and no AI call) when it fires
    if (crisis) return setReplyFor({ id: d.id, crisis })
    if (!d.body.trim() || d.reply) return d.reply && setReplyFor({ id: d.id, crisis })
    if (webAi && !demo) {
      setReplyFor({ id: d.id, crisis, writing: true })
      const text = await webAiReply(`${d.title}\n${d.body}`.trim(), d.emotions.map(e => names[e.key]).filter(Boolean))
      d = store.entries.get(d.id)?.doc ?? d
      if (text) d = persist({ ...d, reply: { text, source: 'llm', engine: 'chrome-builtin', templateId: null, createdAt: Date.now(), reported: false } })
      else d = templateReply(d)
      return setReplyFor({ id: d.id, crisis })
    }
    d = templateReply(d)
    if (d.reply) setReplyFor({ id: d.id, crisis })
  }

  const quickMood = (key: string) => {
    const now = new Date()
    const d = persist({ ...newEntry(iso(now), hhmm(now)), emotions: [{ key, intensity: 3 }] })
    setToast({ text: `${names[key]}(으)로 기록했어요`, undo: () => { store.remove(d.id); bump(); doSync() } })
  }

  const ctx: Ctx = { store, entries, names, premium, bump, persist, openWrite: setWrite, openReply: setReplyFor, notify: setToast, sync: doSync, webAi, ads: adsOn(premium, !!demo) }
  const closeIntro = () => { store.set('introSeen', true); setIntro(false) }

  if (intro) return <Landing ads={ctx.ads} onStart={() => { closeIntro(); setWrite({}) }} onDemo={() => { closeIntro(); setDemo(demoStore()); setTab('today') }} onClose={closeIntro} />

  return (
    <div className="app">
      <nav className="rail">
        <div className="brand"><img src={`${BASE}oni-mark.svg`} alt="" /><b>온이 일기</b></div>
        {NAV.map(n => <button key={n.id} className={'nav ' + (tab === n.id ? 'on' : '')} onClick={() => setTab(n.id)}><Icon name={n.id} /><span>{n.label}</span></button>)}
        <button className="write-fab" onClick={() => setWrite({})} aria-label="쓰기"><Icon name="write" /><span>쓰기</span></button>
        <SyncBadge status={demo ? 'demo' : status} onConnect={connect} />
      </nav>
      <main className="main">
        {readOnly && <div className="notice">앱에서 더 새로운 데이터 형식을 쓰고 있어요. 웹은 읽기 전용으로 동작해요. 앱을 업데이트해 주세요.</div>}
        {tab === 'today' && <Today {...ctx} onQuick={quickMood} />}
        {tab === 'calendar' && <Calendar {...ctx} />}
        {tab === 'report' && <Report {...ctx} />}
        {tab === 'settings' && <Settings {...ctx} status={demo ? 'demo' : status} email={real?.get<string>('email')} lastBackup={real?.get<number>('lastBackupAt')} pending={real?.pending().length ?? 0}
          onConnect={connect} onDisconnect={disconnect} onUnlink={unlink} onSync={doSync} authErr={authErr} theme={theme} setTheme={setTheme}
          demo={!!demo} setDemoOn={on => { setDemo(on ? demoStore() : null); setTab('today') }} onIntro={() => setIntro(true)} />}
      </main>
      <nav className="tabbar">
        {NAV.slice(0, 2).map(n => <button key={n.id} className={tab === n.id ? 'on' : ''} onClick={() => setTab(n.id)}><Icon name={n.id} /><span>{n.label}</span></button>)}
        <button className="tab-write" onClick={() => setWrite({})} aria-label="쓰기"><span className="tab-write-btn"><Icon name="write" /></span><span>쓰기</span></button>
        {NAV.slice(2).map(n => <button key={n.id} className={tab === n.id ? 'on' : ''} onClick={() => setTab(n.id)}><Icon name={n.id} /><span>{n.label}</span></button>)}
      </nav>
      {write && <Write ctx={ctx} init={write} onClose={() => setWrite(null)} onSave={saveEntry} />}
      {replyFor && <ReplyView ctx={ctx} {...replyFor} onClose={() => setReplyFor(null)} />}
      {toast && <div className="toast" role="status">{toast.text}{toast.undo && <button onClick={() => { toast.undo!(); setToast(null) }}>되돌리기</button>}</div>}
    </div>
  )
}

type ReplyFor = { id: string; crisis: boolean; writing?: boolean }
interface Ctx {
  store: Store; entries: EntryDoc[]; names: Record<string, string>; premium: boolean; bump: () => void
  persist: (d: EntryDoc) => EntryDoc; openWrite: (w: { doc?: EntryDoc; date?: string; title?: string }) => void; openReply: (r: ReplyFor) => void
  notify: (t: { text: string; undo?: () => void }) => void; sync: () => void; webAi: boolean; ads: boolean
}

/** 온이(AI) only for model-written replies; library replies are 온이의 한마디 (AI_GROWTH §1). */
const ReplyBadge = ({ r }: { r: NonNullable<EntryDoc['reply']> }) => <span className="ai-badge">{r.source === 'llm' ? '온이(AI)' : '온이의 한마디'}</span>

const NAV = [{ id: 'today', label: '오늘' }, { id: 'calendar', label: '캘린더' }, { id: 'report', label: '리포트' }, { id: 'settings', label: '마이' }] as const

function Icon({ name }: { name: string }) {
  const p: Record<string, JSX.Element> = {
    today: <><path d="M5 14a7 7 0 0 1 14 0v1H5z" /><path d="M9 19h6M12 15v4" /></>,
    calendar: <><rect x="4" y="5" width="16" height="15" rx="3" /><path d="M4 10h16M9 3v4M15 3v4" /></>,
    report: <path d="M6 19V11M12 19V5M18 19v-6" />,
    settings: <><circle cx="12" cy="8" r="4" /><path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" /></>,
    write: <path d="M5 19l1-4L16 5l3 3L9 18zM14 7l3 3" />,
    search: <><circle cx="11" cy="11" r="6" /><path d="M20 20l-4.5-4.5" /></>,
    close: <path d="M6 6l12 12M18 6L6 18" />,
    phone: <path d="M6 3h3l2 5-2 1.5a11 11 0 0 0 5.5 5.5L16 13l5 2v3a2 2 0 0 1-2 2A16 16 0 0 1 4 5a2 2 0 0 1 2-2z" />,
    lock: <><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" /></>,
    left: <path d="M15 5l-7 7 7 7" />, right: <path d="M9 5l7 7-7 7" />,
  }
  return <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>{p[name]}</svg>
}

function SyncBadge({ status, onConnect }: { status: Status; onConnect: () => void }) {
  const t: Record<Status, string> = { local: '로그인 전 · 기록이 사라질 수 있어요', syncing: '드라이브와 맞추는 중…', ok: '드라이브 백업됨', error: '백업 안 됨 · 다시 시도할게요', verify: '백업 확인 실패 · 다시 시도할게요', needAuth: '드라이브 다시 연결', offline: '오프라인 · 연결되면 올릴게요', demo: '체험 모드' }
  return status === 'needAuth'
    ? <button className="sync warn" onClick={onConnect}>{t[status]}</button>
    : <div className={'sync ' + (status === 'ok' ? 'ok' : status === 'error' || status === 'verify' ? 'warn' : '')}>{t[status]}</div>
}

// ---------- 오늘 ----------
function Today({ entries, names, premium, openWrite, openReply, onQuick }: Ctx & { onQuick: (k: string) => void }) {
  const now = new Date(), today = iso(now)
  const todays = entries.filter(e => e.date === today).sort(byTime)
  const emos = todays.flatMap(e => e.emotions)
  const light = todays.length ? mixDay(todays.map(e => e.emotions)) : null
  const g = dominantGroup(emos)
  const q = useMemo(() => question(g, todays.flatMap(e => e.tags), timeSlot(now.getHours()), () => (now.getDate() * 7 % 10) / 10), [g, today])
  const lastReply = [...entries].sort(byTime).reverse().find(e => e.reply && !e.reply.reported)
  const say = !todays.length ? '불 켜 둘게, 오늘 얘기 들려줘.' : light?.off ? '괜찮아, 그런 날도 있어.' : `오늘 빛, ${names[todays[todays.length - 1].emotions[0]?.key] ?? ''} 쪽으로 켜졌어.`
  const yearAgo = entries.find(e => e.date === `${now.getFullYear() - 1}${today.slice(4)}`)
  return (
    <div className="page today" style={{ ['--amb' as string]: (light ?? { glow: BRAND.glow }).glow }}>
      <header className="top"><h1>{fmtDay(today)}</h1></header>
      <div className="cols">
        <section className="hero">
          <div className="bubble">{say}</div>
          <Oni light={light} emotions={emos} expr={todays.length ? undefined : 'sleep'} className="big" />
          {todays.length > 0 && <button className="ghost share-btn" onClick={() => shareImage(todayCard(fmtDay(today), todays, names, !premium), `온이-오늘의빛-${today}.png`)}>오늘의 빛 카드 저장</button>}
        </section>
        <div className="stack">
          <section className="card">
            <div className="row"><h3>지금 기분은 어때요?</h3><span className="hint">톡 누르면 바로 저장돼요</span></div>
            <div className="orbs">
              {QUICK_MOODS.map(k => { const gr = groupOf(GROUP_OF[k]); return (
                <button key={k} className="orb" onClick={() => onQuick(k)} style={{ ['--l' as string]: gr.light, ['--g' as string]: gr.glow }}><i /><span>{names[k]}</span></button>) })}
            </div>
          </section>
          <section className="card">
            <div className="row"><h3>하루의 빛</h3><span className="hint">오늘 {todays.length}번 기록했어요</span></div>
            <DayBand entries={todays} />
            {todays.length > 0 && <ul className="mini">{todays.map(e => <EntryRow key={e.id} e={e} names={names} onClick={() => openWrite({ doc: e })} />)}</ul>}
          </section>
          {q && <section className="card"><p className="hint">오늘의 질문</p><h3 className="q">{q.text}</h3>
            <button className="link" onClick={() => openWrite({ title: q.text })}>이 질문으로 쓰기 ›</button></section>}
          {yearAgo && <section className="card" onClick={() => openWrite({ doc: yearAgo })}><p className="hint">1년 전 오늘</p><p>{yearAgo.title || yearAgo.body.slice(0, 80)}</p></section>}
          {lastReply && <section className="card">
            <div className="row"><h3>최근 답장</h3><ReplyBadge r={lastReply.reply!} /></div>
            <button className="reply-mini" onClick={() => openReply({ id: lastReply.id, crisis: false })}>
              <Oni light={mix(lastReply.emotions)} emotions={lastReply.emotions} className="tiny" /><span>{lastReply.reply!.text}</span>
            </button>
          </section>}
        </div>
      </div>
    </div>
  )
}

function EntryRow({ e, names, onClick }: { e: EntryDoc; names: Record<string, string>; onClick: () => void }) {
  const l = mix(e.emotions)
  return (
    <li><button className="entry" onClick={onClick}>
      <span className="dot" style={{ background: l.glow }} />
      <span className="entry-main">
        <b>{e.title || e.body.split('\n')[0].slice(0, 40) || e.emotions.map(x => names[x.key]).join('·') || '기분 기록'}</b>
        <small>{e.date !== iso(new Date()) ? fmtDay(e.date) + ' · ' : ''}{e.timeUnknown ? '' : e.time} {e.emotions.map(x => names[x.key]).join('·')}{e.photos.length ? ` · 사진 ${e.photos.length}` : ''}</small>
      </span>
    </button></li>
  )
}

// ---------- 쓰기 ----------
function Write({ ctx, init, onClose, onSave }: { ctx: Ctx; init: { doc?: EntryDoc; date?: string; title?: string }; onClose: () => void; onSave: (d: EntryDoc) => void }) {
  const { store, names, bump } = ctx
  const draftKey = 'draft:' + (init.doc?.id ?? 'new')
  const [doc, setDoc] = useState<EntryDoc>(() => {
    const saved = store.get<EntryDoc>(draftKey)
    if (saved && (!init.doc || saved.updatedAt === init.doc.updatedAt)) return saved
    if (init.doc) return init.doc
    const now = new Date()
    return { ...newEntry(init.date ?? iso(now), hhmm(now)), title: init.title ?? '', template: init.title ? 'question' : 'free' }
  })
  const [active, setActive] = useState<string | null>(doc.emotions[doc.emotions.length - 1]?.key ?? null)
  const [saved, setSaved] = useState<'idle' | 'saving' | 'saved'>('idle')
  const [tagText, setTagText] = useState('')
  const [tagKind, setTagKind] = useState('free')
  const first = useRef(true)

  useEffect(() => { // autosave draft 1 s after typing stops
    if (first.current) { first.current = false; return }
    setSaved('saving')
    const t = setTimeout(() => { store.set(draftKey, doc); setSaved('saved') }, 1000)
    return () => clearTimeout(t)
  }, [doc])
  useEffect(() => { const k = (e: KeyboardEvent) => e.key === 'Escape' && onClose(); addEventListener('keydown', k); return () => removeEventListener('keydown', k) }, [onClose])

  const set = (p: Partial<EntryDoc>) => setDoc(d => ({ ...d, ...p }))
  const toggle = (key: string) => {
    const has = doc.emotions.find(e => e.key === key)
    if (key === 'unsure') { set({ emotions: has ? [] : [{ key, intensity: 3 }] }); setActive(has ? null : key); return }
    const rest = doc.emotions.filter(e => e.key !== 'unsure')
    if (has && active === key) { set({ emotions: rest.filter(e => e.key !== key) }); setActive(null) }
    else { if (!has) set({ emotions: [...rest, { key, intensity: 3 }] }); setActive(key) }
  }
  const act = doc.emotions.find(e => e.key === active && e.key !== 'unsure')
  // 감정 AI (E1 빛 미리보기, E2 추천 칩, E3 문장 빛, E6 제목 제안): analysed ~1 s after typing stops, in a worker.
  const ai = useAiState()
  const parts = useMemo(() => sentences(doc.body).slice(0, 40), [doc.body])
  const res = useAnalysis(doc.body.trim().length >= 6 ? [doc.body, ...parts.map(p => p.text)] : [])
  const whole = res?.[0], per = res && res.length === parts.length + 1 ? parts.map((p, i) => ({ ...p, a: res[i + 1] })) : null
  const suggested = whole && whole.emotions[0].key !== 'unsure' ? whole.emotions : []
  const preview = !doc.emotions.length && suggested.length ? suggested : null
  const titleIdea = !doc.title.trim() && per && per.length >= 2 ? suggestTitle(per, (doc.emotions[0] ?? suggested[0])?.key) : ''
  const [lit, setLit] = useState(false) // read view; never switches on by itself (would swap out the textarea mid-typing)
  const toggleLit = () => setLit(!lit)
  const light = doc.emotions.length ? mix(doc.emotions) : preview ? mix(preview) : null
  const finish = () => { store.set(draftKey, undefined); bump(); onSave(doc) }
  const discard = () => { store.set(draftKey, undefined); onClose() }
  const addTag = () => { const v = tagText.trim(); if (v && !doc.tags.some(t => t.value === v)) set({ tags: [...doc.tags, { kind: tagKind, value: v }] }); setTagText('') }

  return (
    <div className="sheet" role="dialog" aria-modal aria-label="일기 쓰기">
      <div className="sheet-in" style={{ ['--amb' as string]: (light ?? BRAND).glow }}>
        <header className="sheet-top">
          <button className="icon-btn" onClick={discard} aria-label="닫기"><Icon name="close" /></button>
          <div className="when">
            <input type="date" value={doc.date} max={iso(new Date())} onChange={e => e.target.value && set({ date: e.target.value })} aria-label="날짜" />
            <input type="time" value={doc.time} onChange={e => e.target.value && set({ time: e.target.value, timeUnknown: false })} aria-label="시각" />
          </div>
          <span className={'autosave ' + saved}>{saved === 'saving' ? '저장 중…' : saved === 'saved' ? '✓ 자동 저장됨' : ''}</span>
        </header>
        <div className="write-cols">
          <section className="pick">
            <div className="pick-head">
              <Oni light={light} emotions={doc.emotions.length ? doc.emotions : preview ?? []} expr={doc.emotions.length || preview ? undefined : 'default'} className={'mid' + (preview ? ' preview' : '')} />
              <div><h2>오늘 마음은<br />어떤 색이에요?</h2><p className="hint">{preview ? '글을 읽고 온이가 빛을 미리 켜 봤어요. 정하는 건 언제나 직접이에요.' : '여러 개 골라도 돼요. 빛이 섞여요.'}</p></div>
            </div>
            {ai.on && ai.phase === 'loading' && ai.progress < 1 && <p className="hint ai-load">감정 AI를 준비하고 있어요 · {Math.round(ai.progress * 100)}% <small>(처음 한 번만 받아요)</small></p>}
            {suggested.some(s => !doc.emotions.some(e => e.key === s.key)) && <div className="suggest">
              <span className="hint">AI가 읽은 마음</span>
              {suggested.filter(s => !doc.emotions.some(e => e.key === s.key)).map(s => { const g = groupOf(GROUP_OF[s.key]); return (
                <button key={s.key} className="chip sugg" style={{ ['--l' as string]: g.light, ['--g' as string]: g.glow }}
                  onClick={() => { set({ emotions: [...doc.emotions.filter(e => e.key !== 'unsure'), s] }); setActive(s.key) }}>+ {names[s.key]}</button>) })}
            </div>}
            <div className="emo-grid">
              {GROUPS.map(g => (
                <div className="emo-row" key={g.key}>
                  <span className="gname"><i style={{ background: g.glow }} />{g.name}</span>
                  {g.emotions.map(e => { const sel = doc.emotions.find(x => x.key === e.key); return (
                    <button key={e.key} className={'chip ' + (sel ? 'sel ' : '') + (active === e.key ? 'act' : '')} style={{ ['--l' as string]: g.light, ['--g' as string]: g.glow }} onClick={() => toggle(e.key)} aria-pressed={!!sel}>
                      {names[e.key]}{sel && <span className="pips">{[1, 2, 3, 4, 5].map(i => <i key={i} className={i <= sel.intensity ? 'on' : ''} />)}</span>}
                    </button>) })}
                </div>
              ))}
              <div className="emo-row unsure">
                <button className={'chip ' + (doc.emotions.some(e => e.key === 'unsure') ? 'sel act' : '')} style={{ ['--l' as string]: UNSURE.light, ['--g' as string]: UNSURE.glow }} onClick={() => toggle('unsure')}>{names.unsure}</button>
                <span className="hint">빛은 꺼진 채로 남아요</span>
              </div>
            </div>
            {act && <div className="card intensity">
              <div className="row"><h3>{names[act.key]}은(는) 얼마나 컸어요?</h3><span className="hint">{act.intensity} / 5</span></div>
              <div className="levels">{[1, 2, 3, 4, 5].map(i => { const g = groupOf(GROUP_OF[act.key] as GroupKey); return (
                <button key={i} className={'lv ' + (i === act.intensity ? 'on' : '')} onClick={() => set({ emotions: doc.emotions.map(e => e.key === act.key ? { ...e, intensity: i } : e) })}
                  style={{ ['--c' as string]: mixHex(g.light, g.glow, (i - 1) / 4), ['--s' as string]: `${26 + i * 5}px` }} aria-label={`강도 ${i}`}><i /><span>{['조금', '', '보통', '', '아주'][i - 1]}</span></button>) })}</div>
            </div>}
          </section>
          <section className="editor">
            {light && <Oni light={light} emotions={doc.emotions.length ? doc.emotions : preview ?? []} className={'tiny editor-oni' + (preview ? ' preview' : '')} />}
            <input className="title" placeholder="제목 (비워 두면 첫 문장)" value={doc.title} onChange={e => set({ title: e.target.value })} />
            {titleIdea && <button className="title-idea" onClick={() => set({ title: titleIdea })}><span className="hint">제목 제안</span> {titleIdea}</button>}
            {lit && per
              ? <div className="body lit" role="button" tabIndex={0} onClick={toggleLit} title="눌러서 이어 쓰기"><SentenceLight text={doc.body} parts={per} /></div>
              : <textarea className="body" placeholder="오늘 있었던 일, 떠오르는 대로 적어 봐요." value={doc.body} onChange={e => set({ body: e.target.value })} />}
            {ai.on && per && per.length > 0 && <label className="switch small-switch"><input type="checkbox" checked={lit} onChange={toggleLit} /><span>문장별 감정 빛 보기</span></label>}
            <div className="tags">
              {doc.tags.map(t => <button key={t.kind + t.value} className={'tag ' + (t.auto ? 'auto' : '')} onClick={() => set({ tags: doc.tags.filter(x => x !== t) })} title="눌러서 빼기">{TAG_LABEL[t.kind] ?? '태그'} · {t.value} ×</button>)}
              <span className="tag-add">
                <select value={tagKind} onChange={e => setTagKind(e.target.value)} aria-label="태그 종류">{Object.entries(TAG_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
                <input placeholder="태그 추가" value={tagText} onChange={e => setTagText(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && !e.nativeEvent.isComposing) { e.preventDefault(); addTag() } }} />
              </span>
            </div>
            {doc.photos.length > 0 && <Photos ids={doc.photos.map(p => p.id)} />}
            <div className="actions">
              {init.doc && <button className="ghost" onClick={() => {
                const id = doc.id
                store.trash(id); store.set(draftKey, undefined); bump(); ctx.sync(); onClose()
                ctx.notify({ text: '휴지통으로 옮겼어요', undo: () => { store.restore(id); bump(); ctx.sync() } })
              }}>지우기</button>}
              <button className="primary" disabled={!doc.emotions.length && !doc.body.trim() && !doc.title.trim()} onClick={finish}>저장</button>
            </div>
          </section>
        </div>
      </div>
    </div>
  )
}

/** E3: sentences the classifier reads as clearly emotional get an underline in that emotion's light. */
function SentenceLight({ text, parts }: { text: string; parts: { text: string; start: number; a: Analysis }[] }) {
  const out: JSX.Element[] = []; let at = 0
  parts.forEach((p, i) => {
    if (p.start > at) out.push(<span key={'t' + i}>{text.slice(at, p.start)}</span>)
    const k = p.a.emotions[0].key, g = GROUP_OF[k]
    out.push(g && p.a.strength >= 0.45 ? <mark key={i} style={{ ['--g' as string]: groupOf(g).glow }} title={DEFAULT_NAMES[k]}>{p.text}</mark> : <span key={i}>{p.text}</span>)
    at = p.start + p.text.length
  })
  out.push(<span key="end">{text.slice(at)}</span>)
  return <>{out}</>
}

function Photos({ ids }: { ids: string[] }) {
  const [urls, setUrls] = useState<(string | null)[]>([])
  useEffect(() => { if (connected()) Promise.all(ids.map(photoUrl)).then(setUrls) }, [ids.join()])
  return <div className="photos">{ids.map((id, i) => urls[i] ? <img key={id} src={urls[i]!} alt="" /> : <span key={id} className="ph">사진</span>)}</div>
}

// ---------- 답장 / 위기 안내 ----------
function ReplyView({ ctx, id, crisis, writing, onClose }: ReplyFor & { ctx: Ctx; onClose: () => void }) {
  const e = ctx.store.entries.get(id)?.doc
  if (!e) return null
  const report = () => {
    if (!confirm('이 답장을 신고할까요? 신고한 답장은 숨겨요. 신고 내용은 어디에도 보내지 않고 이 기기에만 남겨요.')) return
    try { const k = 'replyReports', list = JSON.parse(localStorage.getItem(k) ?? '[]'); list.push({ entryId: e.id, text: e.reply!.text, source: e.reply!.source, engine: e.reply!.engine ?? null, at: Date.now() }); localStorage.setItem(k, JSON.stringify(list)) } catch { /* ignore */ }
    ctx.persist({ ...e, reply: { ...e.reply!, reported: true } }); onClose()
  }
  const light = crisis ? mix([{ key: 'serene', intensity: 2 }, { key: 'sad', intensity: 3 }]) : mix(e.emotions)
  return (
    <div className="sheet" role="dialog" aria-modal aria-label={crisis ? '도움받을 수 있는 곳' : '온이 답장'}>
      <div className="sheet-in reply" style={{ ['--amb' as string]: light.glow }}>
        <header className="sheet-top"><button className="icon-btn" onClick={onClose} aria-label="닫기"><Icon name="close" /></button></header>
        <Oni light={light} emotions={crisis ? [{ key: 'sad', intensity: 3 }] : e.emotions} expr={crisis ? 'comfort' : exprFor(e.emotions, true)} className="mid" />
        {crisis ? <>
          <p className="center hint strong">이번엔 답장 대신 이 얘기를 꼭 하고 싶었어</p>
          <div className="card crisis">
            <p className="say">오늘 정말 많이 무거웠구나. 혼자 다 견디지 않아도 돼. 지금 이 마음을 같이 들어 줄 사람이 있어.</p>
            <p className="hint right">— 온이가</p>
            <hr />
            <a className="call" href="tel:109"><span className="ph-ic"><Icon name="phone" /></span><span><b>109</b><small>자살예방 상담 · 24시간</small></span><em>전화</em></a>
            <a className="call" href="tel:15770199"><span className="ph-ic"><Icon name="phone" /></span><span><b>1577-0199</b><small>정신건강 위기상담 · 24시간</small></span><em>전화</em></a>
            <p className="hint small">문자·카카오톡 상담도 109 누리집에서 할 수 있어요.</p>
          </div>
          <button className="link center" onClick={onClose}>괜찮아요, 일기로 돌아갈게요</button>
        </> : writing && !e.reply ? <>
          <p className="center hint strong">온이가 일기를 읽고 답장을 쓰고 있어요…</p>
          <p className="center hint small">이 기기 안에서만 써요. 일기는 밖으로 나가지 않아요.</p>
        </> : e.reply && !e.reply.reported ? <>
          <p className="center hint strong">오늘 빛이 바뀌었어요</p>
          <div className="card letter">
            <div className="row"><ReplyBadge r={e.reply} /><span className="hint">{new Date(e.reply.createdAt).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })}</span></div>
            <p className="say">{e.reply.text}</p>
            <p className="hint right">— 온이가</p>
            <div className="row">
              <span className="hint small">{e.reply.source !== 'llm' ? '온이의 한마디는 일기의 감정에 맞춰 고른, 미리 써 둔 문장이에요.'
                : e.reply.engine === 'chrome-builtin' ? 'AI가 쓴 답장이에요. 이 기기 안에서 썼고, 일기는 밖으로 나가지 않았어요. 틀린 말이 있을 수 있어요.'
                : 'AI가 쓴 답장이에요. 틀린 말이 있을 수 있어요.'}</span>
              <button className="link" onClick={report}>신고</button>
            </div>
          </div>
          <SimilarDays ctx={ctx} e={e} />
          <button className="primary" onClick={onClose}>고마워</button>
        </> : <><p className="center">일기를 저장했어요</p><SimilarDays ctx={ctx} e={e} /><button className="primary" onClick={onClose}>닫기</button></>}
        <p className="disclaimer">온이는 의료·상담 서비스가 아니에요.</p>
      </div>
    </div>
  )
}

/** Bodies for embedding-based features (E4/E5): newest first, capped. ponytail: cap 400, index if diaries get huge. */
const bodies = (entries: EntryDoc[], skip?: string) => [...entries].filter(x => x.id !== skip && x.body.trim().length >= 10).sort(byTime).reverse().slice(0, 400)

/** E4 비슷했던 날: only when the classifier has a sentence-embedding output (else hidden). */
function SimilarDays({ ctx, e }: { ctx: Ctx; e: EntryDoc }) {
  const { embedding } = useAiState()
  const past = useMemo(() => bodies(ctx.entries, e.id).filter(x => x.date <= e.date), [ctx.entries, e.id])
  const res = useAnalysis(embedding && e.body.trim().length >= 10 ? [e.body, ...past.map(x => x.body)] : [], 0)
  const hits = res?.[0].embedding ? nearest(res[0].embedding, past.map((x, i) => ({ item: x, v: res[i + 1].embedding! })), 3, 0.6) : []
  if (!hits.length) return null
  return (
    <section className="card">
      <div className="row"><h3>비슷했던 날</h3><span className="ai-badge">AI</span></div>
      <p className="hint">그때는 이렇게 지나갔어.</p>
      <ul className="mini">{hits.map(h => <EntryRow key={h.item.id} e={h.item} names={ctx.names} onClick={() => ctx.openWrite({ doc: h.item })} />)}</ul>
    </section>
  )
}

// ---------- 캘린더 · 타임라인 · 검색 ----------
function Calendar(ctx: Ctx) {
  const { entries, names, openWrite } = ctx
  const [view, setView] = useState<'month' | 'timeline'>('month')
  const [month, setMonth] = useState(() => iso(new Date()).slice(0, 7))
  const [sel, setSel] = useState(iso(new Date()))
  const [q, setQ] = useState('')
  const [emo, setEmo] = useState('')
  const dark = document.documentElement.dataset.theme === 'dark' || (!document.documentElement.dataset.theme && matchMedia('(prefers-color-scheme: dark)').matches)
  const byDate = useMemo(() => { const m = new Map<string, EntryDoc[]>(); for (const e of entries) m.set(e.date, [...(m.get(e.date) ?? []), e]); return m }, [entries])
  const [y, mo] = month.split('-').map(Number)
  const first = new Date(y, mo - 1, 1).getDay(), days = new Date(y, mo, 0).getDate()
  const shift = (n: number) => { const d = new Date(y, mo - 1 + n, 1); setMonth(iso(d).slice(0, 7)) }
  const dayEntries = (byDate.get(sel) ?? []).sort(byTime)
  const ql = q.trim().toLowerCase()
  const emoOk = (e: EntryDoc) => !emo || e.emotions.some(x => x.key === emo || GROUP_OF[x.key] === emo)
  const results = entries.filter(e => (!ql || `${e.title} ${e.body} ${e.tags.map(t => t.value).join(' ')}`.toLowerCase().includes(ql)) && emoOk(e))
    .sort(byTime).reverse()
  // E5 뜻으로 검색: entries close in meaning (embedding output only), added after the word matches.
  const { embedding } = useAiState()
  const pool = useMemo(() => bodies(entries), [entries])
  const sem = useAnalysis(embedding && view === 'timeline' && [...ql].length >= 2 ? [q.trim(), ...pool.map(x => x.body)] : [], 600)
  const shown = new Set(results.map(r => r.id))
  const meaning = sem?.[0].embedding ? nearest(sem[0].embedding, pool.map((x, i) => ({ item: x, v: sem[i + 1].embedding! })), 10, 0.5).map(h => h.item).filter(x => !shown.has(x.id) && emoOk(x)) : []
  return (
    <div className="page">
      <header className="top">
        {view === 'month' ? <div className="month-nav"><button className="icon-btn" onClick={() => shift(-1)} aria-label="이전 달"><Icon name="left" /></button><h1>{y}년 {mo}월</h1><button className="icon-btn" onClick={() => shift(1)} aria-label="다음 달"><Icon name="right" /></button></div> : <h1>타임라인</h1>}
      </header>
      <div className="seg"><button className={view === 'month' ? 'on' : ''} onClick={() => setView('month')}>캘린더</button><button className={view === 'timeline' ? 'on' : ''} onClick={() => setView('timeline')}>타임라인·검색</button></div>
      {view === 'month' ? (
        <div className="cols cal">
          <section>
            <div className="grid7 wk">{WEEK.map((w, i) => <span key={w} className={i === 0 ? 'sun' : ''}>{w}</span>)}</div>
            <div className="grid7">
              {Array.from({ length: first }, (_, i) => <span key={'b' + i} />)}
              {Array.from({ length: days }, (_, i) => {
                const date = `${month}-${String(i + 1).padStart(2, '0')}`, es = byDate.get(date) ?? []
                const bg = cellBg([...es], dark)
                return <button key={date} className={'cell ' + (bg ? 'lit ' : '') + (date === sel ? 'sel ' : '') + (date === iso(new Date()) ? 'today' : '')} style={{ background: bg }} onClick={() => setSel(date)}>{i + 1}</button>
              })}
            </div>
            <div className="legend">{GROUPS.map(g => <span key={g.key}><i style={{ background: g.glow }} />{g.name}</span>)}</div>
          </section>
          <section className="card day">
            <div className="row"><h3>{fmtDay(sel)}</h3><span className="hint">기록 {dayEntries.length}개</span></div>
            <DayBand entries={dayEntries} />
            <ul className="mini">{dayEntries.map(e => <EntryRow key={e.id} e={e} names={names} onClick={() => openWrite({ doc: e })} />)}</ul>
            <button className="ghost" onClick={() => openWrite({ date: sel })}>이 날 일기 쓰기</button>
          </section>
        </div>
      ) : (
        <section className="timeline">
          <div className="search">
            <Icon name="search" />
            <input type="search" placeholder="단어·태그로 찾기" value={q} onChange={e => setQ(e.target.value)} aria-label="검색" />
            <select value={emo} onChange={e => setEmo(e.target.value)} aria-label="감정으로 거르기">
              <option value="">모든 감정</option>
              {GROUPS.map(g => <optgroup key={g.key} label={g.name}><option value={g.key}>{g.name} 전체</option>{g.emotions.map(e => <option key={e.key} value={e.key}>{names[e.key]}</option>)}</optgroup>)}
            </select>
          </div>
          {results.length === 0 && !meaning.length && <p className="empty-text">찾는 기록이 없어요.</p>}
          <ul className="tl">{results.slice(0, 200).map((e, i) => (<Fragment key={e.id}>
            {i === 2 && <li className="tl-ad"><AdSlot on={ctx.ads} feed /></li>}
            <li className="card tl-item" onClick={() => openWrite({ doc: e })}>
              <div className="row"><span className="hint"><i className="dot" style={{ background: mix(e.emotions).glow }} /> {fmtDay(e.date)} {e.timeUnknown ? '' : e.time}</span><span className="hint">{e.emotions.map(x => names[x.key]).join(' · ')}</span></div>
              {e.title && <h3>{e.title}</h3>}
              {e.body && <p className="clamp">{e.body}</p>}
              {e.tags.length > 0 && <div className="tags">{e.tags.map(t => <span key={t.value} className="tag">{t.value}</span>)}</div>}
              {e.reply && !e.reply.reported && <p className="tl-reply"><ReplyBadge r={e.reply} /> {e.reply.text}</p>}
            </li></Fragment>))}
            {results.length === 2 && <li className="tl-ad"><AdSlot on={ctx.ads} feed /></li>}</ul>
          {meaning.length > 0 && <><p className="hint tl-sem">뜻이 비슷한 기록 <span className="ai-badge">AI</span></p>
            <ul className="tl">{meaning.map(e => <li key={e.id} className="card tl-item" onClick={() => openWrite({ doc: e })}>
              <div className="row"><span className="hint"><i className="dot" style={{ background: mix(e.emotions).glow }} /> {fmtDay(e.date)}</span></div>
              {e.title && <h3>{e.title}</h3>}{e.body && <p className="clamp">{e.body}</p>}</li>)}</ul></>}
        </section>
      )}
      <AdSlot on={ctx.ads} />
    </div>
  )
}

// ---------- 리포트 ----------
function Report({ entries, names, premium, ads }: Ctx) {
  const [month, setMonth] = useState(() => iso(new Date()).slice(0, 7))
  const [y, mo] = month.split('-').map(Number)
  const shift = (n: number) => setMonth(iso(new Date(y, mo - 1 + n, 1)).slice(0, 7))
  const es = entries.filter(e => e.date.startsWith(month))
  const emos = es.flatMap(e => e.emotions).filter(e => e.key !== 'unsure')
  const total = emos.reduce((s, e) => s + e.intensity, 0) || 1
  const byGroup = GROUPS.map(g => ({ g, v: emos.filter(e => GROUP_OF[e.key] === g.key).reduce((s, e) => s + e.intensity, 0) }))
  const counts = new Map<string, number>(); for (const e of es.flatMap(e => e.emotions)) counts.set(e.key, (counts.get(e.key) ?? 0) + 1)
  const top3 = [...counts].sort((a, b) => b[1] - a[1]).slice(0, 3)
  const days = new Set(es.map(e => e.date)).size
  const all = new Set(entries.map(e => e.date))
  let streak = 0; for (const d = new Date(); all.has(iso(d)); d.setDate(d.getDate() - 1)) streak++
  const tagCount = new Map<string, number>(); for (const t of es.flatMap(e => e.tags)) tagCount.set(t.value, (tagCount.get(t.value) ?? 0) + 1)
  const topTags = [...tagCount].sort((a, b) => b[1] - a[1]).slice(0, 5)
  const light = es.length ? mixDay(es.map(e => e.emotions)) : null
  const found = useMemo(() => causes(entries), [entries]) // E8 (all records: patterns need many days)
  const words = useMemo(() => keywords(es), [month, entries]) // E13
  const { embedding } = useAiState() // E7 이번 달의 한 줄 (embedding output only)
  const lines = useMemo(() => es.flatMap(e => sentences(e.body).map(x => x.text)).filter(t => [...t].length >= 8).slice(0, 300), [month, entries])
  const emb = useAnalysis(embedding && lines.length >= 3 ? lines : [], 0)
  const line = emb?.[0]?.embedding ? central(lines.map((t, i) => ({ item: t, v: emb[i].embedding! }))) : null
  return (
    <div className="page">
      <header className="top"><div className="month-nav"><button className="icon-btn" onClick={() => shift(-1)} aria-label="이전 달"><Icon name="left" /></button><h1>{y}년 {mo}월 리포트</h1><button className="icon-btn" onClick={() => shift(1)} aria-label="다음 달"><Icon name="right" /></button></div></header>
      <div className="cols">
        <div className="stack">
          <section className="card stats">
            <div><b>{es.length}</b><small>기록</small></div><div><b>{days}</b><small>기록한 날</small></div><div><b>{streak}</b><small>연속 기록(일)</small></div>
          </section>
          <section className="card">
            <h3>빛 분포</h3>
            {emos.length === 0 ? <p className="hint">이 달엔 켜진 빛이 없어요.</p> : <>
              <div className="dist">{byGroup.filter(x => x.v).map(x => <i key={x.g.key} style={{ flex: x.v, background: x.g.glow }} title={`${x.g.name} ${Math.round(x.v / total * 100)}%`} />)}</div>
              <ul className="dist-legend">{byGroup.map(x => <li key={x.g.key}><i style={{ background: x.g.glow }} />{x.g.name}<b>{Math.round(x.v / total * 100)}%</b></li>)}</ul>
            </>}
          </section>
          <section className="card">
            <h3>감정 TOP 3</h3>
            {top3.length === 0 ? <p className="hint">아직 기록이 없어요.</p> : <ol className="top3">{top3.map(([k, n], i) => {
              const g = GROUP_OF[k] ? groupOf(GROUP_OF[k]) : UNSURE
              return <li key={k}><span className="rank">{i + 1}</span><i className="orb-s" style={{ ['--l' as string]: g.light, ['--g' as string]: g.glow }} /><b>{names[k]}</b><span className="hint">{n}번</span></li> })}</ol>}
          </section>
          {topTags.length > 0 && <section className="card"><h3>자주 함께한 것</h3><div className="tags">{topTags.map(([t, n]) => <span key={t} className="tag">{t} · {n}</span>)}</div></section>}
          <section className="card">
            <h3>감정 원인 찾기</h3>
            {found.length === 0 ? <p className="hint">기록이 더 쌓이면 어떤 날 어떤 빛이 자주 켜지는지 찾아 줄게요. 태그를 달면 더 잘 보여요.</p>
              : <ul className="causes">{found.map(c => { const g = groupOf(c.group); return (
                <li key={c.label + c.group}><i style={{ background: g.glow }} /><span><b>{c.label}</b>엔 <b>{g.name}</b> 빛이 더 자주 켜졌어요</span><em>+{Math.round(c.diff * 100)}%</em></li>) })}</ul>}
            <p className="hint small">지금까지의 기록 전체에서 직접 고른 감정으로 계산해요. 같은 조건의 기록이 3개 이상일 때만 보여요.</p>
          </section>
          {words.length > 0 && <section className="card">
            <h3>키워드 구름</h3>
            <div className="cloud">{words.map((w, i) => <span key={w.word} className={w.tag ? 'tagw' : ''} style={{ fontSize: `${13 + Math.round(10 * w.n / words[0].n)}px`, opacity: 1 - i * 0.025 }}>{w.word}</span>)}</div>
          </section>}
        </div>
        <div className="stack">
          <section className="card center-card">
            <Oni light={light} emotions={emos} expr={es.length ? undefined : 'sleep'} className="mid" />
            <p className="say">{es.length ? `${mo}월엔 ${days}일 동안 빛을 켜 줬어. 고마워.` : '이번 달 이야기도 기다릴게.'}</p>
            {es.length > 0 && <button className="ghost" onClick={() => shareImage(monthCard(y, mo, es, !premium), `온이-${month}-빛캘린더.png`)}>{mo}월 빛 캘린더 카드 저장</button>}
          </section>
          {line && <section className="card"><div className="row"><h3>이번 달의 한 줄</h3><span className="ai-badge">AI</span></div><p className="say">“{line}”</p></section>}
          <YearLight entries={entries} premium={premium} year={y} />
        </div>
      </div>
      <AdSlot on={ads} />
    </div>
  )
}

function YearLight({ entries, premium, year }: { entries: EntryDoc[]; premium: boolean; year: number }) {
  if (!premium) return (
    <section className="card locked">
      <Icon name="lock" /><div><h3>올해의 빛</h3><p className="hint">365칸 빛 모자이크는 프리미엄 기능이에요. 앱에서 프리미엄을 구매하고 같은 Google 계정으로 로그인하면 웹에서도 열려요.</p></div>
    </section>)
  const byDate = new Map<string, Emo[]>(); for (const e of entries) if (e.date.startsWith(String(year))) byDate.set(e.date, [...(byDate.get(e.date) ?? []), ...e.emotions])
  const cells = []; for (let d = new Date(year, 0, 1); d.getFullYear() === year; d.setDate(d.getDate() + 1)) { const k = iso(d); const em = byDate.get(k); cells.push(<i key={k} title={k} style={{ background: em ? mix(em).glow : undefined }} />) }
  return <section className="card"><h3>{year}년, 올해의 빛</h3><div className="mosaic">{cells}</div></section>
}

// ---------- 마이 ----------
function Settings(p: Ctx & { status: Status; email?: string; lastBackup?: number; pending: number; onConnect: () => void; onDisconnect: () => void; onUnlink: () => Promise<void>; onSync: () => void; authErr: string; theme: string; setTheme: (t: string) => void; demo: boolean; setDemoOn: (on: boolean) => void; onIntro: () => void }) {
  const ai = useAiState()
  return (
    <div className="page settings">
      <header className="top"><h1>마이</h1></header>
      <div className="cols">
        <div className="stack">
          <section className="card">
            <h3>백업 · 계정</h3>
            {!CLIENT_ID ? <p className="warn-text">설정 필요: Google 로그인 클라이언트 ID가 아직 없어요. 지금은 이 기기에만 잠시 저장돼요.</p>
              : p.email ? <>
                <p className="kv"><span>Google 계정</span><b>{p.email}</b></p>
                <p className="kv"><span>마지막 백업</span><b>{ago(p.lastBackup)}</b></p>
                <p className="kv"><span>올릴 기록</span><b>{p.pending}개</b></p>
                <div className="btns">
                  {connected() && p.status !== 'needAuth' ? <button className="ghost" onClick={p.onSync}>지금 백업</button> : <button className="primary" onClick={p.onConnect}>드라이브 다시 연결</button>}
                  <button className="ghost" onClick={p.onDisconnect}>로그아웃</button>
                </div>
                {API && <Unlink onUnlink={p.onUnlink} />}
              </> : <>
                <p className="hint">앱과 같은 Google 계정으로 로그인하면, 앱에서 쓴 일기를 여기서도 읽고 쓸 수 있어요. 일기는 내 Google 드라이브에만 저장되고 우리 서버로 보내지 않아요. 로그인 유지와 프리미엄 확인에만 우리 서버를 써요. 로그인하지 않으면 방문 기록을 지우거나 다른 기기에서 열 때 일기가 사라져요.</p>
                <button className="primary google" onClick={p.onConnect}>Google로 로그인</button>
              </>}
            {p.authErr && <p className="warn-text">{p.authErr}</p>}
          </section>
          <Trash {...p} />
          <Export {...p} />
          <section className="card">
            <h3>화면</h3>
            <div className="seg">{[['system', '시스템'], ['light', '라이트'], ['dark', '다크']].map(([k, v]) => <button key={k} className={p.theme === k ? 'on' : ''} onClick={() => p.setTheme(k)}>{v}</button>)}</div>
          </section>
          <section className="card">
            <h3>체험 모드</h3>
            <p className="hint">예시 일기로 화면을 둘러봐요. 드라이브에 올라가지 않고, 끄면 사라져요.</p>
            <label className="switch"><input type="checkbox" checked={p.demo} onChange={e => p.setDemoOn(e.target.checked)} /><span>예시 데이터 보기</span></label>
          </section>
        </div>
        <div className="stack">
          <section className="card">
            <h3>감정 AI</h3>
            <p className="hint">쓰는 동안 빛 미리보기, 감정 추천, 문장별 감정 빛, 제목 제안을 해요. 앱과 같은 감정 AI가 이 기기 안에서만 돌아가고, 일기는 어디로도 보내지 않아요.</p>
            <label className="switch"><input type="checkbox" checked={ai.on} onChange={e => setEmotionAi(e.target.checked)} /><span>감정 AI 쓰기</span></label>
            <p className="hint small">{ai.phase === 'ready' ? '준비됐어요. 다음부터는 받지 않아요.' : ai.phase === 'loading' ? `준비하고 있어요 · ${Math.round(ai.progress * 100)}%` : ai.phase === 'error' ? '이 기기에서는 감정 AI를 불러오지 못했어요. 직접 고르는 기능은 그대로 쓸 수 있어요.' : `처음 쓸 때 한 번만 약 ${FIRST_LOAD_MB}MB를 받아 이 기기에 저장해요.`}</p>
          </section>
          <section className="card">
            <h3>온이의 답장</h3>
            {p.webAi
              ? <p className="hint">여기서는 온이가 직접 답장을 써요(온이(AI) 표시). 이 기기 안의 AI가 처리하고, 일기는 밖으로 나가지 않아요. 잘 안 되면 온이의 한마디로 대신해요.</p>
              : <p className="hint">일기에 맞춰 고른 온이의 한마디(미리 써 둔 문장)가 와요. 크롬 내장 AI를 이미 쓸 수 있는 PC 크롬에서는 온이가 직접 답장을 써요.</p>}
            <p className="hint">힘든 마음이 담긴 표현이 보이면 답장 대신 도움받을 곳을 먼저 알려 드려요. 이 확인도 이 기기 안에서만 해요.</p>
            <p className="hint">온이는 의료·상담 서비스가 아니에요. 힘든 마음이 계속되면 109(자살예방 상담) 또는 1577-0199(정신건강 위기상담)에 연락해 주세요.</p>
          </section>
          <section className="card">
            <h3>프리미엄</h3>
            {p.premium ? <><p className="kv"><span>상태</span><b>프리미엄 적용 중</b></p><p className="hint">광고 없이 쓰고, 올해의 빛도 열려요. 고마워요!</p></> : <>
              <p className="kv"><span>가격</span><b>₩3,300 · 평생</b></p>
              <p className="hint">프리미엄은 Android 앱에서 구매할 수 있어요. 앱과 같은 Google 계정으로 로그인하면 웹에서도 광고가 사라지고 프리미엄이 적용돼요.</p>
              <a className="link" href={PLAY_URL} target="_blank" rel="noopener">Android 앱 다운로드 (Google Play) ›</a>
            </>}
          </section>
          <section className="card links">
            <button className="link" onClick={p.onIntro}>온이 일기 소개 ›</button>
            <a href={PLAY_URL} target="_blank" rel="noopener">Android 앱 다운로드 (Google Play) ›</a>
            <a href={`${BASE}legal/privacy.html`}>개인정보 처리방침 ›</a>
            <a href={`${BASE}legal/terms.html`}>이용약관 ›</a>
          </section>
        </div>
      </div>
    </div>
  )
}

/** 마이 › 백업·계정 › 계정 연동 해제 (in-page confirm). */
function Unlink({ onUnlink }: { onUnlink: () => Promise<void> }) {
  const [ask, setAsk] = useState(false)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const go = async () => { setBusy(true); setErr(''); try { await onUnlink(); setAsk(false) } catch (e) { setErr((e as Error).message) } finally { setBusy(false) } }
  if (!ask) return <div className="btns"><button className="link" onClick={() => setAsk(true)}>계정 연동 해제</button></div>
  return (
    <div className="card confirm" role="alertdialog" aria-label="계정 연동 해제">
      <p><b>계정 연동을 해제할까요?</b></p>
      <p className="hint">이 Google 계정과 온이 서버의 로그인 유지 연결, 프리미엄 연결을 지워요. 드라이브에 있는 일기는 그대로 남아요. 결제 기록(주문번호·시각)은 법에 따라 5년간 계정과 분리해 보관해요. Play에서 산 프리미엄은 앱에서 같은 계정으로 다시 로그인하면 다시 연결돼요.</p>
      {err && <p className="warn-text">{err}</p>}
      <div className="btns">
        <button className="ghost" onClick={() => setAsk(false)} disabled={busy}>취소</button>
        <button className="primary" onClick={go} disabled={busy}>{busy ? '해제하는 중…' : '연동 해제'}</button>
      </div>
    </div>
  )
}

const daysLeft = (trashedAt: number) => Math.min(30, Math.max(1, Math.ceil((trashedAt + TRASH_KEEP_MS - Date.now()) / 86400000)))

/** 설정 › 휴지통: 지운 일기는 30일 동안 여기 있다가 완전히 지워져요 (Android와 같은 규칙). */
function Trash({ store, names, bump, sync }: Ctx) {
  const list = store.trashed()
  const act = (f: () => void) => { f(); bump(); sync() }
  return (
    <section className="card">
      <h3>휴지통</h3>
      <p className="hint">지운 기록은 30일 동안 여기 있다가 완전히 지워져요. 앱과 웹 어디서 지워도 같이 보여요.</p>
      {list.length === 0 ? <p className="hint">휴지통이 비어 있어요</p> : <>
        {list.map(d => (
          <div key={d.id} className="trash-row">
            <span className="dot" style={{ background: mix(d.emotions).glow }} />
            <div className="trash-text"><b>{d.title.trim() || d.body.split('\n').find(l => l.trim()) || d.emotions.map(e => names[e.key]).join(' · ') || '빈 기록'}</b><span>{fmtDay(d.date)} {d.time} · {daysLeft(d.trashedAt!)}일 남음</span></div>
            <button className="ghost small" onClick={() => act(() => store.restore(d.id))}>복원</button>
            <button className="ghost small" onClick={() => confirm('이 기록을 영구 삭제할까요? 되돌릴 수 없어요.') && act(() => store.remove(d.id))}>영구 삭제</button>
          </div>
        ))}
        <div className="btns"><button className="ghost" onClick={() => confirm(`휴지통의 기록 ${list.length}개를 완전히 지울까요? 되돌릴 수 없어요.`) && act(() => store.emptyTrash())}>비우기</button></div>
      </>}
    </section>
  )
}

function Export({ store, names }: Ctx) {
  const [withTrash, setWithTrash] = useState(false)
  const download = () => {
    const docs = [...store.entries.values()].map(r => r.doc).filter(d => !d.deleted || (withTrash && d.trashedAt != null))
    const url = URL.createObjectURL(new Blob([exportZip(docs, names)], { type: 'application/zip' }))
    const a = document.createElement('a'); a.href = url; a.download = `온이일기-${iso(new Date())}.zip`; a.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
  return (
    <section className="card">
      <h3>내보내기</h3>
      <p className="hint">일기를 zip 파일(JSON + 읽기용 HTML)로 내려받아요. 사진 원본은 Android 앱의 마이 › 내보내기에서 함께 저장할 수 있어요.</p>
      <label className="switch"><input type="checkbox" checked={withTrash} onChange={e => setWithTrash(e.target.checked)} /><span>휴지통의 기록도 넣기</span></label>
      <div className="btns"><button className="ghost" onClick={download}>내보내기 (JSON+HTML zip)</button></div>
    </section>
  )
}

// ---------- 소개 (W3: first screen before any diary / login, and 마이 › 온이 일기 소개) ----------
const FEATURES = [
  { t: '톡 누르면 빛이 켜져요', d: '지금 기분을 한 번 누르면 온이가 그 색으로 켜져요. 하루에 여러 번 남기면 하루의 빛이 이어져요.' },
  { t: '쓰는 동안 빛이 변해요', d: '감정 AI가 글을 읽고 빛을 미리 켜 보고, 감정과 제목을 제안해요. 정하는 건 언제나 직접이에요.' },
  { t: '문장마다 마음의 색', d: '마음이 진하게 담긴 문장에 그 감정의 빛으로 밑줄을 그어 줘요.' },
  { t: '어떤 날 어떤 빛이 켜질까', d: '누구와, 어디서, 어떤 요일에 어떤 감정이 자주 켜지는지 리포트가 찾아 줘요.' },
  { t: '온이의 답장', d: '일기를 저장하면 온이가 한마디 건네요. 힘든 말이 보이면 도움받을 곳부터 알려 줘요.' },
  { t: '앱과 같은 일기장', d: 'Android 앱과 같은 Google 계정으로 로그인하면, 내 드라이브의 일기를 PC에서도 이어서 써요.' },
]
const INTRO_LIGHTS: Emo[][] = [[{ key: 'joy', intensity: 4 }], [{ key: 'serene', intensity: 4 }], [{ key: 'flutter', intensity: 4 }], [{ key: 'sad', intensity: 3 }], [{ key: 'tired', intensity: 4 }]]

function Landing({ ads, onStart, onDemo, onClose }: { ads: boolean; onStart: () => void; onDemo: () => void; onClose: () => void }) {
  const [i, setI] = useState(0)
  useEffect(() => { const t = setInterval(() => setI(v => (v + 1) % INTRO_LIGHTS.length), 2600); return () => clearInterval(t) }, [])
  const emos = INTRO_LIGHTS[i], light = mix(emos)
  return (
    <div className="landing" style={{ ['--amb' as string]: light.glow }}>
      <header className="land-top"><span className="brand"><img src={`${BASE}oni-mark.svg`} alt="" /><b>온이 일기</b></span><button className="link" onClick={onClose}>건너뛰기</button></header>
      <section className="land-hero">
        <Oni light={light} emotions={emos} className="big" />
        <div className="land-copy">
          <h1>기분이 빛이 되는 일기장</h1>
          <p className="say">무드등 온이가 오늘 마음의 색으로 켜져요. 감정 AI가 글을 읽고 빛을 미리 켜 보고, 기록이 쌓이면 내 마음의 패턴을 찾아 줘요.</p>
          <div className="btns">
            <button className="primary" onClick={onStart}>웹에서 바로 쓰기</button>
            <button className="ghost" onClick={onDemo}>예시로 둘러보기</button>
          </div>
          <a className="play" href={PLAY_URL} target="_blank" rel="noopener">Android 앱 다운로드 · Google Play ›</a>
        </div>
      </section>
      <section className="land-grid">{FEATURES.map((f, k) => { const g = GROUPS[k % GROUPS.length]; return (
        <div key={f.t} className="card"><i className="orb-s" style={{ ['--l' as string]: g.light, ['--g' as string]: g.glow }} /><h3>{f.t}</h3><p className="hint">{f.d}</p></div>) })}</section>
      <section className="card land-privacy">
        <h3>일기는 내 것으로만 남아요</h3>
        <p className="hint">Google로 로그인하면 일기가 내 Google 드라이브에 안전하게 저장되고, 앱과 같은 일기장을 써요. 일기는 우리 서버로 보내지 않아요(로그인 유지와 프리미엄 확인만 서버를 거쳐요). 로그인 없이도 바로 써 볼 수 있지만, 그때는 방문 기록을 지우거나 다른 기기에서 열면 일기가 사라져요. 감정 AI도 이 기기 안에서만 돌아요. 만 14세 이상만 이용할 수 있어요.</p>
        <p className="hint small"><a href={`${BASE}legal/privacy.html`}>개인정보 처리방침</a> · <a href={`${BASE}legal/terms.html`}>이용약관</a> · 온이는 의료·상담 서비스가 아니에요.</p>
      </section>
      <AdSlot on={ads} />
      <footer className="land-foot">
        <a href={`${BASE}guide/how-to.html`}>감정일기 쓰는 법</a>
        <a href={`${BASE}guide/mood-tracker.html`}>무드트래커로 마음 돌보기</a>
        <a href={`${BASE}guide/start-emotion-diary.html`}>감정일기 처음 시작하기</a>
        <a href={`${BASE}guide/three-line-diary.html`}>하루 3줄 일기</a>
        <a href={`${BASE}guide/choose-mood-tracker.html`}>무드트래커 앱 고르는 법</a>
        <a href={`${BASE}guide/why-mood-log.html`}>기분 기록이 좋은 이유</a>
        <a href={`${BASE}guide/year-review.html`}>연말 1년 돌아보기</a>
        <a href={`${BASE}guide/faq.html`}>자주 묻는 질문</a>
      </footer>
    </div>
  )
}
