# REA 기반 메가스터디·BuildIt 홈페이지 비교 조사 및 개편 설계 — 산출물 모음

- 작성일: **2026-10-16** (조사 시각 10:01–11:40 KST)
- 대상: `https://www.megastudy.net/megastudy.asp` (참고) / `https://feel2real.co.kr/` (개편 대상)
- 코드 기준: `woori73/feel2real-buildit` @ `69fe4bd356cf561a536ded77367f8f79bc6c7720` (2026-10-16 09:54 KST)
- 방법: 실제 브라우저(Playwright MCP)로 렌더·스크롤·클릭 후 `getBoundingClientRect()` px 실측 + 비로그인 게이트는 `curl`로 분리 검증 + 코드 대조
- **단계**: 조사·분석·설계만 수행. **운영 코드 수정 / PR 병합 / 운영 배포 / AWS·운영 DB 변경 / 기존 Builder·Wiki·Chart·TESTPREP·Ledger 기능 변경 없음.** 소유자 승인 전 구현 시작 금지.

## 문서 목록

| 파일 | 내용 |
|---|---|
| [01-megastudy-reference-analysis.md](01-megastudy-reference-analysis.md) | 메가스터디 PC/모바일 IA 실측, GNB·아이콘·HOT PICK·바텀내비·드로어 전수, 가져올 원칙 P1–P7 / 가져오지 않을 것, 미검증 목록 |
| [02-buildit-current-audit.md](02-buildit-current-audit.md) | BuildIt 라이브 감사: 첫 화면 인터랙티브 11개 전수, 섹션 지도, 라벨↔URL 불일치, 두 개의 홈, 링크 인벤토리 30개, 익명 게이트, 결함 우선순위 P0–P3 |
| [03-buildit-service-inventory.md](03-buildit-service-inventory.md) | 서비스 전수 7열 표(운영중/준비중 구분), 코드 라우트·권한 근거, **Report·Shorts 미존재 정정**, 첫 화면 노출 권장 8개 |
| [04-buildit-home-redesign-spec.md](04-buildit-home-redesign-spec.md) | 개편 설계: A/B/C/D 영역 스펙, 카드 8장 확정 데이터, 버튼 동작 규약, 반응형 기준점, **기존 기능 보존 조건**, 검증 계획 V1–V8 |
| [05-buildit-home-wireframes.md](05-buildit-home-wireframes.md) | 안 A/B/C × PC 1440 + 모바일 390 와이어프레임 + 비교표 |
| [06-screen-captures.md](06-screen-captures.md) | 캡처 25종 매니페스트(주석 6종 번호 대응표) + 재현 절차·함정 |
| [07-claude-implementation-handoff.md](07-claude-implementation-handoff.md) | **최종 권장안 + Claude 구현 지시**: 6 스텝, 수용기준 AC1–AC12, 승인 질문 Q1–Q7, 링크 계약표 |

이미지 증거: `./captures/`

---

## 결론 요약

### 조사가 확인한 사실 (모두 px 실측)

| 항목 | 실측 |
|---|---|
| BuildIt PC 첫 화면(0–900px)의 서비스 진입 링크 | **0개** — 인터랙티브 11개 중 이동 링크 5개(로고+헤더4), 헤더 4개 중 **2개가 라벨과 다른 서비스로 이동** |
| BuildIt 모바일 첫 화면(0–844px)의 서비스 진입 링크 | **0개** (햄버거 1클릭 + 행 1클릭 = 2클릭 필요) |
| `#services`(서비스 카드 섹션) 위치 | PC **y=3453 = 3.8화면** / 모바일 **y=4523 = 5.4화면** |
| 문서 길이 | PC **8732px** / 모바일 **11005px(약 13화면)** |
| 헤더 라벨 오류 | `BUILD IT WIKI` → `/problem-generator`(제작기), `Buildit 모의고사` → `/wiki`(내신위키) — **상호 교차** |
| 메가스터디 동등 기능 위치 | 아이콘 바로가기 **y=705 = 0.78화면**, GNB 7개 전부 1클릭直达 |

### 초기 가정 3건이 실측으로 기각/정정되었다

1. **"서비스 카드 섹션이 없다" → 기각.** `main`의 커밋 `c02aa0a3`(#705)에 이미 존재했고, **조사 도중 10:03–10:05 운영 배포**(전 경로 502)로 신 빌드에 반영됨. 문제는 콘텐츠 부재가 아니라 **첫 화면 노출·순서·라벨 정확성**.
2. **"BuildIt Report / BuildIt Shorts가 서비스다" → 미존재.** `/report`·`/shorts` **404**. 코드는 홈의 **관리형 영상 슬롯**(`LandingVideoSlotKey.ACADEMY_SHORTS_DEMO` / `PARENT_REPORT_DEMO`)일 뿐. `/conversation/report`은 "성장 추세 리포트 곧 제공돼요".
3. **"모바일 드로어에 로그인이 없어 가입 경로가 유실된다" → 정정.** `.auth-actions`(로그인·회원가입)는 `<=860px`에서도 헤더에 노출(390px 렌더 확인: 로그인 x156, 회원가입 x230). 실제 내비 기준점은 900이 아니라 **860**.

### 권장안: **안 C 혼합형**

```
PC 1440 × 900
├ A 헤더(68)          [로고] 내신위키 · 문제지 제작기 · CHART · TestPrep   [로그인][회원가입][문의]
├ C-1 배너+소식(300)   좌 620: AI 홈페이지 제작 입력 폼(#hero에서 이관)  /  우 380: 소식 3그룹
└ B 서비스 카드(504)   4열 × 2행 = 8장  ← /builder /problem-generator /wiki /chart
                                          /testprep /ledger /designcraft /admissions/guide
  (기존 #nation·#hero상세·#wiki·#services상세·#soon·#chart-showcase·#faq·#cta는 삭제 없이 아래로)
```

| 개선 효과 | 현재 → 권장안 |
|---|---|
| 첫 화면 서비스 노출 | 0 → **8 (설명·배지 포함) + 빌더 배너 = 9** |
| 주요 기능 도달 클릭 | 2~3 → **1** |
| 서비스 목록 도달 스크롤 | 3.8화면 → **0.41화면** (모바일 5.4 → 0.36) |
| 헤더 라벨↔URL 불일치 | 4개 중 2개 → **0** |
| AI 홈페이지 제작 | 폴드 밖(`#hero` y=1046) → **폴드 좌상단 1순위, 폼 마크업·submit 로직 불변** |

### 지금 필요한 것: 소유자 승인 7건 (`07` §4)

핵심 트레이드오프는 **Q4** — 현행 폴드 100%를 점유하는 `#nation`(전국 무대, 링크 0개)을 폴드 밖으로 내리는 것. 이것을 승인하지 않으면 900px에 카드 8장이 들어갈 수 없다.
그 외: Q1 `F2R` 헤더 제거 / Q2 `이지장부` 첫 화면 노출 / Q3 `입시상담` 카드화 / Q5 소식 패널 정적 HTML / Q6 문서 길이 압축 2단계 분리 / Q7 배지 문구 4종 확정.

### 이 설계가 일부러 하지 않는 것

- 문서 길이 압축(PC 8732 / MB 11005) → 2단계 과제
- `/home`(F2R)과 `/`의 IA 통합 → 라우팅 개편 필요로 범위 밖 (이번엔 헤더 링크 강등만)
- 메가스터디의 배너 3중 스와이퍼·이벤트 칩 22개·1480 고정 캔버스·별도 모바일 호스트 → **복제 금지 원칙에 따라 제외**
- 검색 UI → BuildIt에 대응 라우트 없음(미검증). **추측 URL 생성 금지** 지시 준수
