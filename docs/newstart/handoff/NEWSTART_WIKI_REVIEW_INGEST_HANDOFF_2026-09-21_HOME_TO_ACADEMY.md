# NewStart 부실차주 Wiki PHASE 2.5 — HOME → ACADEMY HANDOFF (2026-09-21)

## 1. 작업 목적

공식 조사자료(#343 CCRS 부실우려, #344 비교 DB) → Wiki Knowledge 후보 → REVIEW ingest →
evidence 연결 → ADMIN preview → Top10 실제 차주 질문 검증까지의 안전한 연결 구축.
REVIEW→ACTIVE 자동승인 없음. AI production Router 연결 없음.

## 2. 시작 SHA

- `270af6d` (origin/main, PR #342 merge 지점)

## 3. 현재 branch

- `feat/newstart-wiki-review-ingest-20260921`
- worktree(집 PC): `C:\Users\sxxra\feel2real-wt-ns-wiki-0921`
- #343/#344를 merge하지 않음. 필요 파일만 `git checkout <remote-branch> -- <path>`로 참조.

## 4. 현재 HEAD SHA

- `28191f9` — "feat(newstart): PHASE 2.5 REVIEW ingest 연결 (공식자료→Wiki, ACTIVE 생성 없음)"
- push 완료: `origin/feat/newstart-wiki-review-ingest-20260921`

## 5. 완료된 작업

- f268c7c Wiki 인프라 cherry-pick (newstartknowledge 도메인 21파일 + V983 migration + ADMIN UI).
  V976→V983 번호정책 준수改名. lambda 닫기괄호 2건 최소수정.
- research-official comparison 14건 + ccrs 15건을 merge 없이 worktree에 참조.
- `NewstartOfficialReviewIngestService`: 원자 단위 분할(비교행→scheme 2건, 재산/채무 매트릭스, evidence,
  개념, ccrs), status REVIEW 강제, title 멱등성, k02 CONTRADICTS 관계, UNKNOWN/CONFLICT 보존.
- `NewstartTop10Answerability`: 근거 충분성 판정 (ANSWERABLE/PARTIAL/UNKNOWN/CONFLICT).
- 단위테스트 5건 작성 → **5/5 통과** (Gradle 외부 javac+JUnit 직접 실행, 아래 8번).
- 실제 데이터 검증: FIRST 82건 생성 / SECOND 0건 / REVIEW 82 / ACTIVE 0 / Top10 10-10 ANSWERABLE.

## 6. 미완료 작업

- Gradle 전체 컴파일 통과 (아래 10번 — 환경/상위 결함, 집에서 미해결).
- cherry-pick 파일의 DTO 결함 repair (다중 public 클래스, 중복 필드, import 누락).
- ADMIN preview UI 연결 확인 (`/admin/newstart/wiki`는 파일 존재, 동작 미확인).
- Top10 ADMIN preview 수준 검증 (로직은 있으나 DB 연동 미실시).
- production DB ingest 없음 (정상 — 금지 항목).

## 7. 생성/수정 파일

- 신규: `domain/newstartknowledge/service/NewstartOfficialReviewIngestService.java`
- 신규: `domain/newstartknowledge/service/NewstartTop10Answerability.java`
- 신규: `src/test/.../newstartknowledge/service/NewstartOfficialReviewIngestServiceTest.java`
- 신규: `docs/newstart/research-official/comparison/*` (14), `docs/newstart/research-official/ccrs/*` (15)
- cherry-pick: `domain/newstartknowledge/*` 21파일 + `db/migration/V983__*` + ADMIN UI 3파일
- 수정: `EmpiricalCaseService.java:126`, `KnowledgeService.java:204` (괄호 1개씩),
  `config/WebMvcConfig.java`, `templates/fragments/_admin_topnav.html` (f268c7c 내용 그대로)
- 본 HANDOFF 파일.

## 8. 테스트 결과

- `NewstartOfficialReviewIngestServiceTest` 5/5 통과:
  FIRST INGEST 전체 REVIEW·ACTIVE 0·downgrade 1 / SECOND 멱등성(0건) /
  CONTRADICTS 1건·중복없음 / UNKNOWN 보존 / answerability 4 verdicts.
- 실행법(Gradle 전체 빌드 불가 시): `cp.txt` 방식이 아닌 Gradle이 복구되면
  `./gradlew test --tests "...NewstartOfficialReviewIngestServiceTest"`.
- 실제 데이터 검증 (DB 없이 메모리 fake, JUnit 외부 실행):
  FIRST created=82 skipped=0 downgraded=0 relations=1 errors=[] /
  SECOND created=0 skipped=82 / REVIEW=82 ACTIVE=0 / Top10 10-10 ANSWERABLE(OFFICIAL_CURRENT).

## 9. 실패한 테스트

- 없음 (5/5 통과). Gradle `test` 태스크 자체는 10번 사유로 실행 불가.

## 10. 알려진 문제 (중요 — 학원에서 먼저 처리)

1. `gradlew compileJava` 100 errors. 대다수는 손대지 않은 파일
   (Academy/User/Conversation/JwtAuthenticationFilter 등 Lombok 미동작 증상).
   직접 javac+Lombok 1.18.46은 정상 동작하므로 **Gradle 빌드 환경 문제 또는 main 기존 파손** 의심.
   학원에서 pristine main worktree로 `compileJava` 재현 확인 필요.
2. f268c7c DTO 2파일이 javac 규칙 위반 (1파일 다중 public 클래스 —
   `NewstartKnowledgeDtos.java`, `NewstartEmpiricalCaseDtos.java`),
   `authority` 중복 필드, `NewstartDataIngestionService` lombok import 누락.
   학원에서 파일 분리 또는 static nested class로 repair 필요.
3. `-sourcepath` 암시적 컴파일 시 Lombok 미적용 증상 확인 (명시적 파일 나열 시 정상).
   학원에서는 Gradle 빌드로 검증할 것.

## 11. DB 변경 여부

- 코드: `V983__add_newstart_knowledge_wiki.sql` 추가 (미적용).
- 실행 DB 변경: 없음. production DB 변경 없음.

## 12. production 변경 여부

- 없음. retrieval(ACTIVE ONLY) 미수정. AI Router 미연결. deploy 없음.

## 13. #343 상태

- 미머지. `origin/docs/ccrs-busil-wiki-official-review`, PR #343 open.
- 본 브랜치는 해당 파일들을 checkout 참조만 함 (merge 없음).

## 14. #344 상태

- 미머지. `origin/docs/busil-path-comparison-review`, PR #344 open.
- 본 브랜치는 해당 파일들을 checkout 참조만 함 (merge 없음).

## 15. REVIEW/ACTIVE count

- 코드 적재 없음 (DB 미실행). 검증 러너 기준: REVIEW 82 / ACTIVE 0.
-Fixture 테스트: REVIEW 강제·downgrade 1건 확인.

## 16. UNKNOWN 목록

- 단말기 할부 / 임차보증금 세부 / 보험·해약환급금 / 배우자·공동소유 /
  PW 측 가상자산·비상장주식 / PW 월변제금 산식·감면계수 / PW→NSF 전환 근거 /
  NSF 신청기한. 전부 UNKNOWN 보존, 추론 금지.

## 17. CONFLICT 목록

- 10일 vs 31일 연체하한 (CONFLICT_REVIEW, CONTRADICTS 관계). 날짜 우선 자동승자 금지.

## 18. Top10 진행상태

- 로직 + 메모리 검증 완료 (10-10 ANSWERABLE). ADMIN preview·DB 연동은 미완료.

## 19. 다음 정확한 작업 (학원, 순서대로)

1. 10번 빌드 문제 재현·해결 (pristine main worktree에서 `compileJava`).
2. DTO repair 후 `./gradlew test --tests "...NewstartOfficialReviewIngestServiceTest"`.
3. ADMIN `/admin/newstart/wiki`에서 REVIEW 필터·evidence·UNKNOWN·CONFLICT 표시 확인.
4. REVIEW ingest 실행 (ADMIN 또는 fixture) 후 Top10 answerability DB 연동 검증.
5. 그래도 MERGE 금지, ACTIVE 전환 금지, DEPLOY 금지.

## 20. 학원 PC에서 재개 방법 (VIEW LIFE 사용자)

```powershell
git fetch origin
git switch -c feat/newstart-wiki-review-ingest-20260921-academy origin/feat/newstart-wiki-review-ingest-20260921
git pull --ff-only origin feat/newstart-wiki-review-ingest-20260921
git status
git log --oneline -3
git rev-parse HEAD
```

worktree 방식(권장, 기존 체크아웃과 충돌 방지):

```powershell
git worktree add "<학원PC 경로>\feel2real-wt-ns-wiki-0921" -b feat/newstart-wiki-review-ingest-20260921-academy origin/feat/newstart-wiki-review-ingest-20260921
```

주의: 집 PC 절대경로(`C:\Users\sxxra\...`)를 학원에서 그대로 쓰지 않는다.
EASY·시험지 worktree/branch를 건드리지 않는다.

---

## ACADEMY RESUME PROMPT

```text
repo: woori73/feel2real-buildit
branch: origin/feat/newstart-wiki-review-ingest-20260921 (학원에서 -academy suffix 작업브랜치 생성)
HEAD: 28191f9
HANDOFF: docs/newstart/handoff/NEWSTART_WIKI_REVIEW_INGEST_HANDOFF_2026-09-21_HOME_TO_ACADEMY.md

읽을 것:
1. 본 HANDOFF 전체 (§10 알려진 문제 필수)
2. domain/newstartknowledge/service/NewstartOfficialReviewIngestService.java
3. domain/newstartknowledge/service/NewstartTop10Answerability.java
4. docs/newstart/research-official/comparison/MANIFEST.md

완료: REVIEW ingest 서비스·answerability·단위테스트 5/5(외부 실행)·실데이터 검증(82건 REVIEW, Top10 10-10).
계속: ① pristine main에서 compileJava 100 errors 재현·원인 확정 ② DTO repair ③ Gradle 테스트 통과
④ ADMIN preview 확인 ⑤ Top10 DB 연동 검증.
금지: #343/#344 merge, REVIEW→ACTIVE 전환, production DB ingest, AI Router 연결,
EASY·시험지 변경, MERGE, DEPLOY.
```
