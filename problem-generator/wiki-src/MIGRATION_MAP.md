# Wiki migration map

## Production path

`/problem-generator/wiki/` is the Wiki frontend path.

All Wiki backend calls must go through:

`/problem-generator/wiki/api/** -> BuildItWikiProxyController -> http://127.0.0.1:8000/api/**`

Do not call `/api/**` directly from the Wiki frontend.

## Recovered frontend modules

- `App.tsx`: current page/router shell and exam draft state
- `MiddleSchoolExamGenerator.tsx`: new / transform problem generation
- `DigitalTwinImport.tsx`: Digital Twin import and apply
- `SchoolDatabase.tsx`: school materials + school print OCR/edit/export
- `TextbookDatabase.tsx`: textbook DB
- `StudentWrongNoteDashboard.tsx`: student wrong-note capture/review/export
- Teacher question DB and exam maker currently live in `App.tsx`

## V1 educator-state integration

Entry page becomes `EducatorState` while all legacy tool routes remain available.

State actions route to existing tools:

- 시험 문제 -> `home`
- 수업 자료 / 학교 프린트 -> `school`
- 학생 자료 / 오답 -> `wrongnote`
- 수업 인사이트 -> `brain` (`educator_insights` SQLite 저장 + 최근 기록 조회)

FastAPI 원본은 `problem-generator/wiki-backend-src/`에서 관리하고 배포 워크플로가 이 디렉터리를 패키징한다. 과거 `wiki-source.tgz.b64`는 신규 배포 입력으로 사용하지 않는다.

## API normalization

- `src/lib/api.ts` must use `/problem-generator/wiki/api/`
- Vite `base` must be `/problem-generator/wiki/`
- Any direct `fetch('/api/...')` is a regression risk and must be replaced with the Wiki proxy path or shared `api()` helper.
- `SchoolDatabase.tsx` currently contains a direct school-print export call and must be normalized during source import.

## Non-regression rules

Preserve:

1. Digital Twin import/apply
2. new problem generation
3. problem transform
4. teacher question DB persistence
5. exam draft persistence and selected order
6. Office 2007 DOCX export
7. student wrong-note accumulation/export
8. textbook DB
9. school DB and school-print workflow
10. n8n generation route

The UI may be reorganized into `만들기 / 학생 / BRAIN`, but these underlying functions must not be deleted.
