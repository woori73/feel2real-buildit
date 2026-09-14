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
        // BuildIt Brand Colors
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
        // Semantic colors
        background: '#fafbfd',
        foreground: '#14181f',
        muted: {
          DEFAULT: '#f1f5f9',
          foreground: '#64748b',
        },
        border: '#e2e8f0',
        ring: '#3b82f6',
      },
      fontFamily: {
        sans: ['Pretendard Variable', 'Pretendard', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
      borderRadius: {
        DEFAULT: '16px',
        sm: '10px',
        lg: '24px',
      },
      spacing: {
        '1': '4px',
        '2': '8px',
        '3': '12px',
        '4': '16px',
        '5': '20px',
        '6': '24px',
        '8': '32px',
        '10': '40px',
        '12': '48px',
      },
      boxShadow: {
        'sm': '0 1px 2px rgba(20, 24, 31, 0.05)',
        DEFAULT: '0 4px 12px rgba(20, 24, 31, 0.08)',
        'lg': '0 12px 32px rgba(20, 24, 31, 0.12)',
      },
      transitionDuration: {
        DEFAULT: '200ms',
      },
      transitionTimingFunction: {
        DEFAULT: 'cubic-bezier(0.4, 0, 0.2, 1)',
      },
    },
  },
  plugins: [],
}