# WEEKLY 생성 완료 조건

- 기존 Wiki Learning `WEEKLY` 자료 유형을 유지한다.
- `/wiki/learning` 최초 진입에서 제작 기준 MD와 기준 HTML을 자동 로드한다.
- 두 자료는 사용자 클릭 없이 Browser AI가 읽을 수 있는 DOM에 포함한다.
- 같은 payload를 `sessionStorage.buildit_weekly_ai_context`에도 저장한다.
- 우선순위는 제작 기준 MD > 기준 HTML > 이번 HALF/원문이다.
- 기준 MD는 14문항 순서와 조판/밑줄/빈칸/공통지문 규칙을 포함한다.
- 기준 HTML은 확정 WEEKLY의 A4 2단, 9.8pt, Flexible Pagination, 단일 밑줄, 일반 굵기 제시문을 표현한다.
- 계약 테스트가 위 파일/키/핵심 규칙의 존재를 검증한다.