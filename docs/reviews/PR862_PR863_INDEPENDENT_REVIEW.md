# PR #862 · #863 독립 검수 — 실제 536개 자료 적재

검수 HEAD: #862 `a93792d0`, #863 `09d62dee`, main `ae5d1760`. 검수자: Claude(독립). 병합·배포·운영 DB 변경·MCP 활성화·유료 모델 호출 없음. 모든 실행은 로컬 격리 MySQL 8.0.46(3318/3319)·로컬 저장소.

## 판정

| PR | 판정 |
|---|---|
| #863 (자료 보존, docs only) | **PASS** — 병합 가능(일반 병합 권장) |
| #862 (협업실 + 536개 적재 도구) | **PASS (병합)** — P0/P1 없음. 운영 공개는 §5 조건 필요 |

## 1. 검수 항목 (요청 7항목)

| # | 항목 | 판정 | 근거(독립 실행) |
|---|---|---|---|
| 1 | 고정 소스·MANIFEST·536개·SHA | PASS | #863 git archive에서 532개 직접 SHA-256·크기 대조 → 불일치 0, MANIFEST 532행 = 파일 532, 고유 SHA 312(+인계 4 = 316), 중복 220. 승인 5종 SHA 존재(4·2·3·3·2 = 14개 파일), 러프 PNG 68, .md5 12, 최대 파일 14.5MB(<32MiB 상한) |
| 2 | dry-run → 적재 → 재실행 → SQL 실패 롤백 | PASS | `CollaborationProductionMySqlTest` 4/4 통과(브라우저 1건은 미활성), DB 직접 조회: workspace당 536행, 고유 SHA 316, 메타 12, 패널 68, 승인참조 14행, 컷 레코드 68, pass_confirmed 0, comparison NOT_COMPARED |
| 3 | 출처 승인 vs DRAFT, 0/68·PASS 0 | PASS | 신규 자료 전부 DRAFT(승인 상태는 별도 검증 프로젝트의 의도적 시험 3건만), `studio_items` 포인터 전후 동일(내 프로브), 원본 JSON의 과거 `qa_status: PASS`는 별도 필드로 NOT_COMPARED 처리 |
| 4 | 사람용 화면(PC1440/모바일390) | PASS(캡처 검토) | 저장소 캡처를 직접 열람: 컷 이미지+원문 설명/연출/대사, 이전/다음 컷, 구간 선택, 컷 확대·비교, 안전 배너 확인. 브라우저 12항목 자체는 제 환경에서 **재실행하지 않음**(Playwright 미설치) — CI 4/4 성공으로 갈음 |
| 5 | 임포터 격리 | PASS | `@Profile("local")` + `enabled=false` 기본 + `LocalFileStorage` + loopback DB URL 정규식(`f2r_cluster_test`/`f2r_collaboration_import_test`만) 4중 가드. 코드 전체 검색에서 컨트롤러·MCP·스케줄러 호출 0. 내 프로브: enabled=false에서 dryRun/apply 모두 403 |
| 6 | 병합 순서·충돌·마이그레이션 | PASS | main 기준 #863→#862, #862→#863 두 순서 모두 `merge --no-ff` 충돌 0, 결과 `docs/ai-agent-production` 536개. V1094/V1095 파일 byte 동일, Flyway V1093→1094→1095→1098 업그레이드 + fresh 스키마 통과, 체크섬 V1094 `-1031052192`, V1095 `-1470147673` 불변, V1096~98 추가 |
| 7 | 외부 OAuth/MCP·이미지 판독 | **NOT TESTED** | 유지 |

## 2. 추가 독립 프로브 (`handoff/probes/ClaudeIndependentCollabProbeMySqlTest.java.txt`, 실제 536개 적재 위에서 실행, PASS)

- 스튜디오 권한 없는 사용자: 목록·production·원본·메타 모두 403
- 스튜디오 VIEWER지만 프로젝트 비참여자: 목록·원본·PDF 403
- 프로젝트 VIEWER: 100건 조회·원본 SHA 일치, 승인/기억 추가/파일 등록/멤버 변경 403
- 다른 프로젝트의 EDITOR: 이 프로젝트 파일·목록 403
- 적대적 HTML(script, on*, javascript:(대소문자·공백), iframe srcdoc, svg, form, meta refresh, link, base, srcset, svg data URL) 미리보기에서 모두 제거. 허용된 PNG data URL만 유지
- 적재 후 536개 저장 원본 SHA 전수 재대조 일치

## 3. 발견 사항 (병합 차단 아님)

- **P2 — 운영 적재 경로 없음:** 임포터가 local+테스트 DB 전용이라 **병합·배포 후 운영 협업실은 빈 상태**다. 운영에 자료를 넣는 승인된 절차(별도 관리 작업, 백업·SHA 대조·롤백)가 아직 없다. 병합 = 운영 공개가 아니다.
- **P3 — 한글 파일명과 JVM 로케일:** POSIX 로케일에서는 파일명을 읽지 못해 "누락 또는 추가 파일" 400으로 **fail-closed**(조용한 오적재 없음). UTF-8 로케일(`LC_ALL=C.UTF-8`) 필요. 문서화 권장.
- **P3 — ADMIN 전권:** `ADMIN` 역할은 모든 프로젝트의 MANAGER로 간주(설계). 관리자 계정 = 536개 열람·원본 접근 가능.
- **P3 — `<style>` 유지:** HTML 정화가 `<style>`을 제거하지 않음(`@import` 포함). 현재 CSP(`default-src 'none'; style-src 'unsafe-inline'`)와 `sandbox`로 외부 로드가 막혀 실해 없음. 방어 심층화로 제거 권장.
- **P3 — UX:** 빈 대사가 `대사: []`로 JSON 그대로 표시됨.
- **P3 — 저장소 용량:** #863 병합 시 git 이력에 약 164MB 영구 추가. 공개 저장소 공개는 사용자 승인 사항으로 PR에 기재됨.
- **CI 의존:** #862 CI는 `git fetch origin 09d62dee…`로 #863 커밋을 가져온다. 스쿼시 병합하면 이 커밋이 main 이력에서 사라진다 → **#863은 일반 병합**.

## 4. 권고 병합 순서

1. #863 일반 병합(커밋 `09d62dee` 보존) → 2. #862 일반 병합 → 3. 운영 배포는 별도 승인.
병합 직전 두 PR의 CI 재확인. 스쿼시 금지.

## 5. 운영 공개 전 조건(미충족)

- 운영 배포 승인 + Flyway V1096~98 적용 + RDS 스냅샷
- 운영 자료 적재 절차 설계·승인(위 P2)
- MCP는 OFF 유지, `trusted-proxies` 설정 확인 후 격리 HTTPS 연결 시험(NOT TESTED)
- 실제 로그인 계정으로 콘티·이미지·문서 열람 확인

## 6. 미검증

실제 ChatGPT/Claude OAuth·MCP 접속·이미지 판독, 브라우저 12항목 독립 재실행, 운영 환경 열람, 운영 RDS 버전.
