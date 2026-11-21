import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  base: '/Diary/',
  plugins: [react()],
  server: { fs: { allow: ['..'] } }, // shared/*.json lives in the repo root
})
