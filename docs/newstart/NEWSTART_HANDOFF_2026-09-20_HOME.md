# NewStart Home Handoff — 2026-09-20

## Git
Branch:
feat/newstart-official-kb

Latest known SHA:
68696fed

Main merge:
NO

Deploy:
NO

## Completed — Official KB

OFFICIAL_NEWSTART 수집/구조화 완료.

데이터 파일:
- data/newstart/official_knowledge.json (58 records, CURRENT/SUPERSEDED 관리)
- data/newstart/official_documents.json (17 types with situation/method/alternative/caution)
- data/newstart/official_links.json (22 verified URLs)
- data/newstart/user_facing.json (10 요약 항목 with sourceKnowledgeIds traceability)
- data/newstart/match_program.json (4분기 decision-support + disclaimer)
- docs/newstart/official/OFFICIAL_SOURCE_INDEX.md (13 메뉴, 24 URL, 핵심 사실 스냅샷)
- docs/newstart/official/notices/NOTICES.md (49 공지 분류, 12 본문, 충돌/대체 관계)
- docs/newstart/official/ATTACHMENTS.md (19종 첨부, IMPORTANT UNPARSED 3건)
- docs/newstart/official/COVERAGE_AUDIT.md (14 내부 페이지, 49 공지 분류표)
- docs/newstart/SOURCE_LAYERS.md (OFFICIAL > COMMUNITY > BOOK > CASE > AI_SYNTHESIS)
- docs/newstart/NEWSTART_UI_PROPOSAL.md (13 USER-FACING 후보, retrieval 설계)
- docs/newstart/NEWSTART_NAVER_OAUTH_HANDOFF_2026-09-19.md (별도 OAuth 작업 인계)

현재 공식 source layer: READY

## Completed — Official-style UI

새출발기금 공식사이트(https://www.newstartfund.or.kr)의 UX 문법을 참고하여 NewStart 자체 UI로 구현.

공식 logo/image/assets는 복사하지 않음. CSS-only 방식.

구현된 섹션 (index.html 순서):
1. **Hero** — "내 새출발, 어디부터 확인할까" + eyebrow(비공식 서비스 선언)
2. **3 Task Cards** (공식 sec02 문법: navy/red/white, 좌하단 화살표, hover 이동)
   - 나도 대상일까? → #ns-official
   - 내 상황 물어보기 → #ns-ai (기존 AI)
   - 공식 신청·조회 → 공식 Index (외부, target=_blank)
3. **7 Quick Menu** (공식 sec03 문법: 흰 카드, 좌측 제목, 우측 화살표, 첫 항목 red, hover 그림자)
   - 대상 안내 / 감면·상환 / 신청 절차 / 필요 서류 (내부 anchor)
   - FAQ / 상담센터 / 협약 금융기관 (공식 외부 링크)
4. **Official Guide** (OFFICIAL DB v2 기반 4카드 + 7 공식 액션 링크 + 사칭 주의 배너 + 출처 표기)
5. **Recent Updates** (최근 제도 변경 3건: 26.6.25/26.5.6/26.3.25, 공식 공지 링크)
6. **Latest Feed** (3-source 탭 + 모바일 퍼스트 카드 피드)
7. **Reduction Prediction** (플레이스홀더 진입점, 실제 계산 미구현)
8. 기존: **NewStart AI** / **회원 채팅** / **나의 상태** — 모두 보존

기존: AI / 채팅 / 로그인 / 상태 패널 — 모두 보존 (JS id/Controller 무변경).

CSS: src/main/resources/static/css/newstart.css (애드온만, 기존 체계 일치)
JS: src/main/resources/static/js/newstart.js (initFeed fetch/render/tab-switch 추가)

## Latest Feed

현재 production candidate feed에는 검증 완료된 OFFICIAL_NEWSTART 3건만 존재.

| id | sourceArticleId | title | publishedDate | verified |
|----|----------------|-------|---------------|----------|
| os_001 | 56 | 지원체계 보완 — 재산심사 강화·변제능력별 차등·사해행위 조사 | 2026-06-25 | ✅ |
| os_002 | 54 | 비상장주식 보유내역 제출 의무화 시행 | 2026-04-30 | ✅ |
| os_003 | 52 | 제도개선 — 성실상환 인센티브·상환유예 확대·재기지원 확대 | 2026-03-25 | ✅ |

**제거됨 (검증 불가 — 네이버 카페 로그인 필요):**
- NEWSTART_CAFE 3건 (idx 124589, 124542, 124498)
- OFFICIAL_CCRS 2건 (ccrs_20260918_001, ccrs_20260912_001)

향후 사용자가 실제 Chrome에서 확인한 뒤 verified record만 다시 추가.

Canonical source:
data/newstart/feeds/latest_links.json

Deploy copy (Spring Boot static serving):
src/main/resources/static/data/newstart/feeds/latest_links.json

두 파일은 **byte-identical**이어야 함.

Validation script:
scripts/validate_feed_json.py (canonical vs deploy byte-for-byte + 구조 검증)

## Mobile UI

Mobile-first.

375 / 390 / 430px PASS.

Feed 타이틀: 17px
Summary: 15px (≥15px requirement 충족)
Touch card: 44px+ equivalent (카드 전체 클릭)
Horizontal overflow: 없음
Feed source tabs만 mobile horizontal scroll 허용 (overflow-x: auto)

## Reduction Prediction

"감면율 예측" 진입 UI만 구현됨 (ns-predict section, disabled button + ghost link to #ns-official).

실제 계산 Algorithm: **NOT IMPLEMENTED**.

가짜 예측값을 제공하지 않는다.

향후 구현 방향:
- Official Rule Engine (공식 제도 규칙)
- + Case Matching (실제 확정사례 매칭)
- + AI Explanation (결과의 이유/주요 변수/신뢰도)

## Canonical DB status

docs/newstart/CANONICAL_DB_STATUS.md 참고.

현재 상태:

| Source | 상태 | 비고 |
|--------|------|------|
| OFFICIAL_NEWSTART | ✅ READY | 58 records, CURRENT/SUPERSEDED 관리 |
| OFFICIAL_FSC | ❌ NOT YET | 금융위원회 자료 미수집 (별도 Agent) |
| BOOK | ❌ NOT YET | 전자책/정리자료 미수집 (별도 작업) |
| COMMUNITY | ❌ NOT YET | 카페 데이터 미수집 (별도 Agent, 마지막 수집 진행 중) |
| CASE | ❌ NOT YET | 상담 사례 미수집 (향후 상담 기록에서 생성) |

CANONICAL DB BUILD: **NOT YET COMPLETE**

구축 순서:
1. OFFICIAL_NEWSTART + OFFICIAL_FSC → 공식 규정 통합
2. + BOOK → 실무 설명/판단 로직 보강
3. + COMMUNITY + CASE → 실제 사례/경험 보강
4. 우선순위: 공식 최신자료 > 전자책 > 실제 사례

## Parallel work — DO NOT OVERWRITE

다른 Agent가 별도 작업 중 (feat/newstart-official-kb에서 임의 수정/덮어쓰기 금지):

1. **Cafe collection** — 감면율 확정금액 예측 게시판 마지막 수집.
2. **FSC** — 금융위원회 새출발기금 관련 자료 수집.
3. **BOOK** — 전자책 source layer 구축.

이 작업들은 feat/newstart-official-kb에서 임의 수정/덮어쓰기 금지.

## Cafe strategy

현재 대량 sequential cafe collection은 감면율 게시판 마지막 수집 후 종료 예정.

향후 운영 방식:
- 사용자가 유용한 카페 글을 Chrome에서 직접 열기
- articleId/body/comments 추출
- 기존 COMMUNITY/CASE JSON에 incremental append

## CCRS latest feed

신용회복위원회 공식 네이버 카페:
https://cafe.naver.com/ccrs5500

향후 사용자가 직접 확인한 새출발기금/부실차주 관련 게시물만 선별하여 latest feed에 추가.

OFFICIAL_CCRS (신복위 직접 작성) 와 CCRS_COMMUNITY (회원 글) 를 구분하여 sourceType 관리.

## Next major phase

각 source 작업 완료 후:

OFFICIAL_NEWSTART
+ OFFICIAL_FSC
+ BOOK
+ COMMUNITY
+ CASE

→ NEWSTART CANONICAL KNOWLEDGE DB 구축.

원본 source DB는 각각 독립 보존. 그 위에 Canonical layer 생성.

우선순위: 공식 최신자료 > 전자책 > 실제 사례.

## Prediction Dataset

감면율 카페 자료 중 가능한 CASE를:

INPUT CONDITIONS
EXPECTED RESULT
ACTUAL RESULT

형태로 별도 구조화 (개인식별정보 비노출).

그 후 감면율 예측 v1 개발 (Rule Engine + Case Matching + AI Explanation).

현재 단계에서 임의 계산식 생성 금지.

## Home PC recommended order

집 PC에서 바로 merge/deploy하지 않는다.

우선 순서:
1. 이 HANDOFF 읽기
2. Cafe 마지막 수집 결과 확인
3. FSC 결과 확인
4. BOOK source 상태 확인
5. 각 source validation
6. Canonical DB 설계/구축

그 후 UI branch merge/deploy 여부 결정.

==================================================
3. HANDOFF만 COMMIT
==================================================