# 최종 재검수 및 병합 승인 판정 — #854 · #853 · #855

기준 main `930716ec` / 검수 HEAD: #854 `0d8afd2f`, #853 `55ea86d2`, #855 `63c7dbe1`.
검수자: Claude(독립 검수). **실제 병합·Draft 해제·배포·운영 DB 변경·운영 MCP 활성화·유료 외부 AI 호출은 하지 않았다.**

## 1. 판정

| PR | 판정 | 권고 |
|---|---|---|
| #854 | PASS | 일반 병합(merge commit) 1순위 |
| #853 | PASS | #854 병합 후 2순위 |
| #855 | PASS (병합) | #853 병합 후 3순위. **MCP 운영 활성화는 §5 게이트 통과 전 금지** |

P0/P1 병합 차단 결함 없음. GitHub CI는 세 HEAD 모두 필수 체크 통과.

## 2. P1 승인 우회 (`EasyImportService.confirm`)

- 원문 우회 테스트 2건(`handoff/probes/ClaudeOriginalBypassMySqlTest.java.txt`): 이전 2/2 실패 → **2/2 통과**.
- 정상 승인 흐름 PASS, 검토 해시 변조 거부 PASS, 타 사업체 접근 거부 PASS(`IndependentOrdinaryApprovalMySqlTest`, Codex 회귀 13건).
- 일반 CSV(sourceKind `CSV`)는 가드 대상이 아니며 정상 동작.
- main에는 가드 0건, #854/#853/#855에는 있음. 이 PR 스택이 처음 닫는 약점이며 main 동적 재현은 하지 않았다.

## 3. P2 고아 파일 (`handoff/probes/IndependentStudioProbeMySqlTest.java.txt`)

- 30번째 컷 DB 오류 주입: DB 롤백, 저장소 고아 0, 기존 SHA·승인 포인터·revision 불변.
- 저장소 삭제 실패: 새 객체만 최대 30개 잔류(기존 무손상), 원래 오류 노출, 로그에 fileId.
- 잔여 위험(P2): JVM 강제 종료·저장소 장애 시 미연결 객체 잔류. 정리 스윕 없음 → dry-run 점검 스크립트 권고.

## 4. 병합 안전성

- `merge --no-ff` #854→#853→#855 충돌 0, 최종 트리 = #855 HEAD. 스쿼시는 스택이 깨지므로 금지.
- Flyway: main(V1093) → V1094 → V1095 순서 적용, 체크섬 V1094 `-1031052192`, V1095 `-1470147673`. 역순 배포는 검증 실패. `outOfOrder` 사용 금지.
- 롤백: 이전 앱 jar는 신규 스키마(추가 테이블만)에서 경고만 내고 기동.

## 5. MCP 운영 활성화 전 게이트 (병합 차단 아님)

1. `app.admissions.rate-limit.trusted-proxies`를 ALB 대역으로 설정(비어 있으면 익명 요청이 프록시 하나로 집계, 기동 경고 없음 — P3).
2. 격리 HTTPS 실접속 시험(`ISOLATED_HTTPS_CONNECTION_RUNBOOK.md`).
3. 사용자 승인. `app.mcp.enabled` 기본 OFF.

## 6. NOT TESTED / 잔여

- NOT TESTED: 실제 ChatGPT·Claude 연결, 실제 EP01 원본 등록, 클라이언트의 이미지 판독, AcademyCraft 실데이터 격리, S3 비용, 실제 Muse 응답, #854 Playwright E2E, 운영 RDS 정확한 버전.
- P3: `claude.com` 콜백 미지원, 긴 숫자열 과마스킹 가능성, 경합 시 속도 제한 과거부 가능.
