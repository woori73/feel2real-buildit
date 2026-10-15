# HANDOFF — NewStart 접속회원 + 내부 쪽지 (HOME → ACADEMY)

> 집 PC 작업 종료, 학원 PC OpenCode 세션 시작 시 이 문서 + GitHub 최신
> `feat/newstart-presence-dm-20261001` / PR #464만 읽고 이어간다.
> 머지·배포 금지 (PR #464 OPEN 유지).

## 1. 현재 branch / PR / HEAD SHA

- branch: `feat/newstart-presence-dm-20261001` (origin/main 추적)
- PR: #464 `feat(newstart): online members sidebar + internal DMs` — **OPEN, CI success**
- HEAD SHA: 아래 `git log`로 학원 PC에서 확인 (이 문서 작성 후 1개 커밋 추가됨)
- base: origin/main `c45a6b4c` 시점 (PR #462 병합 직후)

## 2. 완료된 작업

- V1018 마이그레이션: `newstart_member_presence` + `newstart_direct_messages`
  (신규 테이블만, 기존 행·GRANT/OAuth 변경 없음)
- 백엔드 `domain/newstart/presence/`: entity 2, repository 2, service 2
  (`NewStartPresenceService`, `NewStartDirectMessageService`),
  `NewStartDmRateLimiter`(10건/1분), controller 2
  (`/api/newstart/presence`, `/api/newstart/dm` — `/api/**` catch-all로
  로그인 회원만, 비로그인 401, SecurityConfig 변경 없음)
- UI(HOME 전용): 모바일 왼쪽 엣지 탭 `접속회원` + 미읽음 배지,
  PC(1024px↑) 왼쪽 레일 유지, 왼쪽 슬라이드 패널 1종
  (접속 목록·닉네임 탭→즉시 작성·받은함 확인/읽음/삭제·숨김/수신거부),
  `newstart-presence.js`(textContent만), CSS는 `newstart.css` 말미 추가
- `index.html` 변경: 엣지 탭·레일·패널 마크업 + presence.js 스크립트 태그 +
  CSS 버전 `v=20261001-4`. 기존 카드·문구·버튼 구조 변경 없음
- docs: `DOMAIN.md` 말미 V1018 섹션, `API.md` 채팅 섹션 다음에 presence/DM 섹션
- 금지영역 0건 변경 확인 (Grant/ThanksRoster/Entitlement/OAuth/Naver/RAG/모델/채팅)

## 3. 완료된 테스트

- Java 단위 14 pass: `NewStartPresenceServiceTest`(4) +
  `NewStartDirectMessageServiceTest`(10: 전송·본인/없는회원/수신거부/도배·읽음·삭제·수신함)
- node 계약 27 pass: presence 4 + home 7 + motion 16
  (`newstart_presence_contract.test.js` 신규)
- CI success: PR #464 Build Check 36813741297

## 4. 미완료 테스트와 문제

- **모바일 390px 렌더 QA 미완료.** 로컬 boot로 잡은 2건은 수정·컴파일까지만 확인:
  - (a) `newstart_member_presence`에 `created_at` 누락 → Hibernate validate 실패
    → V1018에 `created_at` 추가 해결 (미적용 파일이라 직접 수정, 안전)
  - (b) `NewStartPresenceService` 생성자 2개 + `@Autowired` 오배치 →
    `Clock` 빈 요구 실패 → **3-arg 생성자에 `@Autowired`로 확정**
    (단일 생성자 autowire + 테스트용 4-arg 직접 호출 패턴)
- 로컬 boot 최종 상태: 위 수정 후 재부팅하다 집 PC 종료 지시로 중단.
  390px 실측(Playwright 스크린샷)은 학원 PC에서 수행 필요.

## 5. V1017 migration 충돌 상황

- Claude PR #463(OPEN, `feat/newstart-thanks-member-self-verify`)이
  `V1017__add_newstart_thanks_claims.sql` 사용 중.
- 이 브랜치는 충돌 회피를 위해 **V1018로 renumber 완료**
  (`V1018__add_newstart_presence_and_direct_messages.sql`).
- #463이 먼저 머지돼도 충돌 없음. 반대로 이쪽이 먼저 머지되면 #463 측이
  renumber해야 함 → PR #464 본문에 명시해 둠.

## 6. bootRun 반복 원인과 마지막 상태

- 1차: 공유 컨테이너명 충돌 회피용 QA 전용 MySQL(`f2r_mysql_qa390`, 3408포트)
  띄우고 boot → 위 (a) 스키마 오류로 실패.
- 2차: V1018 수정 + QA DB 재생성 후 boot → 위 (b) 생성자 오류로 실패.
- 3차: `@Autowired` 확정 후 재부팅 중 종료 지시 → **boot 미완료 상태로 중단**.
- 정리됨: 내 bootRun 프로세스 없음(8080 free 확인), QA 컨테이너는 학원 PC
  작업 전 `docker rm -f f2r_mysql_qa390` 권장(아래 미완료에 남김 —
  집 PC에서 제거하지 못했으면 학원에서 제거).
- 건드리지 않은 것: `f2r_mysql_main` 등 타 세션 컨테이너, Gradle 데몬,
  `feel2real-wt-*` 프로세스 전부.

## 6-2. DM 삭제 정책 (리뷰용)

- `DELETE /api/newstart/dm/{id}` — 수신자 본인만, row 하드 삭제.
  1:1 단일 복사본이라 발신 측에도 함께 사라진다. 수정 endpoint 없음
  (생성 + 읽음 표시 + 수신자 삭제만). AGENTS.md CRUD 규칙상 머지 전
  리뷰 요청 사항으로 PR 본문에 기재함.

## 7. 학원 PC에서 가장 먼저 이어서 할 작업

1. `git fetch origin && git checkout feat/newstart-presence-dm-20261001 &&
   git log --oneline -3` (HEAD에 본 HANDOFF 포함 확인)
2. `docker rm -f f2r_mysql_qa390` (남아 있다면 — 임시 QA용, 타 세션 무관)
3. QA 전용 MySQL + `bootRun`(포트 3408, `run-local.sh` 대신 env 직접 지정 —
   기존 `f2r_mysql_main`은 타 세션 것이므로 사용 금지) 후
   Playwright 390px 스크린샷: (a) 비로그인 HOME (진입점 숨김·깨짐 없음),
   (b) 패널 강제 오픈 상태 (JS로 hidden 제거 후 촬영 — 인증 플로우 아님 명시)
4. 결과 이상 없으면 PR #464에 QA 결과 코멘트 후 머지 지시 대기
   (머지·배포는 사용자 지시 후)
