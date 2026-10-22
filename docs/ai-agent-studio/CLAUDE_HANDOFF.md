# Claude 독립 검수 인계

**상태: 검수 요청 준비, 독립 검수 미실행.** Codex의 자체 시험을 Claude 검수로 표시하지 않았다. 이 환경에는 Claude 로그인 세션/CLI나 유료 API 사용 승인이 없으며, 사용자 요청대로 Codex 단일 에이전트가 두 업무를 순서대로 개발했다.

- 저장소 `woori73/feel2real-buildit`, 브랜치 `feat/ai-agent-studio-unified-mcp`, main `07d9316a293a30a84e13a09c42957bd64be71bd8` 기준.
- main 병합·운영 배포·운영 DB 적용 금지. PR #850의 랜딩·3D 파일을 수정하지 않았다. 기존 금융 수집 PR #853의 구현 파일도 수정하지 않았다.
- 신규 V1095는 이 브랜치의 추가 스키마일 뿐 운영에 적용하지 않았다. 병합 직전 최신 main 및 다른 PR의 번호 충돌을 다시 확인한다.
- 전체 자료: REPORT.md, SERVICE_INVENTORY.md/JSON, SECURITY_PERFORMANCE_COST.md, CONNECTION_TEST.md, protocol-results.json, browser-results.json, screens/.

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
