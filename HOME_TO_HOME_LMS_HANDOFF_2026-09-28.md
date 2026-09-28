# EDUHOLIC LMS HOME HANDOFF — 2026-09-28

작성 시각: 2026-09-28 21:40 (학원 PC) / 목적: 404 완수가 아니라 **현재 지점 보존 후 집에서 연속 작업**.
성공 조건: 집에서 재조사 0, 이 문서의 NEXT TASK부터 바로 시작.

## 1. Repository

Repository: `woori73/feel2real-buildit` (origin = https://github.com/woori73/feel2real-buildit.git)

Academy worktree: `C:\Users\VIEW LIFE\feel2real-lms-404`  ← **새 clone/새 worktree 만들지 말 것**

Branch: `fix/lms-local-video-delivery-404`
HEAD: `d153096e8af19e9630579e6f2ebab329ef649860`
origin/main: `09ee3b3e7ac9809f30e96280d03053c55fa4a1d7`

HEAD가 origin/main보다 앞선 로컬 커밋 2개 (작업 시작 시 **미push** 상태였음 → 본 HANDOFF 세션에서 push함, §8 참고):
- `d6e58e54` fix(lms): fix local video 404 — unify storage base path with AcademyVideoDeliveryStorage (LocalFileStorage.java +53/-25, application-local.yaml)
- `d153096e` test(lms): local video 404 round-trip regression + relative-key assertions (LmsLocalVideoDeliveryRoundTripTest.java 108줄 신규, LocalFileStorageTest.java)

## 2. Completed baseline

PR #428 **MERGED**
merge SHA: `6b535d1d835a8f98a7d68a64eb8359c223e09277`
→ `git merge-base --is-ancestor 6b535d1d... HEAD` = **YES** (현재 브랜치는 PR #428를 이미 포함)
production deploy completed. BUILD CHECK PASS.

이미 해결된 것 (집에서 재조사 금지):
- LmsContentAdminService의 `toAbsolutePath()` 제거
- storage key contract 보존
- V1013
- production S3 contract

DO NOT REDO: PR #428 / V1013 / LmsContentAdminService redesign / storage_key absolute path experiment

## 3. Today's academy work (학원에서 실제로 한 것 — 관측 사실만)

**작업 시작 시 디스크 상태:** 위 커밋 2개는 이미 존재했고, 미커밋 변경 2개 파일이 더티로 남아 있었음.
untracked 파일 **0개** (`git status --short` = `M` 2줄뿐). 즉 테스트·문서 등 신규 산출물은 이미 §1의 커밋에 포함.

FILES MODIFIED (uncommitted, 작업 시작 시점부터 존재):
- `src/main/java/f2r/tutor_solution/private_tutor_solution_back/infrastructure/storage/LocalFileStorage.java`
- `src/main/resources/config/application-local.yaml`

FILES CREATED: 없음 (untracked 0)
TESTS CREATED: 없음 — `LmsLocalVideoDeliveryRoundTripTest.java`는 **커밋 `d153096e`에 이미 존재**
  경로: `src/test/java/f2r/tutor_solution/private_tutor_solution_back/infrastructure/storage/LmsLocalVideoDeliveryRoundTripTest.java`

본 세션(21:30경)에서 한 유일한 파일 변경 = **인코딩 복구뿐, 코드 내용 수정 0**:
두 파일을 UTF-16LE(BOM+CRLF) → **UTF-8(no BOM)+LF** 로 재인코딩. 바이트 크기 13,548→6,792 / 5,342→3,173.
내용(코드)은 그대로이며, 복구 전 원본은 byte-exact 백업해 두었음 (§4).

### 미커밋 diff가 실제로 하던 일 (중요 — 방향이 회귀)
`git diff` 판독 결과, 이 2개 파일의 미커밋 변경은 **커밋된 404 픽스를 되돌리는 방향**이었음:
- `LocalFileStorage.java`: `return toRelative(dest);` → `return dest.toString();` (절대경로 반환 회귀),
  `Path path = resolveStorageKey(storageKey);` 제거 → `Path.of(storageKey)` 직접 사용 (readAllPages/readPage/readSingleLimited/pageCount),
  pageCount의 try/catch 구조 축약, 한글 주석·메시지 문장 다수 삭제/변경
- `application-local.yaml`: `upload-dir: ${app.video.local-upload-dir}/objects` → `upload-dir: ./raw_datas`
  (공유 베이스 주석 삭제 포함)

→ §7의 DO-NOT-DO(“toAbsolutePath reintroduction”)와 정면 충돌하는 상태.
→ 다만 이 내용은 그 어떤 히스토리 blob과도 동일하지 않음(§4 검증), 따라서 **임의 삭제 금지**이고 patch+백업으로 보존했음.
   이전 세션이 왜 이 상태였는지는 **원인 미확인** (의도된 실험인지, 편집기가 구버전 버퍼를 UTF-16으로 써버린 것인지 판정 불가).

## 4. Stall incident + corruption 조사 (확정)

이전 OpenCode 세션 hang/종료. 마지막 compiler 오류:
```
LocalFileStorage.java:26 illegal character: '\u0000'
LocalFileStorage.java:27 illegal character: '\u0000'
```

관측된 실체 (측정값):
- NUL BYTE COUNT: **6,669** (복구 전) — NUL 위치 = **모든 홀수 바이트 오프셋** (3,5,7,9,…,13547)
- BOM: `FF FE` → 파일 전체가 **UTF-16LE + BOM**, 줄바꿈 **CRLF**
- `decode('utf-16')` 성공, 6,773자 / `decode('utf-8')` 실패(`0xff` invalid start)
  → 소스 내용이 유실된 게 아니라 **인코딩만 틀어져 javac가 본문을 못 읽은 것**. Gradle이 1시간 동안 테스트한 것이 아니라 이 compile 실패가 반복된 것이 맞음.
- FILE SIZE(복구 전/후): LocalFileStorage.java 13,548 → 6,792 / application-local.yaml 5,342 → 3,173
- 비교 대상 blob 크기: HEAD 7,687 / origin(main) 6,746 (LocalFileStorage.java, 모두 UTF-8 no-BOM, LF, NUL 0)

**2차 결함 — 한글 mojibake (복구 불가 부분 존재):**
디코딩한 한글이 `濡쒖뺄 ?붿뒪??湲곕컲` 식으로 깨져 있었음 (UTF-8 원문 → CP949로 읽힘 → UTF-16LE 재저장 계열의 이중 변환).
역변환 시도 결과: `t.encode('cp949')` → `UnicodeEncodeError: '\x80'` (cp949/euc_kr/cp1252 모두 strict 실패),
그리고 이미 `?` 로 치환된 문자열 다수 → **한글 리터럴·주석은 부분적으로 영구 유실**.
- ASCII(코드) 골격은 온전함.
- 다른 히스토리 버전과의 ASCII 스켈레톤 비교: working tree ≠ origin/main, ≠ HEAD, ≠ HEAD~1, ≠ 97a7cc3d (**모두 False**) → 순수 구버전 되돌려놓기가 아니라 고유 내용이 섞여 있음.
- **한글 문구를 복원할 때 깨진 텍스트를 되살리려 하지 말 것**: `git show HEAD:<file>` / `git show d153096e:<file>`의 정상 UTF-8 한글을 가져오는 것이 정답.

복구 여부: **NUL/인코딩 복구 완료 (NUL BYTE COUNT = 0, BOM 없음, CRLF 0).** mojibake는 복구 대상에서 제외(원전 HEAD 사용).
백업(byte-exact UTF-16 원본): `C:\Users\VIEW LIFE\Documents\Qoder\2026-09-28\f9b4df7d\lms_preserve\utf16_backup\`
- `LocalFileStorage.java.utf16.bak`, `application-local.yaml.utf16.bak`
patch: `...\lms_preserve\lms-404-dirty-changes.patch` (13,922 B, 재인코딩 후 상태)

## 5. Current local 404 status

핵심: **404는 미해결이며, 검증도 한 번도 되지 못했다.** (NUL 때문에 테스트 컴파일이 한 번도 통과하지 못했음)

- UPLOAD: UNKNOWN (실제 업로드 왕복 미검증)
- STORAGE_KEY: 커밋된 픽스(`d6e58e54`)의 의도는 **basePath 기준 상대 논리 키**(`toRelative`). 미커밋 변경은 이를 절대경로로 되돌림 → 어느 쪽이 404의 답인지 **검증으로 결정되지 않음**.
- ACTUAL FILE: UNKNOWN (업로드 실제 경로 vs resolve 결과 대조 미수행)
- PLAYER REQUEST: UNKNOWN (HTTP 200/206 확인 0건)
- DELIVERY RESOLUTION: UNKNOWN (`LocalAcademyVideoDeliveryStorage.resolvePath(asyncRoot/objects/{key})`와 LocalFileStorage 키 계약의 실제 일치 여부 미확인)
- ROOT CAUSE CURRENTLY KNOWN: **확정된 런타임 원인 없음.** 이번 세션에서 확정한 것은 런타임이 아니라 **빌드 블로커**(UTF-16/NUL 인코딩)이며 그것은 제거됨.
- 목표 flow (집에서 이 순서로 검증): 강사 video upload → relative logical storage_key → configured local storage root → actual local file → student/player request → delivery resolver → HTTP 200/206 → playback → Range → seek

## 6. Test status

compileJava: **NOT RUN** (출발 임박, §13 타임 게이트 우선 — NUL 제거 후에는 처음 시도하는 것)
LmsLocalVideoDeliveryRoundTripTest: **NOT RUN** (위와 동일)
전체 test: **NOT REQUIRED / NOT RUN**
CI: 대기하지 않음 (push만 확인)

## 7. LMS title

EXPECTED: `압구정에듀홀릭학원`

CURRENT (정적 스캔 결과):
- `압구정에듀홀릭학원` 문자열은 `src/main` 전체에서 **8곳 / 5개 파일**에만 존재:
  - `templates/tutor/academy_builder.html` 3곳 — **font 프리뷰 샘플** (`<small>` 브랜드 폰트 시연 문자열)일 뿐 페이지 타이틀 아님
  - `static/js/academy_builder.js` 1곳
  - `db/migration/V928__add_edu_holic_demo_academy.sql`, `V953__import_legacy_v3_selected_educators.sql`(2), `V973__add_easy_ledger.sql` — DB 시드
- 템플릿 `<title>` 은 대부분 `F2R - 관리자 대시보드`, `관리자 - …`, `수업 관리` 등 **학원명 미사용** (academy_builder.html에는 `<title>` 자체가 없음), `<meta charset="UTF-8"/>` 는 정상
- U+FFFD(치환 문자) 정적 스캔: 본 세션 grep에서는 검출 0

BROKEN CHARACTER: **NOT VERIFIED** (소스에 깨진 문자 없음으로 보이나, 오전에 목격한 “이상한 문자”의 실제 화면 재현을 못 봄)
TITLE FIX PRESENT: **NOT VERIFIED** (학원명을 타이틀/브랜드로 쓰는 커밋을 이 worktree에서 확인하지 못함 — `압구정에듀홀릭학원`은 builder 프리뷰/시드에만 존재)
VERIFIED: **NO — 브라우저 실물 QA 미수행.** 수정하지 않고 NEXT TASK로 이월.
※ 참고: Git Bash(cp949) 셸에서 한글 패턴 grep은 오탐(0건) 남. 한글 문자열 검색은 반드시 Grep/rg 도구 또는 Python으로 할 것.

## 8. Git status

시작 시점 `git status --short`:
```
 M src/main/java/f2r/tutor_solution/private_tutor_solution_back/infrastructure/storage/LocalFileStorage.java
 M src/main/resources/config/application-local.yaml
```
시작 시점 `git diff --stat` (당시 두 파일이 NUL을 포함해 git이 **binary**로 취급):
```
 .../infrastructure/storage/LocalFileStorage.java   | Bin 7687 -> 13548 bytes
 src/main/resources/config/application-local.yaml   | Bin 3257 ->  5342 bytes
 2 files changed, 0 insertions(+), 0 deletions(-)
```
재인코딩 후 `git diff --stat` (text로 복원됨):
```
 .../infrastructure/storage/LocalFileStorage.java   | 95 ++++++++--------------
 src/main/resources/config/application-local.yaml   | 38 ++++-----
 2 files changed, 52 insertions(+), 81 deletions(-)
```
UNCOMMITTED WORK: **YES** — 위 2개 파일. §10 규칙에 따라 **커밋하지 않고** patch+백업+ZIP으로 보존한 채 worktree에 그대로 남겨둠.
(이 파일의 커밋은 §11 NEXT TASK 1번에서 집에서 의도를 판단한 뒤 수행)

## 9. NEXT TASK AT HOME (아주 구체적으로)

0. 이 브랜치를 fetch (이미 push됨, §8/§12): `git fetch origin && git switch fix/lms-local-video-delivery-404`
   - 로컬에 worktree가 이미 있으면 `git status`로 §1 HEAD와 동일인지 확인. 다르면 **멈추고 비교**.
1. (집 PC에서 이 worktree를 이어 쓸 경우) 미커밋 2개 파일을 patch로 복원:
   `git apply lms-404-dirty-changes.patch` — 단, **그 전에 2번을 먼저 판단**할 것.
2. **판단 시점:** 미커밋 diff는 §3·§7에 기록한 대로 404 픽스를 절대경로로 되돌리는 방향 + mojibake.
   - 의도된 실험이 아니었다면 → 그 diff는 폐기(단, 백업·patch는 유지하고 `discard`사실을 커밋 메시지에 기록).
   - 의도된 실험이었다면 → 한글만 `git show HEAD:<file>`의 정상 UTF-8 문구로 되돌린 뒤 커밋.
   - **어느 쪽이든 NUL/UTF-16 상태로 되돌리지 말 것 (UTF-8 + LF).**
3. 인코딩 확인: `python -c "b=open(p,'rb').read(); print(b.count(0), b[:3])"` → `0`, BOM 아님
4. `./gradlew compileJava` 만 실행 (전체 test 금지). 10분 스톨 시 중단.
5. `./gradlew test --tests '*LmsLocalVideoDeliveryRoundTripTest'` (단일 테스트)
   - 이 테스트가 **현재 HEAD의 상대키 계약**을 통과하는지가 404의 첫 관문.
6. local upload → storage_key → actual file 3점 대조: 업로드 후 반환된 storage_key가 상대 논리 키인지,
   그 키가 설정된 local storage root(`app.file.upload-dir` = `${app.video.local-upload-dir}/objects`)에서 실존 파일로 resolve되는지 기록.
7. student/player 요청 HTTP **200** 확인 (재생 시작)
8. **Range** 요청 → **206** + Content-Range 확인
9. seek(중간 offset) 동작 확인
10. 압구정에듀홀릭학원 title 확인: HTML title / LMS header·brand / 학생 화면 / 강사·관리 화면 / player 화면 5종 실물 QA (§7이 NOT VERIFIED로 끝난 항목)
11. scoped tests (회귀 범위만) → 12. commit/push/PR → 13. CI

## 10. DO NOT DO

- `git reset --hard` / `git clean` / modified 파일에 `git checkout`·`git restore` (기존 작업 폐기)
- 새 clone·새 worktree 제작 / branch 삭제
- 기존 미커밋 변경 overwrite (백업: §4 경로에 byte-exact 보존)
- initial implementation from scratch (§5의 런타임 재조사 금지 — 검증되지 않았다는 사실만 기록됨)
- V1013 수정 / LmsContentAdminService redesign / `toAbsolutePath()` 재도입 / production S3 contract 변경
- production DB write / merge / deploy (무승인)
- 전체 Gradle test suite 실행, CI 결과 대기
- 다른 Agent/EASY/NewStart 프로세스 건드리기 (`taskkill /IM java.exe|node.exe` 금지, 필요 시 자기 PID만)
- 깨진 한글 mojibake를 복원하려 재시도 (불가 — HEAD의 정상 UTF-8 문구를 쓸 것)
- API key/secret 값 출력·저장·기록

## 11. Preservation summary (이번 세션 결과)

- GitHub: `fix/lms-local-video-delivery-404` push → §1의 커밋 2개(404 픽스 + 108줄 회귀 테스트) 보존
- Transfer ZIP: `HOME_TO_HOME_LMS_2026-09-28.zip` (HANDOFF + dirty patch + UTF-16 원본 백업), secrets/build/.gradle/node_modules 제외
- production 접촉: 0 (merge no, deploy no, DB write no)
- 404 상태: **UNRESOLVED / 검증 0건** — 이것이 정확한 현재 지점.
