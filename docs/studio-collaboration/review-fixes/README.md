# Claude 검수 수용 후 수정·병합 준비 보고서

사용자 결정: #863/#864~#871 PASS 수용, #862는 중복 FAIL로 병합 제외. 정본은 main의 #855 `studio_*`와 그 위의 기존 스택이다. #862 본문에 대체·병합 제외를 표시하고 모든 커밋/본문/검증 이력은 보존했다. [고유 기능 및 후속 PR 제안](EXCLUDED_862.md). V1096~V1098 및 별도 테이블은 가져오지 않는다.

## 수정

- 실제 콘티 카드의 설명·대사·연출을 묶고 **더 보기/접기**를 제공한다. 개별 문단의 고정 높이·내부 스크롤을 제거해 펼치면 끝까지 읽을 수 있다. 원문 내용은 바꾸지 않고 aria-expanded/aria-controls·키보드 조작을 지원한다.
- 승인된 리비전에 대한 일반 REJECT는409로 거부한다. 미승인 후보의 반려는 기존 승인 포인터를 바꾸지 않는다. REQUEST_CHANGES/새 후보 승인 동작은 유지한다.
- 별도 승인 해제는 원본 SHA·리비전 확인, 사유, 확인 체크를 요구한다. 서버는 confirmed=true·expectedApprovedId·현재 승인 ID·APPROVER·프로젝트·보관 상태를 검사하고 행 잠금 아래 승인 포인터만 해제한다. `WITHDRAW_APPROVAL` 감사(실제 사용자/대상 리비전/사유/시각)를 같은 트랜잭션에 append한다. 기존 원본·SHA·리비전·APPROVE 이력은 수정/삭제하지 않는다.
- 확인 누락400, 승인 교체/중복409, 취소는 변경0. 저장 후 실제 GET으로 해제를 재확인하고 스레드/알림/대기함에 상태를 표시한다. 새 POST는 MCP 허용 목록에 추가하지 않았다.

## 검증

[격리 MySQL 결과](java-results.json): 새 승인 보호7건 + 기존 프로젝트8/공동 기억6 + IP쿼터 분창 검증1 =22건 PASS. 승인본 REJECT 차단, 미승인 후보 반려/기존 승인 유지, 일반 review로 해제 우회 차단, 확인/사유/현재 승인 ID, 중복/오래된 요청, 타 프로젝트/권한/보관 차단, byte/SHA/revision/승인 감사 보존을 확인했다. bootJar PASS.

첫 회귀 실행의 MCP22건 중21건은 통과했고 public registration quota1건이429 대신200으로 실패했다. 기존 테스트는5개+6번째 요청이 같은 고정 분창 안에 있다는 전제를 확인하지 않았다. 생산 쿼터는 바꾸지 않고 **실제 분창이 바뀐 경우에만** 다시 측정(최대2회)하도록 테스트를 보완했다. 같은 분 안의 헤더 회전 우회 차단/Retry-After 검증은 그대로이며 실패를 skip하지 않는다. 수정된 해당 테스트만 재실행해 PASS를 확인했다. 최종 CI는 MCP 전체 및 기존 롤백/Flyway 회귀를 다시 실행한다.

[브라우저12개 PASS](browser-results.json): PC1440/모바일390 실제 콘티 전체 설명·키보드 펼침/접기·가로 넘침0, 일반 승인/승인본 반려409, 별도 확인·취소·확인 누락, 확인 후 해제·실제 재조회·감사·중복/오래된 요청 차단. 실제 작품은 읽기만 했고 **승인/해제는 명시적인 별도 합성 QA 프로젝트 B/C에서만** 실행했다. 실제 작품536경로·316파일·승인5종 SHA/포인터·기억·기존 감사·미승인68컷 불변, JS오류0. 수정 검증용 loopback 앱은 MCP OFF로 실행했고 운영 설정/유료 모델/금융기관은 사용하지 않았다.

화면: [PC 전체 설명](screens/expanded-1440.png) · [모바일 전체 설명](screens/expanded-390.png) · [PC 별도 확인](screens/confirm-1440.png) · [모바일 별도 확인](screens/confirm-390.png) · [모바일 해제 감사](screens/withdrawn-390.png).

재현: 기존 실제 자료 fixture의 로컬 MySQL3318/f2r_assets_*와 파일 저장소를 재사용하고 앱은127.0.0.1:18090/MCP OFF로 실행한다. `node scripts/ai-agent-studio/review-fixes-qa.cjs`. 인증 fixture는 /tmp에만 보관한다. `F2R_STUDIO_MYSQL_TEST=true bash gradlew test --tests '*StudioApprovalProtectionMySqlTest'`가 새 DB 회귀다. 최종 원격 HEAD/CI는 수정 PR Checks 및 PR 본문에 기록한다.

## 병합 승인 범위와 남은 제한

[병합 절차](MERGE_RUNBOOK.md): #863→#864→#865→#866→#867→#868→#869→#870→#871→이번 수정 PR. 선행 merged 확인 후에만 다음 base를 main으로 변경하고 새 main+PR 통합 조합에서 CI 재검증한다. 실제 base 변경/병합/스쿼시/브랜치 삭제는 아직 실행하지 않았다. #869는 문서2개뿐이며 AI 직접 쓰기를 켜지 않는다.

기존 PASS는 사용자가 전달한 Claude 독립 판정으로 수용했다. 이번 추가 수정의 개발/로컬/CI 검증을 Claude의 새 독립 PASS로 표현하지 않는다. 최종 코드 병합 승인을 요청하되 실제 제품 ChatGPT/Claude 연결·직접 그림 판독, AI 후보 쓰기, Hermes 실행은 미검증/설계 상태로 남긴다. 운영 배포·운영 DB 변경·MCP 활성화·유료 호출·정본 삭제/변경은 코드 병합 승인에 포함하지 않는다.
