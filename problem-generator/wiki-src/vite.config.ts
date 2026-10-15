import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
export default defineConfig({
  base: '/problem-generator/wiki/',
  plugins: [react()],
  test: { environment: 'jsdom' },
  server: {
    proxy: {
      '/problem-generator/wiki/api/': {
        target: 'http://127.0.0.1:8000',
        rewrite: (path) => path.replace(/^\/problem-generator\/wiki/, ''),
      },
      '^(/preview/[^/]+)?/api/': {
        target: 'http://127.0.0.1:8000',
        rewrite: (p) => p.replace(/^\/preview\/[^/]+/, ''),
      },
    },
  },
})
