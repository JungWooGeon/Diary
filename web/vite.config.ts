import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  base: '/Diary/',
  plugins: [react()],
  worker: { format: 'es' }, // emotion.worker.ts is a module worker (LiteRT.js)
  server: { fs: { allow: ['..'] } }, // shared/*.json lives in the repo root
})
