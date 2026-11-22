// Google AdSense banner. Only calendar, report and the landing page (mirrors the Android app);
// never on write/edit, reply/crisis, lock or settings.
import { useEffect } from 'react'

declare global { interface Window { adsbygoogle?: unknown[] } }

const env = () => ({ client: import.meta.env.VITE_ADSENSE_CLIENT ?? '', slot: import.meta.env.VITE_ADSENSE_SLOT_BANNER ?? '' })
export const adsOn = (premium: boolean, demo: boolean) => { const { client, slot } = env(); return !!client && !!slot && !premium && !demo }

let loaded = false
export function AdSlot({ on }: { on: boolean }) {
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
  }, [on])
  if (!on) return null
  const { client, slot } = env()
  return <div className="ad"><span className="ad-label">광고</span><ins className="adsbygoogle" style={{ display: 'block' }} data-ad-client={client} data-ad-slot={slot} data-ad-format="auto" data-full-width-responsive="true" /></div>
}
