/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        ink: '#0a0a0c',
        paper: '#fafbfd',
        navy: {
          DEFAULT: '#0f2a7a',
          dark: '#0a1f5c',
          light: '#1e3f9e',
        },
        accent: {
          DEFAULT: '#3b82f6',
          light: '#60a5fa',
          soft: '#eff6ff',
        },
        muted: {
          DEFAULT: '#f1f5f9',
          foreground: '#64748b',
        },
        border: '#e2e8f0',
      },
      fontFamily: {
        sans: ['Pretendard Variable', 'Pretendard', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
