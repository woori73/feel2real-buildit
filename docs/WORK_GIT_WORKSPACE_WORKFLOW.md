# Work / Codex 세션 Git 작업환경 워크플로 (Workspace Workflow)

> ChatGPT Work / Codex 세션에서 이 저장소를 열 때 **가장 먼저** 읽는다.
> 목적: "로컬 checkout의 `origin/main`이 최신"이라는 잘못된 가정 때문에
> 이미 병합된 PR을 못 보고 낡은 base 위에서 작업하는 사고를 막는다.
> ⚠ 이 문서가 구두 지시·에이전트 메모리와 충돌하면 **이 문서가 우선**한다.

---

## 0. 왜 이 문서가 필요한가 (실제 사고)

- 어떤 세션에서 GitHub 최신 `main`은 **PR #52 / SHA `3016752b656cda71b8aece602e89a25340eb0ea1`**
  까지 병합되어 있었고, 해당 SHA의 Build Check와 `Deploy to EC2 #69`가 모두 성공한 상태였다.
- 그러나 그 세션의 로컬 checkout `origin/main`은 **PR #48~49 수준**으로 stale했다.
- 게다가 그 세션에서 `git fetch origin --prune` 와 새 `git clone` 이
  Windows Git 자격증명 오류 **`fatal: ... SEC_E_NO_CREDENTIALS`** 로 실패했다.
- 그 결과 "저장소가 사라졌다 / GitHub 장애다 / 최신 코드가 없다"고 **단정**하고
  낡은 로컬 상태를 authoritative로 취급할 위험이 있었다.

### 핵심 원인

**PC 한 대 안에 이 저장소의 로컬 checkout이 여러 벌 존재한다.**
ChatGPT Work / Codex는 세션(작업 폴더)마다 별도 clone 또는 별도 작업 트리를 쓰고,
각 checkout의 **remote-tracking ref(`origin/main` 등)와 local 브랜치가 서로 다르게 stale** 하다.

- `~/Documents/feel2real-buildit` (주 checkout)
- `~/Documents/Codex/<날짜>/<주제>/work/feel2real-buildit` (세션별 작업 트리, 다수)
- 그 외 세션이 만든 임시 clone

→ **"내가 지금 보고 있는 `origin/main`"은 그 checkout이 마지막으로 `fetch` 한 시점의 스냅샷일 뿐이다.**
→ 다른 세션/다른 폴더가 더 최근에 fetch했다고 해서 내 checkout이 따라가지 않는다.

---

## 1. Authoritative source = GitHub 최신 `main`

우선순위는 항상 다음과 같다.

1. **GitHub `woori73/feel2real-buildit` 의 `main` 브랜치 tip** ← 유일한 정답
2. 그 SHA에 대한 GitHub Actions(Build Check / Deploy to EC2) 결과
3. 로컬 checkout의 `origin/main` (2차 캐시, 항상 의심)
4. 로컬 feature 브랜치 / 작업 트리 (가장 신뢰 낮음)

로컬 상태가 3·4에서 1과 다르면 **로컬이 틀린 것**이다. 그 반대로 가정하지 않는다.

---

## 2. 작업 시작 시 절차 (매 세션 필수)

### 2-1. 기존 repo 먼저 탐색 (새로 clone 하기 전에)

새 clone 전에 **이미 있는 checkout을 찾는다.**

```bash
ls -d ~/Documents/feel2real-buildit 2>/dev/null
find ~/Documents -maxdepth 6 -type d -name .git -path '*feel2real-buildit*' 2>/dev/null
```

발견되면 그 디렉터리에서 작업한다. 이유 없이 새 clone을 만들지 않는다
(새 clone이 늘어날수록 이 문서가 막으려는 stale-checkout 혼란이 커진다).

### 2-2. 현재 상태 확인 (`git status` / remote / branch / HEAD)

```bash
cd <repo>
git rev-parse --show-toplevel     # 지금 어느 checkout인지
git status                         # 브랜치, 클린/더티
git remote -v                      # origin = https://github.com/woori73/feel2real-buildit.git 인지
git branch --show-current
git rev-parse HEAD
git rev-parse origin/main          # ← fetch 전 값. "지금 캐시"임을 인지
git log --oneline -5 origin/main
```

### 2-3. `git fetch origin --prune`

```bash
git fetch origin --prune
```

`--prune`로 원격에서 삭제된 브랜치 ref도 정리한다.

### 2-4. fetch 후 `origin/main` SHA와 최근 merge 확인

```bash
git rev-parse origin/main
git log --oneline -15 origin/main
git log --merges --oneline -10 origin/main   # 최근 병합된 PR 확인
```

- fetch **후** `origin/main`이 실제 최신인지 판단한다.
- 최근 커밋 제목의 `(#NN)` PR 번호가 GitHub의 최신 병합 PR 번호와 일치하는지 확인한다.

### 2-5. stale `origin/main`을 최신이라고 가정하지 않기

- `git fetch`가 성공했어도, **네트워크/자격증명 문제로 조용히 실패**했을 수 있다.
  fetch 명령의 종료 코드와 출력(`From https://github.com/...`, `<old>..<new> main -> origin/main`)을 확인한다.
- fetch 출력에 `main` 업데이트 줄이 없고 `origin/main` SHA가 그대로면
  → **정말 최신인지**를 §4의 원격 교차확인으로 검증한다. "안 바뀌었으니 최신"이라고 넘기지 않는다.

### 2-6. 다른 feature 브랜치에서 새 작업 시작하지 않기

- 지금 HEAD가 남의 feature 브랜치(예: `feat/...`, `fix/...`)이면 거기서 커밋을 시작하지 않는다.
- 이전 세션이 남긴 detached HEAD / 체크아웃 상태를 그대로 base로 쓰지 않는다.

### 2-7. 최신 `main` 기준으로 새 브랜치 생성

```bash
git switch main
git merge --ff-only origin/main     # 로컬 main을 원격 tip으로 정렬 (FF 안 되면 멈추고 원인 파악)
git switch -c <type>/<topic>        # 예: feat/chart-region-registry
```

- `git merge --ff-only`가 실패하면 로컬 `main`에 예상치 못한 커밋이 있는 것 →
  reset/rebase로 강제하지 말고 원인을 먼저 조사한다.
- **기존 feature 브랜치나 운영 코드를 임의로 `reset --hard` / 삭제하지 않는다.**

---

## 3. `SEC_E_NO_CREDENTIALS` 대응

`fatal: unable to access '...': SEC_E_NO_CREDENTIALS` 또는 유사한 Windows 자격증명 오류가 나면:

### 하지 말 것

- ❌ "저장소가 소실됐다 / 삭제됐다"고 단정
- ❌ "GitHub 장애다"라고 단정
- ❌ 로컬 stale checkout을 authoritative로 승격
- ❌ 최신 코드를 못 받았다는 이유로 오래된 base에 그냥 작업 시작

### 할 것

1. **로컬 오류로 간주한다.** 이건 그 세션/셸의 Git 자격증명 헬퍼가 토큰을 못 넘긴 것이다.
2. 다른 checkout에서 fetch가 되는지 확인한다 (자격증명 상태가 폴더/세션마다 다를 수 있다).

   ```bash
   for d in $(find ~/Documents -maxdepth 6 -type d -name .git -path '*feel2real-buildit*' 2>/dev/null); do
     r=$(dirname "$d"); echo "== $r =="; git -C "$r" fetch origin --prune 2>&1 | tail -3
   done
   ```
3. **§4의 GitHub API / Actions 교차확인으로 원격 최신 상태를 파악한다** (읽기 전용, 자격증명 헬퍼와 별개 경로).
4. fetch가 가능한 checkout을 찾으면 거기서 §2-7로 브랜치를 만든다.
5. 어디서도 fetch가 안 되면, 그 사실만 보고하고 **추측으로 코드 변경을 시작하지 않는다.**

---

## 4. 원격 최신 상태 교차확인 (GitHub API / Actions)

로컬 `git fetch`와 **독립적으로** 원격 진실을 확인하는 경로.

```bash
# main tip SHA + 커밋 메시지
gh api repos/woori73/feel2real-buildit/commits/main --jq '.sha, .commit.message'

# 최근 병합된 PR과 merge SHA
gh api "repos/woori73/feel2real-buildit/pulls?state=closed&base=main&per_page=5" \
  --jq '.[] | "\(.number) \(.merged_at) \(.title) merge_sha=\(.merge_commit_sha)"'

# 특정 SHA의 Actions(빌드/배포) 결과
gh api repos/woori73/feel2real-buildit/commits/<SHA>/check-runs \
  --jq '.check_runs[] | "\(.name): \(.conclusion)"'
gh run list --repo woori73/feel2real-buildit --branch main --limit 10
```

`gh`가 없으면 `curl -H "Authorization: Bearer <token>" https://api.github.com/repos/woori73/feel2real-buildit/commits/main`.

**검증 통과 기준**: 로컬 `git rev-parse origin/main` == GitHub API가 준 `main` SHA
== 최신 병합 PR의 `merge_commit_sha`. 셋이 일치해야 "로컬이 최신"이라고 말할 수 있다.

---

## 5. Flyway 마이그레이션 — 재조사/재번호화 금지

`docs/WORKFLOW.md` §4가 단일 출처다. 이 문서는 세션 사고 방지 관점의 요약만 둔다.

- **이미 적용된(prd에 배포된) `V___*.sql` 파일은 불변이다.** 내용 수정·이름 변경·재번호화 금지
  (Flyway 체크섬 불일치 → prd 부팅 거부/502).
- stale checkout에서 봤을 때 "V번호가 비어 보인다"고 중간 번호를 채우거나 되돌리지 않는다.
  → 먼저 §2-4로 최신 `main`의 실제 마이그레이션 목록을 확인한다.
- 새 스키마 변경은 **최신 `main` 기준 최고 V번호 + 1** 로만 추가한다.
  실제 prd 적용 max는 `flyway_schema_history`에서 재확인한다.
- Legacy Chart(125 candidates / 43 verified educators) 데이터는 현행 도메인 테이블이 아니다.
  read-only exporter 계약이며 직접 수정·import·승인하지 않는다.

---

## 6. 요약 체크리스트

```
[ ] 기존 checkout 탐색 (새 clone 자제)
[ ] git status / remote -v / branch / rev-parse HEAD / rev-parse origin/main
[ ] git fetch origin --prune  ← 종료코드·출력 확인
[ ] fetch 후 origin/main SHA + git log --merges 로 최신 PR 확인
[ ] GitHub API로 main SHA / 최신 merge PR / Actions 결과 교차확인 (3개 일치?)
[ ] stale origin/main 을 최신이라 가정하지 않음
[ ] 남의 feature 브랜치 / detached HEAD 에서 작업 시작 안 함
[ ] git switch main && git merge --ff-only origin/main
[ ] 최신 main 기준 새 브랜치 생성 (기존 브랜치/운영 코드 reset·delete 금지)
[ ] SEC_E_NO_CREDENTIALS = 로컬 자격증명 문제로 간주, 소실/장애로 단정 안 함
[ ] 적용된 Flyway V_파일 수정·재번호화 안 함
```
