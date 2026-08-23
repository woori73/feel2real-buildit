import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
export default defineConfig({
  base: '/problem-generator/wiki/',
  plugins: [react()],
  server: {
    proxy: {
      '^(/preview/[^/]+)?/api/': {
        target: 'http://127.0.0.1:8000',
        rewrite: (p) => p.replace(/^\/preview\/[^/]+/, ''),
      },
    },
  },
})
