# WEEKLY AI Context 연결

`/wiki/learning` 진입 시 `wiki_learning.js`가 다음 두 정적 기준 자료를 즉시 읽는다.

- `/wiki/weekly/WEEKLY_GENERATION_STANDARD.md`
- `/wiki/weekly/WEEKLY_REFERENCE.html`

두 자료는 사용자의 펼침/선택 없이 다음 두 위치에 항상 노출된다.

1. `sessionStorage.buildit_weekly_ai_context`
2. DOM `#weeklyAiContext[data-ai-context="WEEKLY_STANDARD"]`

Browser/Chrome AI는 WEEKLY 생성 시 `generation_standard > visual_reference > current_half_source` 우선순위를 사용한다.

현재 WEEKLY는 Wiki 학습자료의 기존 `WEEKLY` 유형을 그대로 사용하며 별도 자료 유형을 추가하지 않는다.