# [기획 임무 요청서 → Codex] 문제 제작 자동 파이프라인 (Claude Opus 5.5)

발행: 2026-11-28 KST · 요청: 차원연산4 · 수행: Codex · 검수: 차원연산4 · 우선순위: **핵심 수익**

## 1. 목적
F2R의 기존 "제작 공장" 흐름을 **매끄럽게 자동 연결**하고, 생성 엔진에 **Claude Opus 5.5**를 꽂아 예상문제·직보를 **자동 제작**한다. 문제제작은 **최고난도=품질 우선**: **원가 2,000원을 넘어도 허용**(Opus 풀 사용). 대신 **실측 원가를 기록해 요금제 단가를 재산정**한다.

## 2. 현재 코드 지도 (여기에 붙인다)
- **작업 흐름**: `CoordFactoryController` (`/api/admin/coord/factory`) — list/create job · `/{jobId}/brief` · `/cancel` · `/publish/approve` · `/publish/done` · `/publish/upload`. (MCP `f2r_pc_task_*`가 이 흐름을 구동.)
- **생성 엔진(어댑터 구조)**: `LunaProblemTransformationService`(OpenAI, api.openai.com) · `GeminiProblemGenerationService` · `N8nProblemGenerationService` · `WikiAiRouterClient`. → **여기에 `ClaudeProblemGenerationService`(Opus 5.5) 어댑터 추가.**
- **세트 저장/수정**: `ProblemGeneratorQuestionSetController` (`/api/problem-generator/question-sets`).
- **검수**: 다른 **3곳** `f2r_pc_task_review`(세션/검수자). (`GrammarBankController`의 review 참고.)
- **내신 Wiki 저장**: BuildIt_Wiki MCP `save_generated_test` / `save_final` / `register_weekly_exam_draft`.

## 3. 요구사항
1. **Opus 5.5 생성 어댑터**: 기존 멀티엔진 어댑터에 **Claude Opus 5.5**를 추가(모델 `claude-opus-5-5`). 입력=기출+시험범위, 출력=예상문제/직보(유효 JSON). 프롬프트 캐싱 적용(CLAUDE.md).
2. **단계 자동 연결**: `기출 읽기 → Opus 5.5 생성 → 3곳 검수 → 내신위키/원장 지정 업로드`가 **수동 핸드오프 없이** 이어지도록 CoordFactory job 상태 전이 자동화(제작 공장 현황판 반영).
3. **저작권 규칙(필수, 검수로 확인된 F2R 원칙)**: 기출에서 **평가요소·사고과정·오개념만 읽고 지문·보기·선지·해설은 새로 작성**. 첫 페이지 **F2R 로고**. **타사 지문·문항·그림 미사용.**
4. **3곳 검수 유지**: 만든 세션이 아닌 **다른 3곳**이 통과해야 원장 알림·위키 저장.
5. **과금·원가**: 기출세트 **채택·저장된 고유 문항 1건** 기준((B) Phase 2). **Opus 호출 실측 사용량·원가 기록**(공급자 호출 원장, r2 §4) → **요금제 기출세트 단가 재산정 근거**.
6. **라우팅**: 문제제작=Opus 5.5(고난도). 저난도(상담·출결·확인)=카나나, 음성=OpenAI Realtime와 분리(어댑터).

## 4. 검수 기준 (차원연산4)
1. 기출+범위 입력 → **Opus 5.5가 예상문제/직보 자동 생성**(유효 JSON·문항 수 충족).
2. 저작권 규칙 준수(지문·선지 새로, 로고, 타사 미사용) — 산출물 점검.
3. **3곳 검수 통과** 후에만 위키 저장·원장 알림.
4. 단계 **자동 전이**(수동 개입 최소) + 실패 시 상태/재시도 안전.
5. **Opus 실측 원가 기록**(토큰·모델·요청ID) → 세트당 원가 산출.
6. 과금: 채택 문항 1건 기준, 재생성·수동문항 중복 차감 없음.

## 5. 비용 방침
- **품질 우선**: Opus 5.5 풀 사용, **세트 원가 2,000원 초과 허용.**
- 단 **실측 원가를 반드시 기록** → 요금제(기출세트 단가·마진) 재산정에 사용. (요금제 상품 설계는 별도 기획.)

## 6. 분담
- **구현**: Codex. **검수**: 차원연산4(§4).
