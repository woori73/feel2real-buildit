import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    outDir: '../../src/main/resources/static/design-lab-gallery',
    emptyOutDir: true,
    manifest: true,
    rollupOptions: {
      input: {
        main: path.resolve(__dirname, 'index.html'),
        preview: path.resolve(__dirname, 'preview.html'),
        zones: path.resolve(__dirname, 'zones.html'),
      },
    },
  },
  server: {
    port: 5174,
    strictPort: true,
    // Dev-only proxy so ORIGINAL academy media (/api, /video) loads
    // same-origin in the preview. Under Spring these paths resolve
    // on the host directly (no proxy needed in production).
    proxy: {
      '/api': {
        target: 'https://eduholic.feel2real.co.kr',
        changeOrigin: true,
      },
      '/video': {
        target: 'https://eduholic.feel2real.co.kr',
        changeOrigin: true,
      },
    },
  },
});
