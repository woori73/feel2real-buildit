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

## #863 원본 임포터

`production-09d62dee.json`은 사용자가 지정한 Git 커밋의 경로·크기·SHA 카탈로그다. 전체 536개(164,559,341 bytes), 316고유 SHA, 중복 경로 220개. 원본 콘텐츠는 저장소에 다시 복사하지 않는다. Git 객체에서 읽거나 `--extract-to`로 로컬에 확보하며, MANIFEST의 원본 532개와 4개 인계 문서의 해시를 확인한다. 기본 dry-run이며 `import-dry-run.json`을 참조한다.

실제 적용은 `import-production.py --source <격리 확보 경로> --apply --base-url http://127.0.0.1:18090 --auth-file <격리 로그인 쿠키 파일> --receipt <결과.json>`. 쿠키는 채팅·PR·로그에 남기지 않는다. 각 고유 파일의 실제 보관 bytes를 다시 읽어 SHA 대조 후 서버가 고정된 카탈로그와 일치하는 참조만 원자 등록한다. 브라우저 파일 목록은 전체 페이지를 조회한다. MD5 원본 12개도 파일명을 유지해 보관한다. HTML 내 내장 이미지를 제거한 뒤 문서 길이를 제한하므로 대형 HTML 콘티도 안전한 텍스트로 열람한다.

r14 콘티 68개와 r5 콘티 68개를 서로 다른 버전으로 보관한다. 보드는 r5 최신 텍스트와 보관된 러프 r1을 연결하며 두 출처를 표시한다. r9/rev1은 원본 문서/제작 명세로 보존하며 **10개 대표 컷 명세를 68개 전체 콘티로 오인하지 않는다**. 내부 JSON의 과거 `qa_status=PASS`는 원본 bytes에만 보존하며 현재 컷의 승인·원작 대조·PASS로 승격하지 않는다. 화면의 ORIGINAL은 ‘보관 원본’으로 표시해 승인/정식 원작 판정과 구분한다.

승인 원본 5종만 기존 사용자 승인 근거와 함께 이관한다. S5·S6 v1·리더 v1·러프 패널을 승인 캐릭터로 등록하지 않는다. 등록은 파일·기존 revision·승인 포인터를 덮어쓰지 않는다. 원본을 먼저 업로드한 뒤 DB 참조를 한 트랜잭션으로 등록하므로, DB 등록 실패 후 원본 보관함의 선행 파일은 남는다. 신규 파일 저장 실패의 보상 정리는 #855 기존 구현을 재사용하며 프로세스 중단/스토리지 삭제 실패까지 완전한 분산 트랜잭션을 보장하지 않는다.

실제 적재·검증 결과: `import-local-receipt.json`은 API로 저장 원본 316개를 다시 읽어 SHA를 검증하고 536경로 매핑을 재조회한 영수증이다. 최초 등록은 678개 append-only 버전(출처536+컷136+승인캐릭터5+초기기억1), 610개 자료 키다. 재실행은 createdRevisions=0이다. `import-java-results.json` 20건 PASS 중 실제 작품 MySQL 회귀 5건은 536경로/SHA·30번째 컷 실패 2회 전체 롤백·재시도·승인5종·무권한/잘못된 매핑 거부를 확인했다. 테스트마다 새 격리 schema를 만들고 사용한 DB/파일 저장소는 로컬에서 보존한다. 운영 DB와 무관하다.

실제 작품 Chromium 16개 검사 PASS: [결과](actual-browser-results.json), [PC 콘티](screens/actual-board-1440.png), [모바일 콘티](screens/actual-board-390.png), [승인 캐릭터](screens/actual-characters-1440.png), [모바일 캐릭터](screens/actual-characters-390.png), [대본](screens/actual-script-1440.png), [PDF](screens/actual-pdf-1440.png), [ZIP](screens/actual-zip-1440.png). 68개 이미지를 실제 디코딩했고 이미지·설명·확대·이전/다음·문서/ZIP을 열람했다. 실제 작품을 수정하거나 새 승인하지 않았고 감사 이력도 그대로다. 사용자에게 중간 파일 전달/수동 업로드를 요구하지 않았다. 외부 ChatGPT·Claude 연결은 이 결과에 포함하지 않는다.

## MCP 문서·이미지 읽기 (별도 PR)

[전달 규격·보안·실접속 조건](MCP_MEDIA.md), [Java 32건 PASS](media-java-results.json), [공식 SDK 결과](media-protocol-results.json). 실제 #863 CURRENT_STATE.md와 승인 기준 시트를 독립 OAuth grant 2개로 읽어 같은 문서 digest와 같은 JPEG 파생본 SHA를 확인했다. 결과는 text 메타데이터 + image/jpeg 콘텐츠 블록이며 파생본 70,577 bytes다. 별도 원본 동의 없는 세 번째 grant는 문서만 읽고 이미지 블록은 거부됐다. POST 등록도 거부됐다. 3개 grant는 로컬 공식 SDK 시험 클라이언트이고 실제 ChatGPT·Claude 계정이 아니다. 실제 모델 시각 판독은 NOT TESTED이며 외부 유료 호출 0이다.

로컬 실제 원본 서버 재개는 `run-production-local.py`(격리 테스트가 만든 3318/f2r_assets_* 및 /tmp 저장소만 허용, loopback18090에만 바인딩), 프로토콜 검증은 `media-protocol-qa.py`다. 이 스크립트는 운영 주소·DB를 받지 않는다.

## 공동 작업 기억·컷별 검수 (4단계 별도 PR)

새 테이블 없이 기존 revision/event에 결정과 댓글을 보존한다. 마지막 결정·현재 담당·열린 문제·다음 작업·최신 PR/SHA·연결 파일은 서버의 새 버전으로 저장하고 실제 재조회한다. 오래된 expectedRevisionId는 409, 이전 결정은 불변이다. 컷 댓글은 전 버전을 묶고 각 버전/실제 사람 작성자를 표시한다. 사람이 공유한 Claude/Codex/Muse/ChatGPT 자료는 근거 파일이 필요하며 AI 직접 인증 작성으로 표시하지 않는다. AI 직접 쓰기 연결은 별도 설계 대상이다. 수정 요청은 승인 정본을 바꾸지 않는다. 승인 대기함은 100개씩 페이지 조회하며 보관용 출처536개를 제외한다.

MySQL 회귀36건 PASS(공동 기억6·기존 Studio8·MCP22), Markdown 표/안전 렌더9건 PASS. [브라우저11개 PASS](memory-browser-results.json): [PC 기억](screens/memory-1440.png), [모바일 새 세션 복원](screens/memory-390.png), [승인 이력 표](screens/memory-approval-history-1440.png), [댓글·수정 요청](screens/comments-1440.png), [승인 대기함](screens/inbox-390.png). 실제 작품에서 새 세션·다른 사용자가 동일 기억/파일4개를 복원했고, 오래된 저장 거부·수정 요청 후 컷 미승인 유지·무권한403을 확인했다. [공식 SDK 새 세션 인계](memory-protocol-results.json)도 같은 revisionId/digest를 읽고 POST를 거부했다. 이는 외부 제품 계정 검증을 대신하지 않는다.

## 5단계 · 프로젝트·팀·알림 (로컬 검증 완료)

프로젝트 생성·제목 수정·보관·재개는 ADMIN에게만 허용하고 모든 실제 변경을 감사 기록한다. 영구 삭제는 없다. 보관해도 모든 문서·그림·컷·SHA·버전·승인·기억은 읽을 수 있으며 자료 수정은 재개 후 허용한다. 사용자 이름/닉네임 검색으로 초대하고 상대방의 수락 전에는 접근을 허용하지 않는다. VIEWER/EDITOR/APPROVER 역할을 재사용한다. 검수·승인·댓글 알림은 프로젝트 내에서만 보이며 GET은 읽음 상태를 바꾸지 않는다. 신규 스키마/마이그레이션 없이 기존 프로젝트·권한·감사 테이블을 재사용했다.

[Java44건 PASS](projects-java-results.json): 프로젝트8·기존 공동 기억6·Studio8·MCP22. [실제 브라우저9개 PASS](projects-browser-results.json): 기존 작품+빈 QA프로젝트2개 동시 관리, PC 제목/보관/재개, 모바일 초대 수락/알림, 동일 출처/역할/프로젝트/파일 격리, 기존536경로·316파일·승인5종 SHA·버전/승인 포인터·작업 기억 보존. 원작 대조0/68·PASS확정0·컷 승인0·대표10컷 미생성 유지. [PC 프로젝트](screens/projects-1440.png), [모바일 프로젝트](screens/projects-390.png), [보관 화면](screens/project-archived-1440.png), [모바일 알림](screens/notifications-390.png).

[공식 MCP SDK](projects-protocol-results.json)는 동일 프로젝트의 실제 문서/image 블록·카탈로그 projectId, 수락한 프로젝트만 목록 표시, 타 프로젝트 원본/기억 차단, 생성/보관/수락/읽음 처리 POST 차단을 확인했다. 외부 모델 호출0. 실제 ChatGPT/Claude 제품 연결·시각 판독은 미검증이다. 원본 자료는 격리 MySQL과 로컬 파일 저장소에 실제 적재되어 있으며 운영 제작실에는 배포/등록하지 않았다.

## 3단계 · AI 후보 쓰기 연결 설계

[별도 동의형 연결 설계](STUDIO_PROPOSE_DESIGN.md)는 기존 읽기 MCP와 OAuth resource/token/scope를 분리하고, 인증된 AI 주체·출처/SHA·DRAFT·사람 확인·별도 승인을 명시한다. **설계만 제출하며 직접 쓰기 endpoint/토큰 발급/DB는 미구현·비활성이다.** 현재의 사람이 공유한 AI 파일/댓글과 실제 AI 인증 쓰기를 구분한다. Claude 독립 보안 검수와 별도 구현/검증이 필요하다.
