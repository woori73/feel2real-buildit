# 제작 협업실 로컬 인수 보고서 · 2026-10-22

GitHub authoritative main `ae5d1760b9ba76ca79ef5c6f906acc59e6c293b1` 기준. Claude 감사 `ccr-d295072e-waeyz7`의 `bd3097682a04804f848eedeea977feb619a55024:docs/reviews/STUDIO_AUDIT_AND_HERMES_PLAN.md`를 반영했다. Codex가 구현·개발 검증했고 **Claude 독립 재검수는 대기**다. 아래 구현은 격리 MySQL/로컬 제작실에 실제 적재·검증했으며 운영에 등록하거나 배포하지 않았다.

## 제출한 변경

| 범위 | Draft PR | HEAD |
|---|---|---|
| 1. 사람 중심 콘티/이미지/문서 화면 | [#864](https://github.com/woori73/feel2real-buildit/pull/864) | `41b7ee73ab3eb9c6e38ddd152842d538aa5cd502` |
| 실제 GitHub536개 등록 임포터 | [#865](https://github.com/woori73/feel2real-buildit/pull/865) | `55da24507e116be1826151031871e4181103f47d` |
| 2. 읽기 MCP 문서·실제 image 블록 | [#866](https://github.com/woori73/feel2real-buildit/pull/866) | `518d48a8212ae4dec925f431b6e2a65808c2dc98` |
| 4. 기억/댓글/수정 요청/대기함 | [#867](https://github.com/woori73/feel2real-buildit/pull/867) | `94a36126ff2cc9a5f97d9e47b5494f1c1326e495` |
| 5. 프로젝트/보관/초대/알림 | [#868](https://github.com/woori73/feel2real-buildit/pull/868) | `f8dbd097e5b320e58f3d972b6e6fbc425c8510da` |
| 3. 별도 동의형 후보 쓰기 **설계** | [#869](https://github.com/woori73/feel2real-buildit/pull/869) | `cac911da6e4d52dc8b6809a8cc60430cf80f1fbb` |
| 6. Hermes 인계 **준비**·최종 증거 | `docs/hermes-collaboration-handoff` | 본 보고서의 GitHub PR HEAD 참조 |

각 PR은 직전 브랜치 위에 쌓았다. main 병합/스쿼시/운영 활성화 없음. 모든 단계의 diff를 별도로 검수할 수 있다. 해당 HEAD의 실제 CI는 PR Checks가 기준이며, 변경 경로에 따라 Financial/Studio 워크플로가 실행되지 않는 문서 PR도 있다. V1094/V1095 내용·체크섬 불변, 신규 마이그레이션 없음. MySQL Flyway 전체/업그레이드는 Studio CI에서 검증한다.

## 실제 적재와 보존

원본은 #863의 Git `09d62dee982e2e89e3653c90b1ee42f05bff4f9d`, `docs/ai-agent-production/`에서 직접 확보했다. 536경로(작품자료532+인계문서4), 164,559,341 bytes, 316고유 SHA, 중복 경로220개를 보존했다. [원본 카탈로그](../../src/main/resources/studio/production-09d62dee.json), [등록/실제 byte 재조회 영수증](import-local-receipt.json), [dry-run](import-dry-run.json).

출처별 revision536, r14/r5 컷136, 기존 승인 캐릭터5, 초기 기억1을 최초 append-only 등록했다(678revision/610item/316file). 등록 재시도는 추가 revision0이다. 실제30번째 컷 실패를 두 번 주입해 DB 전체 롤백과 성공 재시도를 검증했다. 업로드/DB가 완전한 분산 트랜잭션은 아니며, 업로드 후 등록 실패는 정상 staged file을 유지하고 재사용한다. process crash/저장소 정리 실패까지 원자성을 보장하지 않는다.

68장 러프·설명·대사·연출, 실제 r14 HTML, PDF 페이지, ZIP 내부 PNG, 캐릭터 승인5종을 사람이 다운로드 없이 보았다. 대표10컷 이미지는 생성하지 않았다. **원작 대조0/68·PASS 확정0·컷 승인0 유지.** 승인 시트5 SHA, 이전 revision/승인 포인터/작업 기억은 보관·재개 후에도 불변이다. 제목 수정/보관/재개와 초대는 감사 이벤트를 남기며 프로젝트 영구 삭제 기능은 없다. 다른 프로젝트에 자료를 복사하거나 임의 작품 자료를 생성하지 않았다.

## 검증 증거

- [실제 자료 브라우저16개](actual-browser-results.json): 최종 프로젝트 확장 코드에서도 재실행 PASS. 68장 실제 디코딩·316전체 파일·문서/PDF/ZIP·PC1440/모바일390·무권한403·작품 쓰기0·JS/외부 요청0.
- [임포터 Java20건](import-java-results.json): 실제 자료 MySQL5건 포함. [공동 기억 Java36건](memory-java-results.json), [표/안전 렌더9건](memory-renderer-results.json), [MCP 미디어 Java32건](media-java-results.json). 서로 중복되는 회귀를 합산해 총건수로 보고하지 않는다.
- [최종 프로젝트 MySQL44건](projects-java-results.json): 프로젝트8+공동 기억6+Studio8+MCP22 PASS. 기존 정상 등록·복원·승인·OAuth/읽기 권한 회귀 유지.
- [기억 브라우저11개](memory-browser-results.json): 새 사용자/세션 복원, CAS409, 근거 자료를 연결한 사람 작성 AI 공유 댓글, 수정 요청/기존 승인 보존, PC/모바일 대기함.
- [프로젝트 브라우저9개](projects-browser-results.json): 실제 작품과 빈 QA2개 동시 관리, 검색 초대/수락, 보관/재개/제목, 역할·프로젝트·파일 격리, 알림 GET쓰기0/본인 읽음.
- 공식 SDK: [문서/image 블록](media-protocol-results.json), [독립 세션 기억 복원](memory-protocol-results.json), [최종 프로젝트 격리](projects-protocol-results.json). 실제 PNG에서 JPEG≤100KiB 파생·원본/파생 SHA 대조, 원본 별도 동의, 외부 AI 호출0. **외부 제품의 그림 판독 시험이 아니다.**
- [GET 전체 감사](../ai-agent-studio/GET_SAFETY_AUDIT.md):641 GET/414허용, DB DML 표지0, 기존 S3읽기49/ZIP 임시파일1 비용·한계 명시. 금융은 허용 사업체 월 요약만, 확정·수집 원문 미제공.

화면: [PC 콘티](screens/actual-board-1440.png) · [모바일 콘티](screens/actual-board-390.png) · [승인 캐릭터](screens/actual-characters-390.png) · [PC 프로젝트/보관 구분](screens/projects-1440.png) · [모바일 프로젝트](screens/projects-390.png) · [모바일 알림](screens/notifications-390.png) · [공동 기억](screens/memory-390.png). 프로젝트/알림 화면은 최종 코드로 촬영했고, 실제 자료 화면도 최종 코드에서 다시 확인했다. 구조 접근성 검사에서 색 대비 전체 AA 검증은 제외했다.

재현: 기존 [README](README.md)의 격리 fixture 준비·실제 Git 카탈로그 검사/임포트·`run-production-local.py`·`production-assets-qa.cjs`·`projects-browser-qa.cjs`·`projects-protocol-qa.py`. 기존 로컬 실제 적재 DB와 파일 저장소는 유지하며 개인 fixture/cookie는 `/tmp`에만 두고 Git에 넣지 않는다. 127.0.0.1:18090만 열고 운영 URL/DB를 사용하지 않는다.

## 남은 위험·Claude 재검수 요청

1. 격리 HTTPS/계정 미준비로 ChatGPT·Claude 실제 인증·문서/동일 그림 직접 판독 **NOT TESTED**. image 블록 전달·base64 해시 확인만으로 시각 판독 성공을 선언하지 않는다.
2. AI 직접 쓰기/후보 제출은 [별도 동의 설계](STUDIO_PROPOSE_DESIGN.md)만 제출했다. 사람이 공유한 AI 자료의 실제 기록자는 사람이며 모델명이 AI 인증을 증명하지 않는다. 직접 인증 쓰기 구현/실측 전까지 중간 파일 전달 없는 AI 후보 자동 인계 완료로 판정하지 않는다.
3. [Hermes 준비](HERMES_HANDOFF_PREPARATION.md)는 설치/자동 실행 없음. 공식 문서상 Max OAuth는 구매한 추가 크레딧을 소비하므로 사용자가 승인하지 않은 유료 호출을 하지 않는다.
4. Claude는 단계별 실제536 매핑/5SHA/원본 불변, 문서 sandbox·ZIP 경계·PDF 제한, 프로젝트 선택/동시 보관/권한 폐기 current read, pending 초대 접근 차단, GET 알림 무변경, CAS/전 버전 댓글, MCP original 동의/역할/프로젝트/POST 거부를 독립 재현해 주세요.
5. Flyway1094/1095 업그레이드, 기존 금융 수집 승인 우회 차단·30번째 컷 업로드 롤백은 기존/CI 회귀를 유지한다. 운영 MCP/DB/배포/main 병합은 별도 승인 전 실행하지 않는다. Claude 재검수와 실제 제품 연결이 남아 **전체 공동 작업 완료·병합 승인 요청을 하지 않는다.**
