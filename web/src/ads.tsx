// Google AdSense banner. Only calendar, report and the landing page (mirrors the Android app);
// never on write/edit, reply/crisis, lock or settings.
// When AdSense leaves the slot empty (site not approved yet, no inventory, ad blocker) the same spot shows our own
// app promo instead — never a test ad (data-adtest is for development only).
import { useEffect, useRef, useState } from 'react'

declare global { interface Window { adsbygoogle?: unknown[] } }

const PLAY_URL = 'https://play.google.com/store/apps/details?id=com.pass.diary'
const env = () => ({ client: import.meta.env.VITE_ADSENSE_CLIENT ?? '', slot: import.meta.env.VITE_ADSENSE_SLOT_BANNER ?? '' })
export const adsOn = (premium: boolean, demo: boolean) => { const { client, slot } = env(); return !!client && !!slot && !premium && !demo }

let loaded = false
export function AdSlot({ on }: { on: boolean }) {
  const ins = useRef<HTMLModElement>(null)
  const [empty, setEmpty] = useState(false)
  useEffect(() => {
    if (!on) return
    if (!loaded) {
      loaded = true
      const s = document.createElement('script')
      s.async = true; s.crossOrigin = 'anonymous'
      s.src = `https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${encodeURIComponent(env().client)}`
      document.head.appendChild(s)
    }
    try { (window.adsbygoogle ??= []).push({}) } catch { /* ad blocker / already filled */ }
    const el = ins.current
    if (!el) return
    const check = () => setEmpty(el.getAttribute('data-ad-status') === 'unfilled')
    const mo = new MutationObserver(check)
    mo.observe(el, { attributes: true, attributeFilter: ['data-ad-status'] })
    // No answer at all after 6 s (script blocked): treat as empty.
    const t = setTimeout(() => { if (!el.getAttribute('data-ad-status')) setEmpty(true) }, 6000)
    return () => { mo.disconnect(); clearTimeout(t) }
  }, [on])
  if (!on) return null
  const { client, slot } = env()
  return (
    <div className={empty ? 'ad ad-empty' : 'ad'}>
      {!empty && <span className="ad-label">광고</span>}
      <ins ref={ins} className="adsbygoogle" style={{ display: empty ? 'none' : 'block' }} data-ad-client={client} data-ad-slot={slot} data-ad-format="auto" data-full-width-responsive="true" />
      {empty && (
        <a className="house-ad" href={PLAY_URL} target="_blank" rel="noopener">
          <b>폰에서도 온이와 함께</b>
          <span>위젯으로 1초 기록하고, 쓰는 동안 빛이 바뀌어요 · Android 앱 다운로드 ›</span>
        </a>
      )}
    </div>
  )
}
