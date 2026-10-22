# Claude 독립 검수 인계

**현재 상태:** Codex의 긴급 P1/P2 코드 수정·격리 MySQL·브라우저·CI 검증 완료, Claude 독립 재검수 대기. 자체 시험을 독립 검수 판정으로 표시하지 않는다. 재검수 항목·원본 handoff 출처·최신 검증은 [긴급 수정 보고서](URGENT_FIXES_20261022.md)를 따른다. 현재 main 기준은 #859 `930716ec`이며 #854 → #853 → #855 순서와 V1094/V1095 체크섬을 유지한다. 검수자는 GitHub 각 PR의 최신 HEAD/checks를 확인한다.

- 수집 배치 일반 확정/계정 변경 거부, 승인 전용 상태/사업체/출처/검토 해시 검증을 #854에서 #853/#855로 전달했다. Claude 원본 재현 2건과 양성/변조 대조를 정식 MySQL 시험에 편입했다.
- Studio 신규 파일 롤백 정리·30번째 컷 SQL 실패·재시도, MCP originals:read/경고·권한 설명·+82/숫자 마스킹·SHA/UUID 보존·framework proxy 경계를 추가 검수한다.
- 실제 ChatGPT/Claude 구독 계정 연결·실제 EP01 정본 등록·운영 ALB/RDS 확인은 미검증이다. main 병합·운영 배포/DB 변경·MCP 활성화·금융기관 접속은 실행하지 않았다.
- 전체 자료: URGENT_FIXES_20261022.md, urgent-test-results.json, GET_SAFETY_AUDIT.md, CONNECTION_TEST.md, protocol-results.json, browser-results.json, consent-browser-results.json, screens/.

## 검수에서 실제 확인할 것

1. **사용성·접근성:** PC/모바일 14화·EP01 68컷 업로드 대기, 각본·콘티·캐릭터·영상·작업·검수 등록/열람/새 버전, 파일 연결·원문·비교·승인. 동적 로딩 중 선택값 유지, 키보드/포커스·대화상자·축소/터치·스크린리더를 직접 확인한다.
2. **원본·승인:** 모든 업로드 형식 bytes/SHA 보존, 중복·동시 업로드, parent 경합, 라벨 중복, AI 제안 미승인 유지, 원작 출처와 승인 포인터 구분, 반려 시 이전 이력 보존. 임의 실제 작품 파일을 만들지 않는다.
3. **권한·보안:** 익명/VIEWER/EDITOR/APPROVER/ADMIN/다른 TUTOR, scope 축소, 프로젝트/서비스/학원 간 IDOR, token 만료·role/password/멤버 변경·폐기·refresh 재사용, callback/PKCE/resource/nonce 오류, origin·path encoding·query 우회·chunked body·속도 제한을 검증한다.
4. **읽기 경계:** 등록된 exact GET 정책을 실제 서비스 구현과 대조한다. read-only/rollback이 SQL뿐 아니라 각 경로의 파일/원격 서비스 부수효과를 막는지 검토한다. REQUIRES_NEW·비동기·원격 호출·숨은 초기화가 있는 경로는 별도 확인한다. 외부 AcademyCraft DB에는 Spring의 트랜잭션이 적용되지 않으므로 기존 실제 backend GET을 독립 시험한다.
5. **정확성:** 전체 서비스 census와 실제 runtime routes, WebMvc view, Wiki backend mount 여부, dynamic screenSources와 실제 화면 API가 일치하는지 확인한다. 정적 transitiveTableCandidates를 개별 실제 SQL이라고 해석하지 않는다. 미허용 경로를 “조회 성공”으로 집계하지 않는다.
6. **원격 연결:** 사용자 계정으로 ChatGPT와 Claude 각각 OAuth·도구 탐색·동일 실제 자료 ID/SHA·승인 상태를 비교한다. SDK 클라이언트 시험을 두 제품의 실제 계정 시험으로 대체하지 않는다.
7. **회귀:** 기존 로그인·ADMIN 세션 교체·Wiki 회원 경계·AcademyCraft Gateway·BuildIt 저장/게시·EASY 사업체 격리·NewStart 상담/감사회원과 홈 PR #850의 분리 여부. 운영 쓰기는 하지 않는다.
8. **성능·비용:** 실제 EC2 격리 환경의 메모리·동시 파일 전송·PDF 입력·DB 검색·audit 증가량과 외부 전송량을 확인한다. OpenAI/Claude/embedding 과금 호출이 없는지 검토한다.

## Codex로 반환할 형식

각 발견을 `심각도 / 파일:줄 / 재현 절차 / 기대·실제 / 근거(민감 원문 제외) / 수정 권고`로 남긴다. Codex는 같은 브랜치와 같은 PR에서 수정하고 해당 시험을 재실행한다. 독립 검수 결과가 아직 없으므로 “Claude 지적 모두 수정 완료”라고 선언할 수 없다.

재검수 대상의 최신 근거는 URGENT_FIXES_20261022.md다. 사용자가 제공한 `ccr-d295072e-waeyz7`의 `cf3c12a5` 및 최초 handoff `5bc0a3a`를 확인해 원본 재현 시험을 보존·편입했다. 실제 제품 계정 연결·실제 EP01 원본 등록은 미완료 상태를 유지한다.
