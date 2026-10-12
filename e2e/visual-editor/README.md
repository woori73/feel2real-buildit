# BuildIt 시각 편집기 — 실제 브라우저 E2E

GrapesJS 0.23.6 기반 시각 편집기(`/tutor/academy/editor`)를 Chromium으로 실제 조작해 검증한다.
CI(Gradle)는 브라우저가 없어 이 E2E를 돌리지 않는다. 대신 CI는 **실제 GrapesJS 출력 fixture**로 서버 정제기·저장 API를 검증한다
(`VisualEditorRealOutputTest`, `VisualEditorMySqlIntegrationTest`). 편집기 화면을 바꾼 PR은 이 E2E를 로컬에서 실행한다.

## 준비
```bash
cd e2e/visual-editor
npm install            # playwright-core, grapesjs@0.23.6(CDN 대체), font-awesome
```
- 외부 CDN(unpkg/cdnjs/jsdelivr)은 `node_modules`의 같은 버전 파일로, 이미지 서비스는 800x400 회색 PNG로 대체해 서빙한다(오프라인·재현 가능).
- Chromium 경로: 기본 `/opt/pw-browsers/chromium`. 다르면 `CHROMIUM_PATH=...`.

## 실행
앱을 local 프로파일로 띄운다(`./scripts/run-local.sh`). dev-seed 계정 `tutor` / `admin1234` 와 그 학원을 쓴다.
시나리오는 "초안·게시본이 비어 있고 renderMode가 template" 상태에서 시작해야 한다. 로컬 DB 초기화(로컬 전용!):
```sql
DELETE FROM academy_visual_editor_draft;
DELETE FROM academy_visual_editor_published;
UPDATE academies SET page_settings = JSON_REMOVE(page_settings, '$.builderDraft."homepage.renderMode"')
 WHERE tutor_user_id = (SELECT user_id FROM users WHERE login_id = 'tutor') AND page_settings IS NOT NULL;
```
```bash
npm run e2e            # BASE_URL=http://localhost:8080 기본. 실패가 있으면 종료 코드 1
npm run capture-fixture   # GrapesJS 업그레이드 시 서버 테스트용 fixture 재생성(서버 불필요)
```
결과: 콘솔 ✔/✘, `artifacts/e2e-result.json`, `artifacts/*.png`(스크린샷).

## 검증 항목
빈 상태·기본 패널 제거 · 변경 없는 페이지의 자동저장/이탈 경고 없음 · 프리셋 5종 · 블록 클릭 추가 · 기존 홈페이지 불러오기 ·
텍스트/스타일/이미지 교체/삭제 · 수동 저장·새로고침 유지 · 자동저장(30초) · 재불러오기 후 재저장 ·
미리보기 PC/태블릿/모바일(실제 375px) · 캔버스↔미리보기↔공개 페이지(PC/모바일)의 요소별 스타일 일치 ·
편집기 CSS가 P0 GNB/푸터로 새지 않음(Shadow DOM) · 게시/게시 취소 · 모바일 편집기(툴바·터치 추가·미리보기).
