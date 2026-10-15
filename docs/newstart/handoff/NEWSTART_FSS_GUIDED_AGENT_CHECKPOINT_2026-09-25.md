# NewStart FSS Guided Agent — 2026-09-25 체크포인트 (Phase 1 v2)

> 브랜치: `feat/newstart-fss-guided-agent-phase1` (PR #400) — 이 문서는 그 후속 커밋으로 함께 들어온다.
> 이전 handoff: `docs/newstart/handoff/` 의 2026-09-24 HOME→ACADEMY 문서가 source of truth였다. 이 문서는 그 다음 상태.

## 1. 이번 커밋에서 완료한 것

### 백엔드 (무상태·비저장 원칙 유지, 마이그레이션 0건)
- `FssComplaintHelperService`: guided interview 전용 합성 경로 `composeInterview(Interview)` 추가.
  - 입력 가드: facts 필수, 필드 1000자/타임라인 20행/증거 20건 상한. 원문·인터뷰 내용 로그 미기록(테스트로 검증).
  - INTERVIEW_SYSTEM_PROMPT: FACT/CLAIM/CONCERN/REQUEST 분리, 사용자 진술 외 사실 창작권 금지,
    위법 확정 금지("이 연락이 적절한 절차인지 확인을 요청합니다" 스타일), 7개 섹션 고정 순서,
    "제목:" 40자 + "본문:" 출력 계약.
  - `parseDraft`: 제목/본문 분리 + 실패 시 원문 전체를 본문으로 넣는 fail-safe.
- `fss_complaint_sources.json`에 `officialRouting`(OFFICIAL, 조회 2026-09-25) 추가:
  금감원 공식 안내 3페이지(menuNo 201178/201179/201180) 실측 근거로 소관 분기 데이터 —
  상담 우선(1332), 처리기간(분쟁조정 30일/기타 14일), 타 기관 이송 8건(채무조정→신용회복위원회 등),
  카테고리 6종별 byCategory 안내문.
- `/api/newstart/fss/companion` 응답에 `official` 노드 추가. `/compose`는 `interview` 페이로드 우선 라우팅.
  응답에 `subject`/`body` 분리 필드 추가(§19 제목·본문 개별 복사 지원).
- `NewStartAiChatService` SYSTEM_PROMPT에 §23 연결 문구: 추심/채권사/금감원 민원 언급 시
  `/newstart/fss-helper`로 안내(제출·본인인증은 사용자가 공식 사이트에서 직접).

### 프론트 (`fss-helper.html` 4화면 리라이팅)
- §9 첫 화면 "어떤 일이 있었나요?" 6개 카테고리 버튼 + 카테고리별 공식 소관 안내 패널(§7/§22 분리).
- §10~§12 7문항 인터뷰(STEP 8=최종 확인) + 타임라인 행 추가/삭제 + §11 증거 칩(증거 없음 상호배타).
- §14~§16 초안 화면: 원문 요약·이벤트 타임라인·제목/본문 개별 복사·전체 복사,
  제출 전 체크리스트 7개 전부 선택 시에만 공식 페이지 CTA 활성화(aria-disabled+preventDefault).
- V1 자유서술 경로는 `<details>` 폴백으로 유지(회귀 없음).

## 2. 검증 결과 (2026-09-25)
- `compileJava compileTestJava` PASS. FSS 범위 테스트 45/45 PASS
  (Service/Controller/GuidedWorkflow/Phase1ArchitectureContract/AiChat 포함).
- NewStart 패키지 173건: 172 PASS + 1건(`NewStartOfficialUpdateSpringContextRegressionTest`)은
  공용 `f2r_mysql_main` DB에 남은 타 세션의 실패한 V1000 이력 때문 — 코드와 무관.
  전용 클린 DB(`f2r_mysql_fss_guided_v2`:34831)에서 동일 테스트 PASS 확인. **공용 DB의 failed
  V1000 이력 정리는 별도 결정 사항으로 잔존.**
- bootJar PASS.
- 실제 Chrome headless E2E(`C:\Users\sxxra\ns387-qa\qa_fss_v2.mjs`, 로컬 앱 :8926 + 시드 student):
  29/29 PASS — 카테고리→공식 패널→인터뷰 가드→증거 칩 상호배타→리뷰→(키 없는 환경이라) 실compose
  실패 경로 사용자 문구 확인→stub compose로 3화면→체크리스트 게이트→공식 URL(search.jsp) 유효화,
  Agent 14단계 패널, HOME·A-to-Z 이중 진입, 375/390/430/768 가로오버플로 0·버튼 ≥44px.
  스크린샷 `C:\Users\sxxra\ns387-qa\v2_*.png`.
- 금감원 공식 경로 재실측(STEP1~4, 2026-09-25, `fss_verify_20260925.json`): search.jsp→[신청]→pop-up
  [확인]→minwonChk(네/아니요)→동의 3종 흐름 유지. **STEP 8~14는 인증 이후라 여전히 UNKNOWN —
  사용자 본인 세션에서만 매핑 가능(§6 HARD STOP 유지).**

## 3. 미검증 / 향후
- LIVE LLM으로 interview 초안 실제 품질(제목·본문·섹션 준수) — API 키 환경에서 표본 확인 필요.
- §23 채팅 연결은 프롬프트 안내 수준; 채팅 응답 내 자연스러운 링크 노출 품질은 LIVE 확인 대기자.
- Phase 2(B): Chrome 확장 프로그램 방향은 이번 범위 밖(의도적 미구현).
- 서버 Playwright 자동화(A)는 계속 금지 — `FssPhase1ArchitectureContractTest`가 게이트.

## 4. 재현 명령 (ACADEMY/다른 세션용)
```bash
# worktree: C:/Users/sxxra/feel2real-wt-fss-agent2 @ feat/...-phase1-v2
./gradlew compileJava compileTestJava
SPRING_DATASOURCE_URL="jdbc:mysql://localhost:34831/f2r_db?..." ./gradlew test --tests "f2r...domain.newstart.*" bootJar
SERVER_PORT=8926 SPRING_DATASOURCE_URL=... java -jar build/libs/private_tutor_solution_back-0.0.1-SNAPSHOT.jar &
node C:/Users/sxxra/ns387-qa/qa_fss_v2.mjs   # 29 checks
```
