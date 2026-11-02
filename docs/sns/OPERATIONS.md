# 인스타 자동 피드 운영 설정

작업 지시서: `docs/handoff/instagram-auto-feed/HANDOFF.md`(PR #970). 화면: `/admin/sns`(관리자 로그인).

## 흐름

1. 매일 19:00(서울) 켜진 규칙이 실행된다(매분 확인, 하루 한 번, 실행 시각부터 3시간 안).
2. 문구: 운영 서버가 Meta Muse Spark(기존 `META_JIPJIGI_API_KEY`)로 바로 만든다. 브랜드 지시·금지 표현·최근 30개 주제를 넣고, 본문의 가격·할인·비율 숫자와 금지 표현은 걸러서 1회 다시 만든다. 고정 문구는 서버가 그대로 붙인다.
3. 이미지: runyour.ai GPU(RTX A5000)에서 도는 작업자(`gpu-pipeline/sns_worker`)가 작업을 가져가 ComfyUI로 만들고 JPEG를 올린다. GPU가 꺼져 있으면 작업은 대기열에 남아 있다가 GPU를 켜면 처리된다.
4. 문구·이미지가 모두 끝나면 콘텐츠 > 검토 대기. 사람이 보고 '지금 발행' 또는 '예약 발행'. 자동 발행은 1차에서 꺼져 있다.

## 운영 환경변수(EC2 `/etc/f2r/env`)

| 변수 | 값 | 없을 때 |
|---|---|---|
| `SNS_MONTHLY_BUDGET_KRW` | 운영 기본 30000(application-prd.yaml, 2026-11-02 승인) | 0이면 생성 안 함 |
| `SNS_WORKER_TOKEN` | 임의 긴 문자열(예: `openssl rand -hex 32`). GPU 작업자에도 같은 값 | 작업자 API 503 → 이미지 못 만듦 |
| `SNS_TOKEN_KEY_BASE64` | 32바이트 base64(`openssl rand -base64 32`). **바꾸면 기존 연결을 다시 해야 함** | 계정 연결 불가 |
| `SNS_META_APP_ID`, `SNS_META_APP_SECRET` | Meta 개발자 앱의 Instagram 앱 ID·비밀 | 계정 연결 불가 |
| `SNS_META_REDIRECT_URI` | 비우면 `https://feel2real.co.kr/admin/sns/accounts/callback` | — |
| `SNS_SCHEDULER_ENABLED` | 운영 기본 true | — |
| `SNS_GPU_KRW_PER_HOUR` | 1261 | — |

비용 기록: 이미지 = GPU 작업 시간 × 1,261원/시간(올림), 문구 = `SNS_TEXT_COST_KRW_PER_CALL`(기본 0). 상한에 닿으면 새 생성·작업 내주기를 멈추고 홈에 알린다.

## Meta 앱 등록·계정 연결(계정 주인이 직접)

1. developers.facebook.com → 앱 만들기 → 'Instagram' 제품 추가 → **Instagram API with Instagram Login** 설정.
2. 권한: `instagram_business_basic`, `instagram_business_content_publish`.
3. OAuth 리디렉션 URI에 `https://feel2real.co.kr/admin/sns/accounts/callback` 등록.
4. 앱 ID·비밀을 위 환경변수로 넣고 재시작.
5. `/admin/sns/accounts` → '인스타그램 계정 연결' → 인스타그램 로그인·동의(@woorinaesin, 비즈니스/크리에이터 계정).
6. 규칙 편집에서 계정을 고른다. 계정 없이도 생성·검토는 되지만 발행은 안 된다.

앱이 개발 모드면 앱 역할(테스터)로 등록된 계정만 연결된다. 일반 공개(앱 검수)는 필요할 때 따로 진행한다.

## 처음 켜는 순서

1. 환경변수 설정 → 배포/재시작.
2. GPU 켜기 → `gpu-pipeline/sns_worker/README.md`대로 ComfyUI·모델·작업자 실행.
3. `/admin/sns/rules`에서 규칙마다 '시험 생성' → 검토 대기/시험 결과에서 얼굴이 참고 이미지와 같은지 사람이 확인.
4. 괜찮으면 규칙 '켜기'. 다음 19:00부터 매일 쌓인다.
5. 1주일 동안 로보MD 결과와 비교 → 로보MD 무료 60회 소진 시점에 로보MD 규칙은 대표가 끈다.

## 하지 않는 것(1차 범위 밖)

Threads 발행, 카드뉴스(여러 장), 성과 리포트, 자동 발행 켜기.
