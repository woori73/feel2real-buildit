# 제작 협업실 — 단계별 구현·검증

기준 main `ae5d1760` (#854/#853/#855 병합). Claude [감사 원문](https://github.com/woori73/feel2real-buildit/blob/bd309768/docs/reviews/STUDIO_AUDIT_AND_HERMES_PLAN.md)을 반영한다. 운영 배포·운영 DB·운영 MCP·유료 모델 호출을 하지 않는다. 독립 Claude 재검수는 별도이며, Codex의 테스트로 대신하지 않는다.

## 1단계 — 사람 중심 콘티·문서 화면

기존 파일·SHA·버전·권한·승인·복원 API를 사용한다. 68컷 전체 head 조회, 이미지·설명·대사·연출 보드, 이전/다음·방향키, 확대, 승인본/수정 후보 이미지 비교, 최신 캐릭터 갤러리를 제공한다. 미등록 자료는 업로드 대기다.

HTML은 Jsoup 태그 허용 목록, Markdown은 CommonMark HTML 이스케이프 후 같은 정제를 거쳐 sandbox iframe으로 렌더한다. 스크립트·폼·이미지·외부 리소스·링크는 제거하고 원본은 보관한다. PDF는 브라우저별 플러그인 차이를 피하도록 PDFBox로 페이지를 렌더하고 브라우저에서 이전/다음으로 열람한다(처음 100쪽). PDF의 스크립트·첨부 파일·링크를 실행하지 않는다. 원본 PDF 다운로드는 유지한다.

ZIP 중앙 디렉터리 목록에서 지원 항목만 선택해 미리 본다. 경로 탈출·중복 이름·중첩 ZIP·실행 파일·8 MiB 초과 항목·압축비 1000 초과는 거부한다. 소스는 20 MiB, 최대 1000항목이며 선택한 내용만 메모리로 읽고 CRC를 확인한다. 임시 ZIP은 finally 정리하고 항목을 저장소에 풀지 않는다. 동시 미리보기는 2건, 이미지 입력은 4천만 화소까지·파생본은 최대 1024px다. 원본·승인 포인터를 갱신하지 않는다.

로컬 Java 검증: StudioPreviewTest 7건 + StudioServiceTest 8건 PASS, bootJar PASS. Chromium 실제 브라우저 15개 검사 PASS(PC1440/모바일390 승인 재조회·권한 403·JS 오류/외부 요청 0 포함). axe는 네 영역 구조 검사이며 색 대비 전체 감사는 포함하지 않는다. 브라우저 결과와 캡처는 `stage1-browser-results.json`, `screens/`에 기록한다. 이 단계의 QA는 **합성 자료**이며 실제 EP01 적재 증거와 구분한다. 재현: 기존 `McpStudioMySqlTest`로 격리 사용자/쿠키를 준비 → `make-qa-fixtures.py` → `run-isolated.sh` → `readable-board-qa.cjs`. 포트 18088/격리 MySQL 3318만 사용한다. 쿠키는 `/tmp`에만 보관한다.

## 실제 제작 자료 적재 (후속 PR)

사용자의 #863 추가 지시에 따라 `09d62dee982e2e89e3653c90b1ee42f05bff4f9d`의 `docs/ai-agent-production/` 536개를 GitHub에서 직접 확보했다. 원본 경로별 출처와 SHA를 유지하는 임포터·실제 자료 화면 검증은 별도 PR로 제출한다. 승인 원본 5종 이외에 컷을 자동 승인하지 않으며 **원작 대조 0/68, PASS 확정 0, 대표 10컷 미생성**을 유지한다. 누락 자료를 생성하지 않는다.

## 후속 단계와 실제 연결

문서 본문·MCP image 콘텐츠 블록, 별도 동의된 `studio:propose` 설계, 댓글·작업 기억·인계, 다중 프로젝트/팀원, Hermes 준비를 각각 분리한다. 기존 협업 관제실 #862의 모델 실행·예산 기능은 재구현하지 않는다.

ChatGPT·Claude 실제 연결은 **NOT TESTED**. 사용자가 격리 HTTPS와 두 제품 계정 미준비를 확인했으므로 외부 시험만 보류한다. 프로토콜 image 블록·바이트/SHA 검증은 실제 제품의 이미지 판독 성공을 뜻하지 않는다. 실제 연결·새 세션 판독이 확인되기 전 공동 AI 작업 완료로 보고하지 않는다.
