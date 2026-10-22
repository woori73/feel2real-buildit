# 실제 《AI AGENT》 자료 적재·사람용 검증

사용자 후속 지시 [#862 댓글](https://github.com/woori73/feel2real-buildit/pull/862#issuecomment-6337878937)의 실행 결과다. 운영 DB/설정/배포/MCP 활성화, 유료 모델 호출, 신규 AWS 자원 생성은 하지 않았다. #863을 병합하지 않고 고정 commit의 Git blob을 읽었다.

## 적재 결과

| 항목 | 결과 |
|---|---:|
| 출처 | #863 `09d62dee982e2e89e3653c90b1ee42f05bff4f9d` |
| 작품 자료 + 인계 문서 | 532 + 4 = **536개** |
| 원문 합계 | 164,559,341 bytes |
| 개별 자료/경로/버전 등록 | **536개**, 모두 DRAFT |
| 저장 원문 SHA·크기 일치 | **536/536**, 누락/불일치0 |
| 고유 원본 / 동일SHA 재사용 | **316 / 220** |
| 검증 메타자료 | `.md5` **12개**, 이미지로 취급하지 않음 |
| 보존 러프 패널 | **68개**, 1280×720 |
| 원본 r5 컷 설명/대사 기록 | **68개**, 원문 JSON 별도 보존 |
| 기존 승인 참조 | 고유 SHA **5종** 확인, 출처 승인 이력으로 표시 |
| 정식 원작 대조 / PASS 확정 | **0/68 / 0컷 유지** |
| 같은 작업실 재실행 | 신규 파일0, 신규 이력0 |
| 전체 자료 조회 | 100개씩6페이지, 536개 ID 중복/누락0 |
| SQL 실패 주입 | 부분 배치0, 신규 고아 blob0, 기존 승인 파일 byte/상태 보존 |
| 실패 후 재시도 | 536개 등록 성공 |

실제 workspace/import ID는 [등록 결과](production-results/import-results.json)에 있다. 계정/토큰/비밀번호는 결과물에 넣지 않는다. 실패·승인 보존 시험은 별도 격리 검증 프로젝트에서 수행하며, 실제 제작 자료 프로젝트의 신규536개는 DRAFT 그대로다. 기존 제작실의 head/approved 포인터와 복원 이력을 수정하지 않았다.

## 원본·버전·승인 보존

`materialize-production-source.py`가 고정 Git tree의 일반 blob만 원래 파일명·byte로 추출한다. 실행 파일·symlink·경로 탈출·기존 파일 덮어쓰기를 거부한다. 소스 파일을 readonly로 두며 합성 이미지·새 사건·대사·캐릭터 설정을 만들지 않는다.

`CollaborationProductionImport.dryRun`이 원본536개, MANIFEST의532개 항목, 고정 인계 문서4개의 SHA, 승인 참조5종, 러프68장, 메타12개를 확인한다. 정해진 원본 이외의 누락/추가/변경은 거부한다. 경로·버전·분류·출처 승인 상태는 `ai_collaboration_sources`에 보존한다. 같은 SHA라도 서로 다른 버전/경로를 합쳐 버리지 않는다. 원본 blob만 재사용한다.

r14/r1/r2~r9 rev 이력과 승인5종은 **출처 정보**다. 앱의 신규 상태는 DRAFT이며 원작 정본/사용자 승인 포인터로 승격하지 않는다. 원본 JSON에 남아 있는 `qa_status: PASS`는 내부 정합의 과거 표기다. 별도 정규화 필드는 `NOT_COMPARED`/`pass_confirmed=false`로 등록하고 화면에 정식 원작 대조0/68·PASS0을 명시한다. 원문 JSON 자체는 고치지 않는다.

배치는 하나의 DB 트랜잭션에서 workspace 행 잠금으로 직렬화한다. source_ref+inventory_sha로 같은 배치를 재사용하고 source_path/file/SHA/크기/버전/분류/출처 상태와 실제 저장 byte를 다시 검증한다. 재실행은 파일/이력을 추가하거나 승인 상태를 초기화하지 않는다. 새 blob은 DB 등록 직후의 기존 rollback hook으로 정리하고 기존 dedup/승인 원본은 보존한다.

임포터는 **local profile + 명시적 enabled + loopback의 지정된 격리 테스트 DB + LocalFileStorage**에서만 실행 가능하다. HTTP/MCP import 도구와 자동 startup/scheduler job을 추가하지 않았다. 일반 운영 profile에서는 임포터 bean이 없다. 운영 입력/원격 다운로드 URL을 받지 않는다.

## 사람용 화면

실제 Git 자료로 다음을 Chromium에서 실행했다. 생성 모델이나 응답 대체 자료를 사용하지 않았다.

- 68컷 콘티 보드: 보존 PNG와 r5의 설명·연출·대사, 12컷 단위의 전체6개 구간 및 마지막68컷.
- 원본 이미지 확대, S5/S6의 실제 v1 후보와 v2 승인 참조를 SHA/경로/버전과 함께 비교.
- 실제 r14 HTML(스크립트/외부 리소스 차단 iframe), CURRENT_STATE Markdown, 콘티 PDF 페이지 뷰어.
- `.md5`는 UTF-8 검증 메타자료로 열람·다운로드. 이미지 카드/컷으로 등록하지 않음.
- 사람용 더 보기로 536개 전수 조회, 파일명/원본 경로 검색.
- CURRENT_STATE/APPROVAL_HISTORY/MISSING_FILES 원문을 공동 기억·결정·진행에 출처 파일ID와 함께 표시. 다음 작업은 인계 문서의 미해결 기본안 확인으로 기록. 참여 AI를 임의 배정하지 않음.
- 모든 미리보기/확대/비교 뒤 실제 원문 SHA를 재대조. 원본 파일은 불변.

[브라우저 결과](production-results/browser-results.json) · [PC 콘티](production-results/screens/real-storyboard-1440.png) · [모바일 콘티](production-results/screens/real-storyboard-390.png) · [PC 비교](production-results/screens/real-version-compare-1440.png) · [모바일 비교](production-results/screens/real-version-compare-390.png).

원본 HTML의 preview는 script/event handler/외부 주소를 제거한 임시 표현이며 sandbox/CSP로 격리한다. 원문 다운로드 byte는 바꾸지 않는다. PDF는 한 번에1개 렌더·페이지 크기 제한을 적용하고 PNG 응답만 생성한다. Markdown은 DOM text node로 그리며 문서 HTML을 실행하지 않는다. 자동 axe 검사는 보드/일반 화면의 WCAG 위반0이며 원본 문서 전체에 대한 수동 접근성 판정을 대신하지 않는다.

## 재현과 검수

```sh
# 코드 checkout의 Git 객체에 없을 때만 고정 #863 commit을 fetch (병합 아님)
git fetch --no-tags origin 09d62dee982e2e89e3653c90b1ee42f05bff4f9d
python3 scripts/ai-agent-studio/materialize-production-source.py /tmp/f2r-production-source

# .github/workflows/collaboration-check.yml의 격리 MySQL3319/합성 계정 사용
# 운영 DB/파일 저장소/비밀값을 지정하지 않는다.
F2R_COLLAB_PRODUCTION_TEST=true F2R_COLLAB_PRODUCTION_SOURCE=/tmp/f2r-production-source \
  F2R_COLLAB_BROWSER_TEST=true NODE_PATH="$PWD/e2e/collaboration/node_modules" \
  bash gradlew test --no-daemon --tests '*CollaborationProductionMySqlTest'
```

이 검증은 실제 local DB/FileStorage에 자료를 적재한다. 테스트 계정과 검증용 프로젝트를 만들어 독립 실행하며, 같은 workspace의 importer 재호출 멱등성도 따로 확인한다. 전체 과정을 반복하면 검증 프로젝트가 별도로 생기므로 전역 DB 합계와 [결과 파일의 workspace별536개](production-results/import-results.json)를 혼동하지 않는다. 기존 파일/프로젝트를 삭제하거나 DB를 reset하지 않는다. 이력 원문은 DB에 보존되며 현재 인간용 프로젝트 기억 목록은 최근200개(자료 목록은 cursor로 전수 조회 가능)다.

증거: [dry-run](production-results/dry-run.json), [536개 SHA·버전 표](production-results/sha-comparison.json), [재실행/페이지](production-results/replay-pagination.json), [SQL 실패·복구](production-results/rollback.json). 결과는 비밀값을 포함하지 않는다. CI는 동일한 고정 source를 읽어 이 절차를 반복한다.

**미완료:** Claude 독립 검수, #862/#863 병합 순서 결정, 운영 공개. ChatGPT·Claude 실제 OAuth/MCP 연결과 실제 이미지 판독은 **NOT TESTED**로 유지한다. 새 HTTPS 서버를 만들지 않았고 기존 ALB·도메인 재사용 검토의 조건도 변경하지 않았다.
