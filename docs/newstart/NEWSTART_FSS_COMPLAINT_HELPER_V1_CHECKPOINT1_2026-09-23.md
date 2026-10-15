# NEWSTART FSS COMPLAINT HELPER V1 — CHECKPOINT 1 (출발 감사 + 설계)

- 일시: 2026-09-23 / branch `feat/newstart-fss-complaint-helper-v1` @ worktree `C:\Users\sxxra\f2r-wt-fss-helper`
- START MAIN: origin/main `1352bb01` (Merge PR #371) — 최신 fetch 후 분기. 공유 worktree 미사용.

## EBOOK SOURCE FOUND
- FILE: `docs/newstart/ebook/EBOOK_FULLTEXT.txt`(정본 전문), `ebook_sections.json`(19 sections, ID EBK-*), `EBOOK_INDEX.md`
- FORMAT: HWP 5.0 자체 파서 추출(6ecaf8df Agent3 P0). authority=`OPERATOR_PRACTICAL`, status=`REVIEW`. 제1장 부재, 이미지 14장 미OCR.
- SAMPLES: **3종 발견 (과업 기술의 "4개"와 불일치 — 사용자 확인 필요)**
  - `EBK-6-3` L713-824 "금감원 민원 샘플 3종 (전문 수록)":
    - 3-1 채권추심·법적 절차 중지 요청 (L713-735)
    - 3-2 상계처리 중단 요청 (L737-778)
    - 3-3 부실차주 신청 예정자 채권추심 중단 요청 (L779-824)
  - 전문 전체에서 `제목:` 샘플 블록은 이 3개뿐(grep 검증). 4번째는 e-book에 없음.
- MANUAL SOURCE: **세부 사이트 접수 단계 매뉴얼은 e-book에 없음.** 실전 절차 서술은:
  - `EBK-6-1` L641-711: 민원 넣기 판단 기준 → 1~3일 내 채권사 연락 → 취하 조건(추심금지·소송금지) → 녹취 보존 → 무연락 시 2일 간격 재민원
  - `EBK-6-2` L661-683: 민원 부담 시 전략적 연체→부실우려(신속) 우회 3단계
  - §6 지침(없는 절차 창작 금지)에 따라 위 실전 루프를 단계형 안내로만 사용. 사이트 UI 클릭 순서 등은 임의 추가 금지.

## AI INFRASTRUCTURE 감사 (main 기준)
- 재사용 대상: `WikiAiRouterClient.complete(system, prompt, maxOutputChars)` (problemgenerator). POST `{base}/api/ai/complete`, 로그인 principal 서명 필수.
- `NewStartAiChatService.ask()`(main)는 Q&A 전용 system prompt 고정 → 본 기능의 작성이(system) 계약이 다르므로 **service만 신규**(`FssComplaintComposeService`)하고 router 클라이언트는 그대로 재사용. PR #374(askWithEvidence) 코드 수정·cherry-pick 없음 — main의 router infrastructure만 사용.
- logging/storage: newstart AI 경로(NewStartAiChatService/Controller, WikiAiRouterClient)에 Logger 부재 확인 → 사용자 본문 로그 잔존 없음. `NewStartChatService`(채팅방, DB 저장)는 별개 — 사용 금지.
- V1 영속화: 없음(무상태 request→response). production DB write 0.

## 설계
1. 정적 source: `src/main/resources/newstart/fss/fss_complaint_sources.json` — EBOOK_FULLTEXT 해당 줄 **verbatim** 사본 + provenance(source_lines, authority, ebook commit). 생성 문안 없음.
2. API: `GET /api/newstart/fss/companion`(samples+manual payload), `POST /api/newstart/fss/compose` {sampleId, situationText} → AI 정리문. 길이 가드(입력 ≤4000자), 빈/초단문 가드, 에러 응답에 사용자 원문·내부 상세 미포함.
3. Prompt 계약: §5 전문 + 선택 샘플 전문을 구조 참조로만 주입, 미제시 사실은 `[… 확인 필요]` 플레이스홀더 지시. 금단 어휘(결과 단정)는 system에 CLASSIFY/INFORM/JUDGE 원칙 유지.
4. UI: `templates/newstart/fss_helper.html`(mobile-first, 기존 newstart.css 토큰) + 홈 카드 1개(실전 도우미) 최소 진입점. 3-step(선택→작성→정리), 결과 화면 [원문]/[AI 민원문] 구분, 다시정리/직접수정/전체복사. 접수안내 + `[금감원 민원 접수하러 가기]`(checkpoint 3에서 현재 공식 URL 검증 후 새 탭, 자동화 0).
5. privacy/log/XSS: escaping은 HTML 출력 바인딩 시 textContent/Thymeleaf 기본. 로그 미출력 회귀 테스트.

## HARD LIMITS 준수
PR #374/#375/EASY/CHOONGGI/내신Wiki 수정 0 · production DB write 0 · 자동 제출 0 · 자격증거 저장 0 · 브라우저 자동화 0.

## 미결(사용자 확인)
- [ ] "예시 4개" vs 실재 3종 — 4번째 소재(미OCR 이미지/제1장/타 자료) 지시 필요, 없으면 3종으로 확정
- [ ] 매뉴얼 범위: e-book 실전 루프(EBK-6-1/6-2)만 사용으로 확정할 것
