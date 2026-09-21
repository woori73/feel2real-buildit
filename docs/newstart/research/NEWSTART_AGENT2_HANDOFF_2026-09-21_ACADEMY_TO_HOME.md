# NEWSTART AGENT2 HANDOFF — 2026-09-21 학원 PC → 집 PC

> 이 문서는 Agent2(부실차주 Wiki / NewStart Official Knowledge Layer 담당) 작업의 현재 상태를 집 PC에서 안전하게 이어갈 수 있도록 보존합니다. **production code는 push하지 않습니다.** 이 문서만 별도 docs branch에 commit/push합니다.

---

## 1. 기본 식별자

| 항목 | 값 |
|------|-----|
| Agent2 branch | `feat/newstart-official-knowledge-phase2` |
| PR #353 | https://github.com/woori73/feel2real-buildit/pull/353 |
| Agent2 원래 HEAD SHA | `16c93ae9f83a42e0fddcbdb7ab9d61b1088df625` |
| 당시 merge 대상 main SHA | `a4c508a0222afa18c3eab5aebb1ed194435ac8fa` |
| 현재 최신 main SHA (PR #355 병합 후) | `22b2922a90b70811ff094a605cae69ac50a9d229` |

---

## 2. 현재 작업트리 상태 (학원 PC)

```text
branch: feat/newstart-official-knowledge-phase2
HEAD: 16c93ae9f83a42e0fddcbdb7ab9d61b1088df625 (미커밋 merge resolving 상태)
unmerged files: 3 (SecurityConfig.java, WebMvcConfig.java, _admin_topnav.html)
conflict markers: 0개 (전부 수동 해결 완료, 커밋만 대기 중)
modified (merge가 가져온 main 변경): .env.example, .github/workflows/*, .gitignore 등 다수
```

> **중요**: `git diff --name-only --diff-filter=U`는 여전히 3파일을 보여주지만, conflict marker(`<<<<<<<`, `=======`, `>>>>>>>`)는 모두 제거된 상태입니다. merge commit만 하면 되는 상태입니다.

---

## 3. 해결한 Conflict 3건 — 정확한 Resolution

### 3.1 SecurityConfig.java (lines 173-180)

**HEAD (Agent2)**: `/admin/newstart/wiki` 단일 경로 ADMIN 보호
**main (origin/main)**: `/admin/newstart/**` 전체 + `/easy/**` ADMIN 보호

**Resolution**: **main 블록 채택**. Agent2가 추가한 `/admin/newstart/wiki`는 main의 `/admin/newstart/**` 상위 규칙에 흡수됨. 주석 1줄로 명시.

```java
// 랜딩 편집 화면은 기존 public admin view 그룹보다 먼저 차단한다.
.requestMatchers("/admin/landing", "/admin/chart").hasRole("ADMIN")
// Easy 회계·학생원장 SSR 화면 — ADMIN 전용. 데이터는 /api/admin/easy/** (ROLE_ADMIN).
.requestMatchers("/easy", "/easy/**").hasRole("ADMIN")
// NewStart 운영자 화면 — ADMIN 전용. 메뉴 숨김이 아니라 서버 규칙으로 차단한다.
// 반드시 아래 catch-all permitAll의 /admin/** 나열보다 위에 둔다(first-match-wins).
// /admin/newstart/wiki(부실차주 Wiki 검토 화면)도 이 규칙으로 보호된다.
.requestMatchers("/admin/newstart", "/admin/newstart/**").hasRole("ADMIN")
```

> **NAVER OAuth / chat / nickname / home UI 보안 설정은 main 그대로 보존됨** (Agent2가 SecurityConfig 다른 부분 수정 없음).

### 3.2 WebMvcConfig.java (lines 88-91)

**HEAD**: `view(registry, "/admin/newstart/wiki", "admin/newstart_wiki")`
**main**: members/analytics/landing/easy 등 신규 admin view 다수 추가

**Resolution**: **양쪽 모두 유지**. main이 추가한 행들 + Agent2의 wiki view 행을 모두 포함.

```java
view(registry, "/admin/wiki-originals", "admin/wiki_originals");
view(registry, "/admin/wiki-analysis", "admin/wiki_analysis");
view(registry, "/admin/newstart/wiki", "admin/newstart_wiki");   // Agent2
view(registry, "/admin/lesson-communities", "admin/lesson_communities");
view(registry, "/admin/landing", "admin/landing");
```

### 3.3 _admin_topnav.html (lines 27-29)

**HEAD**: 부실차주 Wiki 링크 1줄
**main**: lessonCommunities, landing, easy, newstart 링크 추가

**Resolution**: **main 링크 전부 보존 + 부실차주 Wiki 링크 재삽입** (`wiki-analysis` 다음).

```html
<a class="admin-nav-link" th:classappend="${activeMenu == 'wikiAnalysis'} ? ' active' : ''" href="/admin/wiki-analysis">분석 자료</a>
<a class="admin-nav-link" th:classappend="${activeMenu == 'newstartWiki'} ? ' active' : ''" href="/admin/newstart/wiki">부실차주 Wiki</a>
<a class="admin-nav-link" th:classappend="${activeMenu == 'lessonCommunities'} ? ' active' : ''" href="/admin/lesson-communities">커뮤니티 검색</a>
<a class="admin-nav-link" th:classappend="${activeMenu == 'landing'} ? ' active' : ''" href="/admin/landing">랜딩 페이지 관리</a>
<a class="admin-nav-link" th:classappend="${activeMenu == 'easy'} ? ' active' : ''" href="/easy">회계 Easy</a>
<a class="admin-nav-link" th:classappend="${activeMenu == 'newstart'} ? ' active' : ''" href="/admin/newstart">새출발</a>
```

---

## 4. Agent2 Knowledge Workflow 상태 (Production Ready)

| 기능 | 상태 | 비고 |
|------|------|------|
| ACTIVE-only retrieval | ✅ 유지 | `NewstartKnowledgeRetrievalService`는 `status=ACTIVE`만 조회 |
| generateKnowledgeFromCases | ✅ REVIEW | 자동 ACTIVE 금지, DERIVED_RULE은 REVIEW로만 생성 |
| update() direct ACTIVE 차단 | ✅ 구현 | `IllegalStateException`으로 직행 차단, 승인 API 경유 강제 |
| REVIEW approval workflow | ✅ 5개 엔드포인트 | list/detail/approve/keep/archive/history |
| DELETE = ARCHIVED | ✅ 구현 | provenance 보존, 하드 삭제 금지 |
| 10/31일 충돌 자동 해결 금지 | ✅ 유지 | CONFLICT_RISK 경고로만 처리, 상태 변경 없음 |
| Gap logic | ✅ untouched | 기존 로직 그대로 |
| provenance 보존 | ✅ 구현 | 모든 상태 변경 `newstart_knowledge_status_log` 기록 |
| 단위 테스트 | **15/15 PASS** | `NewstartKnowledgeReviewWorkflowTest` |
| compileJava | **PASS** | errors 0 |
| bootJar | **PASS** | 빌드 성공 |

---

## 5. Migration 버전 현황 — 핵심 이슈

### Agent2 branch (작업트리)
```
V976__add_newstart_knowledge_wiki.sql   ← Agent2 Knowledge 스키마
```

### latest main (origin/main, 22b2922a)
```
V983__add_easy_collection_foundation.sql
V984__add_newstart_member_profiles.sql
V985__add_newstart_knowledge_wiki.sql   ← Agent3 #355 Knowledge 스키마 (이미 main에 병합됨)
```

### V976 vs V985 비교 (핵심)
두 파일이 **거의 동일**한 `CREATE TABLE newstart_knowledge_items` DDL을 가짐.
- V976: Agent2가 작성 (source_type enum에 OFFICIAL|AUDIT|EBOOK|CAFE|CASE|RULE)
- V985: Agent3가 작성 (관련 컬럼 `related_case_ids`, `related_rule_ids`, `created_by`, `updated_by`, `created_at`, `updated_at` 추가 + 인덱스 보강)

> **충돌 타입**: Flyway version 중복(V976) + 동일 테이블 DDL 중복

---

## 6. 다음 작업 (집 PC에서 수행)

### 6.1 필수: V985와 V976 실제 SQL 단위 비교
```bash
# 집 PC에서
git fetch origin
git show origin/main:src/main/resources/db/migration/V985__add_newstart_knowledge_wiki.sql > /tmp/V985.sql
cat src/main/resources/db/migration/V976__add_newstart_knowledge_wiki.sql > /tmp/V976.sql
diff -u /tmp/V985.sql /tmp/V976.sql
```

### 6.2 Decision Matrix (단순 rename 금지)

| 상황 | 액션 |
|------|------|
| V985가 V976 DDL을 완전히 포함(동일+추가 컬럼/인덱스) | Agent2 V976 **제거** 검토 (duplicate migration 해소) |
| Agent2 workflow가 V985에 없는 추가 스키마 필요 | 차이만 **별도 V986 migration** 후보로 생성 |
| Destructive/중복 DDL (테이블 재생성 등) | **STOP 후 보고** — director 결정 대기 |

### 6.3 예상 Conflict: Agent2 #353 vs Agent3 #355
| 영역 | main(V985 포함) | Agent2 branch | 비고 |
|------|----------------|---------------|------|
| `newstartknowledge` domain | **존재함** (controller/dto/entity/repository/service 전체) | **동일 패키지 구조** | 클래스명·필드명 차이 비교 필요 |
| `V985` migration | **이미 main에 있음** | `V976` (중복 버전) | 버전·내용 충돌 |
| `SecurityConfig` | `/admin/newstart/**` 보호 | 흡수됨 (주석만 남김) | 완료 |
| `WebMvcConfig` | admin view 다수 | wiki view 추가됨 | 완료 |
| `_admin_topnav` | newstart/easy/landing 링크 | Wiki 링크 추가됨 | 완료 |

> **주의**: main에는 이미 `newstartknowledge` 도메인 코드 전체가 merge됨(PR #355). Agent2 PR #353을 push하면 **동일 패키지·클래스 중복**으로 대규모 conflict 발생 예상.

---

## 7. House PC에서 재현 절차

```bash
# 1. repo clone
git clone https://github.com/woori73/feel2real-buildit.git
cd feel2real-buildit

# 2. Agent2 branch checkout
git fetch origin --prune
git checkout feat/newstart-official-knowledge-phase2

# 3. 현재 HEAD 확인 (16c93ae여야 함)
git rev-parse HEAD

# 4. merge 대상 main과 동기화
git merge origin/main  # conflict 3건 발생 → 이 문서의 Resolution대로 수동 해결

# 5. 검증
./gradlew compileJava
./gradlew test --tests "f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartKnowledgeReviewWorkflowTest"
./gradlew bootJar

# 6. V976 vs V985 비교 → Decision
```

---

## 8. 보존하지 않은 것 (Agent2 범위 외)

- ❌ Agent3 V985 migration 수정/가져오기
- ❌ PR #353에 미완성 merge push
- ❌ V976 renumber/delete/modify
- ❌ EASY / NAVER OAuth / 홈 UI / 시험지 / Chart / Academy 수정
- ❌ 전체 테스트 스위트 실행 (Academy/Chart OUT OF SCOPE)

---

## 9. Handoff Branch Push (이 문서만)

```bash
git checkout -b docs/newstart-agent2-handoff-20260921-academy
git add docs/newstart/research/NEWSTART_AGENT2_HANDOFF_2026-09-21_ACADEMY_TO_HOME.md
git commit -m "docs(handoff): Agent2 NewStart Knowledge Phase2 handoff 2026-09-21"
git push origin docs/newstart-agent2-handoff-20260921-academy
```

---

## 10. 최종 상태 요약

| 항목 | 상태 |
|------|------|
| HANDOFF BRANCH | `docs/newstart-agent2-handoff-20260921-academy` |
| HANDOFF FILE | `docs/newstart/research/NEWSTART_AGENT2_HANDOFF_2026-09-21_ACADEMY_TO_HOME.md` |
| PUSH | 수행 예정 (이 문서만) |
| PR #353 UNCHANGED | **YES** (production code push 안 함) |
| PRODUCTION CODE PUSHED | **NO** |
| MIGRATION MODIFIED | **NO** (V976 그대로) |
| LATEST MAIN RECORDED | `22b2922a90b70811ff094a605cae69ac50a9d229` |
| MERGE | **NO** |
| DEPLOY | **NO** |
| PR #353 PUSH | **NO** |

---

> **STOP HERE**. 집 PC에서 위 절차대로 재현 후 V976/V985 비교부터 시작하십시오.