import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const adsense = loadEnv(mode, process.cwd()).VITE_ADSENSE_CLIENT
  return {
    base: '/Diary/',
    plugins: [react(), {
      // index.html has the AdSense verification meta with %VITE_ADSENSE_CLIENT%; drop it when unset (no literal placeholder).
      name: 'adsense-meta',
      transformIndexHtml: { order: 'pre', handler: (html: string) => adsense ? html : html.replace(/\s*<meta name="google-adsense-account"[^>]*>/, '') },
    }],
    worker: { format: 'es' }, // emotion.worker.ts is a module worker (LiteRT.js)
    server: { fs: { allow: ['..'] } }, // shared/*.json lives in the repo root
  }
})
