import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// 문법 문제은행 독립 페이지(/grammar-items) 전용 빌드. base를 /grammar-items/로分離해
// 자산이 /problem-generator/** 보안에 묶이지 않고, modu·ADMIN 최소 권한 페이지가 따로 서빙된다.
export default defineConfig({
  base: '/grammar-items/',
  plugins: [react()],
  build: {
    outDir: 'dist-grammar-items',
    emptyOutDir: true,
    rollupOptions: {
      input: 'grammar-items.html',
    },
  },
})
