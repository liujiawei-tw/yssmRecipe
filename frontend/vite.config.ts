import react from '@vitejs/plugin-react'
import path from 'node:path'
import { tmpdir } from 'node:os'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: process.platform === 'win32'
      ? path.join(tmpdir(), 'yssmRecipe-frontend-dist')
      : 'dist-temp',
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
