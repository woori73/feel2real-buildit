/**
 * EASY 자동수집 Playwright Worker — 설정.
 *
 * 보안 규칙:
 * - WORKER_AUTH_TOKEN: BuildIt ↔ Worker 간 인증 토큰 (환경변수, 필수)
 * - ALLOWED_URL_PATTERNS: 허용된 URL 패턴 (P1에서는 로컬 테스트 사이트만)
 * - 인증정보는 Worker 메모리에만 보관, AI 모델에 전달 금지
 */
module.exports = {
  port: parseInt(process.env.WORKER_PORT || '3101', 10),
  authToken: process.env.WORKER_AUTH_TOKEN || '',
  // P1: 로컬 테스트 사이트만 허용. 실제 금융 사이트는 별도 승인 후 추가.
  allowedUrlPatterns: (process.env.WORKER_ALLOWED_URLS || 'http://127.0.0.1:*,http://localhost:*').split(','),
  // 다운로드 제한
  maxFileSizeBytes: parseInt(process.env.WORKER_MAX_FILE_SIZE || String(10 * 1024 * 1024), 10), // 10MB
  allowedExtensions: ['.csv', '.xlsx', '.xls'],
  // 타임아웃 (ms)
  browserLaunchTimeout: 30000,
  pageNavigationTimeout: 30000,
  downloadTimeout: 60000,
  jobTimeout: 300000, // 5분
  // 작업별 임시 디렉토리
  tempDir: process.env.WORKER_TEMP_DIR || '/tmp/easy-worker',
};
