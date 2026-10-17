# EASY 자동수집 Playwright Worker

BuildIt과 분리된 Node.js 프로세스로 실행되는 브라우저 자동화 Worker입니다.

## 실행 방법

```bash
cd easy-collection-worker
npm install
npx playwright install chromium
WORKER_AUTH_TOKEN=<토큰> npm start
```

## 환경변수

| 변수 | 필수 | 기본값 | 설명 |
|------|------|--------|------|
| WORKER_AUTH_TOKEN | ✅ | - | BuildIt ↔ Worker 인증 토큰 |
| WORKER_PORT | - | 3101 | 리스닝 포트 |
| WORKER_ALLOWED_URLS | - | http://127.0.0.1:*,http://localhost:* | 허용 URL 패턴 (콤마 구분) |
| WORKER_MAX_FILE_SIZE | - | 10485760 | 다운로드 최대 크기 (바이트) |
| WORKER_TEMP_DIR | - | /tmp/easy-worker | 임시 디렉토리 |

## API

- `POST /jobs` — 다운로드 작업 요청 (인증 필요)
- `GET /jobs/:jobId` — 작업 상태 조회 (인증 필요)
- `GET /health` — 헬스 체크 (인증 불필요)

## 보안

- P1에서는 로컬 테스트 사이트만 허용 (`WORKER_ALLOWED_URLS`)
- 임의 URL·내부망 접근 차단 (url-guard)
- 다운로드 파일 형식 (.csv/.xlsx/.xls) 및 크기 제한
- 작업별 브라우저 컨텍스트 격리
- 인증정보는 Worker 메모리에만 보관, AI 모델에 전달 금지
- 작업 완료·실패 시 임시파일 자동 삭제

## P1 제한

- 실제 금융 사이트 로그인 금지
- 실제 인증서·OTP 처리 금지
- 로컬 테스트 사이트만 사용
