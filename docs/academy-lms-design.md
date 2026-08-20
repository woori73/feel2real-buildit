# 학원 온라인 LMS 설계 노트

> 대상: 학원별 온라인 강좌 판매·수강 화면(`{handle}.feel2real.co.kr/lms/**`) 전체.
> 성격: 설계 결정 + **열린 결정** 기록. 계약 상세는 [API.md](API.md), 도메인은 [DOMAIN.md](DOMAIN.md), 플로우는 [FLOWS.md](FLOWS.md), 서브도메인 라우팅은 [MVP.md](MVP.md).
> 배경: 목업 `TalkFile_LMS_V3.html`(단일 파일 1087줄, 압구정 에듀홀릭 온라인 LMS)을 우리 서비스에 이식하는 작업.
> 원칙: **레이아웃을 먼저 올리고 백엔드는 단계적으로 연결한다.**

---

## 0. 최종 방향 — LMS가 `/tutor/academy`를 대체한다

**이 LMS는 별도 기능이 아니라 강사용 학원 관리 화면(`/tutor/academy`)의 후계다.** 지금은 두 화면이
공존하지만, 최종적으로 강사의 진입점은 LMS 하나가 된다.

이 전제가 단계별 설계를 좌우한다.

- LMS의 **강좌관리**는 장기적으로 `/tutor/academy`의 **수업 상품** 섹션을 흡수한다. 따라서 2단계에서
  영상강좌 도메인을 정할 때(§6) "`LessonProduct`를 확장할 것인가"는 단순 재사용 문제가 아니라
  **두 화면을 하나로 합칠 수 있는가**의 문제다.
- 그래서 1단계 강좌관리 화면의 컬럼(강좌명·학교·학년·가격·소개)은 목업을 그대로 옮긴 임시 형태이며,
  `LessonProduct`의 실제 필드와 맞추는 작업이 이행의 핵심이 된다.
- 이행은 한 번에 하지 않는다. `/tutor/academy`에서 수업 상품 섹션을 떼고 LMS로 보내는 링크만 남기는
  식으로 진입점부터 옮긴다.

### 0-1. 1차 이관 완료 상태

| 기능 | 이관 전 위치 | 현재 위치 |
|---|---|---|
| 강좌(수업 상품) 등록 | `/tutor/academy` "수업 상품 생성 +" | **LMS 강좌관리 "+ 강좌 등록"** → `/tutor/academy/lessons/editor` (같은 편집기) |
| 강좌 수정·삭제 | `/tutor/academy` 카드 ⋯메뉴 | 수업 상품 전용 편집기 (`lesson_product_editor.js`) |
| 신청·수강생 관리, 수업 커뮤니티 | `/tutor/academy` 카드 클릭 | **`/tutor/academy` "수업 운영" 목록** (그대로 남김) |

⚠ **"수업 운영" 목록을 지우면 `/tutor/lesson/{id}`가 고아가 된다.** 전체 코드베이스에서 그 화면으로
가는 링크는 이것 하나뿐이다. LMS 강좌관리 표가 `LessonProduct` 실데이터를 갖기 전까지 제거 금지.

✅ **Stage 2a에서 LMS 강좌관리 표를 `LessonProduct` 실데이터로 전환했다.** 편집기에서 저장한
DRAFT/PUBLISHED 수업은 owner 전용 표에 나타나고 기존 hide API로만 삭제한다. 후기 탭과 나머지 LMS
화면은 계속 MOCK이며 영상강좌 도메인 결정은 여전히 차단 상태다.

---

## 1. 단계 구분 (확정)

전체를 한 번에 만들지 않는다. 붙일 백엔드가 있는지에 따라 3단계로 나눈다.

| 단계 | 범위 | 데이터 | 상태 |
|---|---|---|---|
| **1단계** | 8개 화면 SSR + 레이아웃 셸 + 권한 | **JS MOCK 상수** | 진행 중 |
| **2a단계** | `LessonProduct` 학교·학년·가격 authoring + owner 강좌관리 read/delete | 강좌관리만 실 DB | **완료** |
| 2b단계 | `AcademyVideo`와 영상강좌 모델 확정 후 콘텐츠/영상 연결 | 미정 | 미착수 |
| 3단계 | 커머스(장바구니·결제·교재배송) + 수강후기 = **신규 도메인** | 실 DB | 미착수 |

**1단계의 성격**: "데이터는 가짜, 인증·권한은 진짜"인 클릭 가능한 프로토타입.
DB 스키마 변경 0 · Flyway 0 · 신규 REST API 0을 지킨다. 화면과 권한 경계를 먼저 확정하는 것이 목적이다.

> 커머스·후기 4개 화면은 2단계에서도 붙일 백엔드가 없어 목업 상태로 남는다. 전체 그림을 먼저 눈으로 보는 것을 우선한 결과이며, 의도된 선택이다.

---

## 2. URL 체계 (확정)

| 환경 | URL | 학원 식별 |
|---|---|---|
| **prd** | `{handle}.feel2real.co.kr/lms/{feature}` | Host 서브도메인 |
| **local** | `localhost:8080/lms/{handle}/{feature}` | 경로 2번째 세그먼트 |

`{feature}` = `(빈값=강좌목록) | courses/{courseId} | cart | checkout | my | lecture/{courseId} | manage | contents`

로컬은 `app.public.base-domain`이 비어 있어 서브도메인 라우팅이 동작하지 않으므로(`PublicSiteProperties.hasBaseDomain()`), 경로형 대체 주소를 둔다.

### 2-1. 식별자는 `handle`이지 `slug`가 아니다

`Academy`는 식별자를 두 개 가진다.

| 필드 | 성격 | LMS |
|---|---|---|
| `slug` | 한글 허용, 내부 식별, `/academies/{slug}` 경로형 | ❌ 서브도메인 불가 |
| `handle` | 영문 전용(`^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$`), 서브도메인 라우팅 전용 | ✅ **이걸 쓴다** |

`handle`은 nullable이다(V926 — 전환기 동안 기존 Academy는 경로형 유지). **handle이 없는 학원은 LMS 주소가 성립하지 않는다.**

`HandlePolicy`의 예약어 목록에 **`lms`를 추가한다**. `lms.{base-domain}`이 특정 학원에 선점되면 향후 플랫폼 차원의 `lms.` 서브도메인을 쓸 수 없게 된다.

### 2-2. `/academy`도 `/academies/{slug}/lms`도 쓰지 않는다

검토 후 기각했다.

- `/academy`(단수)는 **등록된 라우트가 아니다**. `docs/API.md`에 남아 있던 행은 stale이므로 삭제한다.
- `/academies/{slug}/lms`(경로형)는 기존 계열과는 일관되지만, 학원 공개 페이지도 서브도메인 방향을 함께 고려하므로 LMS만 경로형으로 두면 이행 방향과 어긋난다.

---

## 3. 서브도메인 요청 처리 — forward가 아니라 request wrapping

**이 절이 이 문서에서 가장 중요하다.** 잘못 구현하면 인가가 조용히 사라진다.

### 3-1. pass-through 없이는 prd에서 깨진다

`SubdomainAcademyFilter`는 서브도메인 요청 중 path가 `/`가 아니면, `PASS_THROUGH_PREFIXES`(`/api/`, `/css/`, `/js/`, `/img/`, `/webjars/`, `/subdomain-home`)에 해당하지 않는 한 **apex로 301**한다. 공통 페이지 경로 앞에 학원 식별자가 붙는 것을 막기 위한 장치다.

```
edu-holic.feel2real.co.kr/lms/my  →  301  →  feel2real.co.kr/lms/my   (미존재)
```

→ **`PASS_THROUGH_PREFIXES`에 `/lms`를 추가해야 한다.** 이것 없이는 prd에서 LMS가 한 화면도 뜨지 않는다.

### 3-2. forward는 인가를 통째로 우회한다 (채택 금지)

prd와 local의 path 형태가 다르므로(`/lms/my` vs `/lms/edu-holic/my`) 어딘가에서 하나로 맞춰야 한다.
"필터가 `/lms/**`를 로컬 형태로 **forward**한다"가 자연스러워 보이지만 **채택하면 안 된다.**

- `SubdomainAcademyFilter`는 `UsernamePasswordAuthenticationFilter` **앞**에 등록된다. `JwtAuthenticationFilter`조차 그보다 뒤다.
- Spring Security의 `AuthorizationFilter`는 체인 **맨 끝**에서 돈다.
- `forward()`를 부르면 **현재 체인의 나머지 필터가 전부 실행되지 않는다.**
- FORWARD dispatch는 시큐리티 체인에 **재진입하지 않는다** — Spring Boot는 `springSecurityFilterChain`을 REQUEST/ERROR/ASYNC에만 등록한다. (`SecurityConfig`가 `DispatcherType.ASYNC`를 별도 처리하는 것이 방증이다. ASYNC는 체인을 타므로 예외 처리가 필요했고, FORWARD는 애초에 타지 않는다.)

**결과**: prd에서 `/lms/cart`·`/lms/my`·`/lms/lecture/{id}`가 인증·인가 없이 렌더된다. JWT 파싱조차 되지 않는다.

> 기존 `/` → `/subdomain-home` forward가 안전한 것은 대상이 공개 페이지이기 때문이다. 보호 화면이 있는 LMS로 확장하는 순간 이 전제가 무너진다.

### 3-3. 채택: `HttpServletRequestWrapper`로 경로만 정규화

```
요청   edu-holic.feel2real.co.kr/lms/my
필터   handle="edu-holic" 추출
       → getRequestURI()/getServletPath()가 "/lms/edu-holic/my"를 반환하는 wrapper 생성
       → filterChain.doFilter(wrapped, response)      ← 체인 계속 (forward 아님)
체인   JwtAuthenticationFilter → … → AuthorizationFilter 정상 실행
       인가 평가 대상 = "/lms/edu-holic/my"
컨트롤러 @GetMapping("/lms/{handle}/my")               ← 매핑 한 벌
```

브라우저 주소창은 원래 URL을 유지한다.

**이점**
1. 인가가 정상 수행된다.
2. 인가 평가 경로가 **항상 로컬 형태 하나로 정규화**되므로, SecurityConfig 매처도 컨트롤러 매핑도 **한 벌**이면 된다.

**⚠ 구현 시 함정**: Spring 6+/Security 7은 파싱된 경로를 request attribute(`ServletRequestPathUtils.PATH_ATTRIBUTE`)로 캐시한다. wrapping 후 이 캐시를 갱신하지 않으면 `PathPatternRequestMatcher`가 **원본 경로로 매칭**한다. 실패가 조용하므로(에러 없이 인가만 어긋남) 통합 테스트로 못 박는다.

---

## 4. 권한 (확정)

| 화면 | prd path | 접근 |
|---|---|---|
| 강좌목록 | `/lms` | permitAll |
| 강좌상세 | `/lms/courses/{id}` | permitAll |
| 장바구니 | `/lms/cart` | authenticated |
| 결제 | `/lms/checkout` | authenticated |
| 내 강의실 | `/lms/my` | authenticated |
| 강의보기 | `/lms/lecture/{id}` | authenticated |
| 강좌관리 | `/lms/manage` | **학원 owner** |
| 콘텐츠관리 | `/lms/contents` | **학원 owner** |

**학생(STUDENT)은 앞 6개에 전부 접근한다.** 운영자 화면 2개만 owner 전용이다.

SecurityConfig 매처는 정규화된 로컬 형태(`/lms/*/…`) **한 벌만** 작성한다. prd 형태로 중복 작성하면 정규화 이후 절대 매칭되지 않는 죽은 줄이 된다.

### 4-1. owner 판정은 role이 아니라 소유권

`hasRole("TUTOR")`는 **role만** 표현하며 "이 학원의 소유자"를 표현하지 못한다. SecurityConfig는 익명·STUDENT를 걸러내는 1차 방어까지만 담당하고, **slug/handle 기준 소유권 검증은 컨트롤러에서** 한다.

⚠ 기존 `AcademyService.requireMyAcademy(principal)`를 **재사용하면 안 된다.** 이 메서드는 요청 경로의 학원을 보지 않고 "로그인한 강사 본인의 학원"을 찾는다. 그대로 쓰면 **학원 B의 URL로 들어온 학원 A 강사에게 A의 화면을 렌더**하는 조용한 오답이 된다. handle 기준 판을 별도로 둔다.

### 4-2. 사이드바 관리 탭 노출

관리 탭(강좌관리·콘텐츠관리)은 **학생에게 노출하지 않는다.**

노출 조건은 `role == 'TUTOR'`가 아니라 **해당 학원의 owner**로 건다. role만 보면 *다른 학원* 강사에게도 탭이 보이고 누르면 403이 난다. 노출은 표시일 뿐이며 실제 차단은 §4의 owner 가드가 담당한다.

### 4-3. 비로그인 사용자의 목적지

보호 화면에 익명으로 접근하면 로그인 화면으로 보내야 하는데, 현재 전역 `authenticationEntryPoint`는 빈 401을 반환한다. 이는 fetch/XHR 전제이며 주소창 top-level GET에는 맞지 않는다.

- 목적지는 `/login`이 **아니다** — 일반 로그인 페이지는 제거되었다. 로그인은 공통 모달이다.
- **목적지는 `/`(랜딩)로 확정했다.** LMS 보호 화면은 로그인 상태에서만 쓰는 화면이고, 로그아웃도 `/`로 보낸다(`static/js/account_menu.js`). 비로그인 목적지를 한 곳으로 맞춘다.
- 이건 페이지 404 정책(`PageNotFoundRedirectController` → `/home`)과 **다른 흐름**이다. 그쪽 통일은 별도 이슈(#283)로 분리했다.
- 전역 entry point를 커스텀 클래스로 통째 교체하지 **않는다.** `ExceptionHandlingConfigurer.defaultAuthenticationEntryPointFor(entryPoint, matcher)`로 LMS 경로에만 적용하고 나머지는 기존 401을 유지한다.
- ⚠ 명시적 `.authenticationEntryPoint(...)` 호출이 남아 있으면 위 매핑을 **전부 덮어쓴다.** 반드시 제거한다.

---

## 5. 클라이언트 구조 (1단계)

기존 컨벤션대로 화면당 `templates/…/{page}.html` + `static/css/{page}.css` + `static/js/{page}.js`로 쪼갠다. 목업의 SPA 뷰 전환(`showView()`)은 버린다 — 화면 전환은 서버가 한다.

### 5-1. 단계별 교체 지점을 한 곳으로 모은다

MOCK 데이터를 상수 파일에만 격리하고 각 페이지가 직접 접근하면(`LMS_MOCK.courses.filter(...)`), 2단계에서 8개 스크립트를 전부 뜯어야 한다. 게다가 실제 교체는 동기 → 비동기 전환이라 호출부가 동기면 전부 재작성이다.

**상수 파일과 접근 seam을 분리한다.**

- `lms_mock.js` — 데이터 리터럴만
- `lms_data.js` — **유일한 seam.** 모든 함수가 `Promise`를 반환한다. 페이지 스크립트는 처음부터 `await LmsData.listCourses(...)` 형태로 쓴다.

Stage 2a는 기존 MOCK 함수를 바꾸지 않고 `listManageCourses(handle)`와 `deleteManageCourse(id)`만 추가한다.
강좌관리 course tab만 두 live 함수를 사용하며 후기 탭과 다른 페이지는 `listCourses()` 등 기존 MOCK
함수를 그대로 사용한다. 보호 API 의존성 때문에 manage template은 `api.js`를 `lms_data.js`보다 먼저
로드한다.

### 5-3. Stage 2a 강좌관리 live 경계

- `GET /api/tutor/lms/{handle}/lessons`는 handle owner를 서버에서 재검증하고 DRAFT/PUBLISHED만
  `createdAt ASC, lessonId ASC`로 반환한다.
- 응답은 `lessonId`, `schoolName`, `gradeLabel`, `name`, `priceAmount`, `priceUnit`, `description`만 갖는
  최소 DTO다. 공개 API에는 학교·학년을 추가하지 않는다.
- 표는 read-only다. contentEditable/저장 버튼은 없고 `DELETE /api/tutor/academy/lessons/{id}`의 기존
  HIDDEN/409 정책만 재사용한다.
- live read/delete는 `LmsState`, cart/review, sessionStorage, MOCK 배열을 변경하지 않는다. 이 격리가
  깨지면 실제 lesson ID와 MOCK course ID가 충돌해 장바구니/후기 상태가 오염된다.

번들러가 없으므로(클래식 `<script src>`) ES module이 아니라 IIFE + `window` 전역으로 노출하고, 로드 순서는 shell fragment의 `<script>` 태그 순서로 강제한다.

### 5-2. 페이지 간 상태

목업은 단일 페이지라 `let cart = []`로 충분했다. SSR로 URL을 쪼갠 순간 이것이 사라진다. 서버 세션은 `SessionCreationPolicy.STATELESS`라 애초에 불가능하고, 1단계는 신규 API를 만들지 않는다.

→ `sessionStorage`를 쓰되 **접근 창구를 `lms_state.js` 하나로 묶는다.** 탭 종료 시 소멸하는 성질이 "이건 가짜 데이터"라는 성격과 맞는다(localStorage는 세션을 넘겨 살아남아 2단계에서 유령 데이터를 만든다).

키는 `lms:{handle}:{userKey}:{name}` 형태로 네임스페이싱한다. `sessionStorage`는 origin+tab 단위지 **사용자 단위가 아니어서**, 같은 탭에서 학생 A 로그아웃 → B 로그인 시 B가 A의 장바구니를 보게 된다.

⚠ 공통 `api.js`는 보호 API가 401을 반환하면 `sessionStorage.clear()`를 호출한다. 네임스페이싱과 무관하게 LMS 상태도 함께 증발한다. 1단계는 API 호출이 없어 노출이 좁지만, **3단계 커머스는 이 구조를 그대로 복사하면 안 된다** — 서버 장바구니로 대체할 것.

---

## 6. 열린 결정 (미정 — 확정 금지)

- **[2b단계 진입 차단] 영상강좌의 도메인 모델.** Stage 2a는 기존 `LessonProduct`의 metadata/read/delete만 연결했으며 영상 관계를 만들지 않았다. `LessonProduct`를 영상강좌로 확장할지, 신규 `Course` 엔티티를 만들지, `AcademyVideo` 중간 테이블을 둘지 결정하기 전에는 콘텐츠/영상 실연결을 시작하지 않는다.
- **3단계 커머스의 기반.** `LessonApplication`(신청 개념만 있고 결제 개념 없음)을 확장할 것인가, 별도 `Order` 도메인을 세울 것인가. 학원별 정산·PG 가맹 구조가 여기에 딸린다.
- **handle 백필.** 현재 handle 보유 학원이 매우 적다. LMS를 일반 학원에 열려면 전 Academy handle 백필이 선행되어야 한다(V926 주석의 "retire 전제조건").
- **로그인 후 복귀.** 현재 로그인은 `/home`으로만 보내며 `?next=` 지원이 없다. LMS 보호 화면에서 튕긴 사용자를 원래 화면으로 되돌릴 것인가.
- **경로 명명 통일.** 학원 공개 페이지는 `/academies/{slug}`(경로형), LMS는 서브도메인이다. 서브도메인 이행이 끝나면 경로형을 retire할 것인가.

---

## 7. 검증 대상 학원

1단계 **로컬** 검증은 **`edu-holic`** 으로 한다.

- `V928__add_edu_holic_demo_academy.sql`이 만드는 데모 학원이고, 로컬/dev에서는 Flyway가 항상 재생성한다.
- dev-seed 학원은 `handle`이 없어 LMS 주소가 성립하지 않는다. dev-seed에 handle을 새로 부여할 필요는 없다 → **dev-seed 수정 0, Flyway 0 유지.**
- owner 화면 검증은 `login_id = 'edu-holic'` 계정으로 로그인한다.

> ### ⚠ `edu-holic`은 운영(prd)에 더 이상 없다
>
> 빌더/발행 홈페이지의 히어로·학원설명 섹션 개편 과정에서 **운영 DB의 edu-holic 더미 학원을 수동 삭제했다.**
> `V928`은 이미 적용된 V_파일이라 그대로 남아 있고 삭제 마이그레이션은 없다 → **코드와 운영 DB가 갈라진 상태다.**
>
> **LMS에 미치는 영향:** handle을 부여하는 곳은 `V926`(컬럼 추가)과 `V928`(edu-holic)뿐이다.
> 따라서 `/tutor/academy/setup`에서 직접 handle을 넣은 학원이 없다면 **prd에는 handle 보유 학원이 0개이고,
> 1단계 LMS는 prd에서 도달 가능한 URL이 하나도 없다.** prd 확인 후 대상 학원을 정해야 한다.
>
> 코드/운영 DB 정합은 별도 이슈(#284)로 분리했다. 이 문서에서는 "edu-holic = 로컬 전용"으로만 취급한다.

---

## 8. 1단계 구현 결과 (완료)

라우트·권한 계약의 정본은 [API.md §2.1](API.md#21-학원-온라인-lms-lmshandle)이다. 여기엔 파일 지도만 둔다.

| 층 | 파일 |
|---|---|
| 인가 매처 · entry point 분기 | `config/SecurityConfig.java` |
| 서브도메인 경로 정규화 | `config/SubdomainAcademyFilter.java` (`normalizeLmsPath`) |
| handle 예약어 | `domain/academy/service/HandlePolicy.java` |
| handle 조회 2종 | `domain/academy/service/AcademyService.java` (`requireOwnedAcademyByHandle` / `getPublishedEntityByHandle`) |
| 컨트롤러 | `domain/academy/controller/AcademyLms{,Manage}PageController.java` + `AcademyLmsShellModel.java` |
| 템플릿 | `templates/fragments/_lms_shell.html` + `templates/lms/lms_*.html` (8) |
| 정적 자원 | `static/css/lms_*.css` (9) · `static/js/lms_*.js` (13) |

**회귀 방지선 3개** — 지우면 결함이 조용해진다.

1. `SubdomainLmsPathSecurityIntegrationTest` (T-SUB1~7) — `ServletRequestPathUtils.parseAndCache` 한 줄을
   빼면 T-SUB1~4가 red가 되는 것을 mutation으로 확인했다. 이 줄이 없으면 인가가 원본 경로 기준으로
   조용히 평가된다.
2. `AcademyLmsSecurityIntegrationTest` — 권한 매트릭스 8행 + "다른 학원 TUTOR는 403" + fail-closed.
3. `AcademyLmsManagePageControllerTest` — owner 컨트롤러의 클래스 불변식(모든 handler가 소유권 가드로
   시작한다)을 필터를 끈 상태로 검증한다.

**Stage 2a 이후 남긴 것(의도):** 강좌관리 course tab만 실제 `LessonProduct`를 읽고 hide한다. 공개 강좌,
강좌상세, 영상, 장바구니, 결제, 내 강의실, 강의보기, 콘텐츠관리, 후기 데이터는 계속 MOCK이다. §6의
영상 도메인 결정 전에는 `AcademyVideo`/course-video linkage나 다른 LMS live API를 추가하지 않는다.
