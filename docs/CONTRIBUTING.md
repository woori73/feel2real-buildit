# 이슈 / PR 작성 형식 (Issue & PR Format)

> 모든 에이전트(OMC/Claude, OMX/Codex)와 사람은 GitHub 이슈·PR을 **아래 형식 그대로** 작성한다.
> 공통: 한글 본문, 마케팅 문구·이모지 헤더·거대 체크리스트 금지, 가능하면 한 화면. 사실 위주.

## GitHub Issue

`gh issue create`로 생성.

- **제목**: 타입 prefix + 간결(마침표 없음).
  - `[FEAT]` 새 기능 · `[BUG]` 버그 · `[CHORE]` 잡무/인프라 · `[REFACTOR]` 리팩토링 · `[PROCESS]` 프로세스/규칙 · `[CI]` CI/CD
  - prefix는 Trello 라벨로 자동 매핑된다 (issue-to-trello 워크플로): FEAT→feature, BUG→fix, CHORE→chore, REFACTOR→refactor, PROCESS→process, CI→CI/CD.
- **본문 섹션** (내용 없으면 해당 섹션만 생략):

```markdown
## 목표
<무엇을 / 왜 — 한 문단>

## 범위
- <구현/변경 항목 1>
- <구현/변경 항목 2>

## 버그 픽스
<있으면: 고친 버그 + 증상>

## 향후 (별도)
- <이번 범위 밖, 후속으로 분리할 것>

## 관련 PR
#NN
```

## Pull Request

`gh pr create --base main --head <branch>`로 생성. main은 **PR로만** 머지(직접 push 금지).

- **제목**: 이슈와 동일한 타입 prefix + 간결.
- **본문 섹션**:

```markdown
## 목표
<이 PR이 무엇을 / 왜 — 한 문단>

## 범위
- <변경 항목 1>
- <변경 항목 2>

## 검증
- <빌드/테스트/부팅 등 실제로 돌린 것 + 결과>

## 향후 (별도)
- <후속 작업>

## 관련
Closes #NN   <!-- 이슈 자동 닫기 -->
```

- **검증 섹션 필수**: `direnv exec . ./gradlew build` 결과, stg 부팅 결과 등 실제 돌린 근거를 적는다(없으면 "검증 안 함"과 사유).
- DB/마이그레이션 변경 PR은 `docs/WORKFLOW.md` PR 전 체크리스트(§3)를 따른다.
- 미해결/한 PR에 못 담은 작업은 "향후 (별도)"에 명시한다.
