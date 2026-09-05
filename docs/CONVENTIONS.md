# UI/템플릿 컨벤션 (Conventions)

> Thymeleaf head·admin nav·academy blog 등 화면 공통 규칙.

## Thymeleaf page head convention

새 Thymeleaf 페이지는 공통 favicon/head fragment를 반드시 포함한다. `<head>` 안, 가능하면 `<title>` 바로 다음에 아래 한 줄을 추가한다.

```html
<th:block th:replace="~{fragments/_head :: favicon}"></th:block>
```

브라우저 탭 로고 리소스는 `src/main/resources/static/favicon.ico`, `src/main/resources/static/favicon.svg`에서 제공하고, 링크 관리는 `src/main/resources/templates/fragments/_head.html`에서 한다. 로고를 교체할 때는 브라우저 캐시 무효화를 위해 fragment의 `v=` 값을 갱신한다.

## 학원 LMS 셸 fragment convention

학원 LMS 화면(`templates/lms/*.html`)은 `fragments/_lms_shell.html`의 fragment를 조합해 만든다.
`_sidebar.html`과 같은 원칙이다 — **구조 단위 1개 = fragment 1개**. 통합 shell fragment를 만들지
않는 이유는, 여는 태그만 뱉고 닫는 태그를 페이지가 담당하면 태그 균형이 깨져 IDE·포매터가
읽지 못하기 때문이다. 페이지가 `<div class="lms-layout">`를 직접 열고 닫는다.

```html
<th:block th:replace="~{fragments/_lms_shell :: header}"></th:block>
<div class="lms-layout"
     th:attr="data-handle=${academyHandle},
              data-lms-base=${lmsBase},
              data-lms-user=${lmsUserKey},
              data-authenticated=${lmsAuthenticated}">
  <th:block th:replace="~{fragments/_lms_shell :: sidebar('cart')}"></th:block>
  <main class="lms-content"> ... </main>
</div>
<th:block th:replace="~{fragments/_lms_shell :: footer}"></th:block>
```

- **유효한 `activeMenu` 값 8개**: `list · detail · cart · checkout · my · lecture · manage · contents`.
  URL 마지막 세그먼트와 같은 이름으로 고정한다 — 어긋나면 오타가 조용히 통과한다.
- fragment 파라미터(`activeMenu`)는 Thymeleaf 바인딩이지 Spring 모델 속성이 아니다.
- 모델 속성(`academyName`/`academyHandle`/`lmsBase`/`lmsAuthenticated`/`lmsUserKey`/`lmsIsOwner`)은
  `AcademyLmsShellModel` 한 곳이 주입한다. 공개 컨트롤러와 owner 컨트롤러가 나뉘어 있어도 여기서
  갈리면 사이드바 규칙이 화면마다 달라진다.
- `lmsIsOwner`는 관리 메뉴 **표시** 여부일 뿐 접근 통제가 아니다. 실제 차단은 `SecurityConfig` 매처와
  `AcademyLmsManagePageController`가 한다.
- CSS는 공통 `lms_shell.css` + 페이지별 `lms_{page}.css` 2장을 로드한다.
- JS 로드 순서는 고정이다: `lms_mock` → `lms_data` → `lms_state` → `lms_shell` → (`lms_player`) → `lms_{page}`.
  `sessionStorage`는 `lms_state.js`에서만 만지고, 데이터 접근은 `lms_data.js`(2단계 교체 지점)만 거친다.

## Public page layout / GTM convention

외부 방문자가 읽는 공개 Thymeleaf 페이지는 `layouts/public.html`을 장식하는 Layout Dialect 구조를 사용한다.

```html
<html xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layouts/public}">
<head>...</head>
<body>
  <th:block layout:fragment="content">
    ...
  </th:block>
</body>
</html>
```

- 공통 layout은 `<head>` 최상단의 GTM 스크립트와 여는 `<body>` 직후의 `noscript` iframe을 소유한다.
- GTM 컨테이너는 `GTM-MMJ4MGNN`이며, GA4 측정 ID(`G-...`)와 이벤트 설정은 GTM 관리 화면에서 후속 구성한다.
- 공개 읽기 화면만 이 layout을 사용한다. 작성 화면(`public/community_new.html`), 내부 Academy layout 미리보기(`public/academy_layout_preview.html`), 로그인 후 student/tutor/admin 화면은 기본 방문 분석 범위에서 제외한다.
- 개별 페이지는 GTM 스니펫을 복사하지 않고 `layout:fragment="content"`로 본문만 제공한다.



## Admin topbar/nav fragment convention

새 Admin Thymeleaf 페이지는 상단 영역을 직접 복붙하지 않고 공통 fragment를 사용한다.

```html
<head>
  ...
  <th:block th:replace="~{fragments/_admin_topnav :: head}"></th:block>
</head>
<body>
  <th:block th:replace="~{fragments/_admin_topnav :: topnav('students')}"></th:block>
  ...
</body>
```

- 기준 디자인은 `/admin/academies`의 topbar/nav이다.
- `_admin_topnav :: head`는 favicon 공통 fragment(`fragments/_head :: favicon`)와 Admin topbar/nav CSS를 함께 포함한다.
- `topnav(activeMenu)`의 `activeMenu` 값으로 현재 메뉴만 활성화한다: `students`, `academies`, `analyzes`.
- 본문 레이아웃, 카드, 테이블 스타일은 각 Admin 페이지가 소유한다.
- 새 Admin 메뉴를 추가할 때는 `_admin_topnav.html`의 nav 목록을 먼저 갱신한 뒤 페이지에서 같은 fragment를 호출한다.

## 학교별 시험 분석 자료 공개/관리

Admin은 학교별 시험 분석 HTML 파일을 업로드해 public 콘텐츠로 관리할 수 있다.

- 관리 화면: `/admin/analyzes`
- 공개 목록/검색: `/analyzes`
- 공개 상세: `/analyzes/{id}`
- `/home`에는 공개 상태 분석 자료를 최신 등록순 최대 3개까지 “최신 학교 시험 분석” 섹션으로 노출한다.
- 상세 화면은 업로드 HTML을 직접 DOM에 삽입하지 않고 sandbox iframe으로 격리 렌더링한다.
- CRUD 범위: ADMIN만 생성/조회/수정/삭제 가능. 삭제는 숨김/비공개 처리와 완전 삭제를 모두 제공한다.
- 새 분석 자료 필수 메타데이터: 제목, 학교명, 학년, 학기, 시험 종류, 과목, 공개 여부.

## 공개 BuildIt Wiki (`/wiki`) 화면 컨벤션

- 공개 위키/커뮤니티 허브. `layouts/public` 데코레이트 + `bodyClass='wiki-body'`, 전용 `static/css/wiki.css`(자체 `:root` 팔레트 — teal 계열, "작고 편안한" 톤. 대형 브랜드 CSS 미사용).
- 게시판(시험 후기/변형문제/자료 공유·요청)은 open-community 엔진(`/api/open-community/*`) 재사용 — `BoardType` enum 값 3개(`EXAM_REVIEW`/`VARIANT`/`MATERIAL`) + 목록 `scope=wiki` 로 F2R `/community` 와 분리.
- **`/wiki` Community 플로우는 `/wiki` 문맥 안에서만 끝난다(스펙 §11~13)**: 전용 화면 `/wiki/board/{key}/new`·`/wiki/posts/{id}`, 모든 API 호출 `redirectOnAuthError:false`, `/home`·`/community` 링크·리다이렉트 금지, 각 화면에 명시적 "← BuildIt Wiki로 돌아가기"(`history.back()` 의존 금지). `/community/posts/*` 는 F2R 커뮤니티 전용.
- **자료실 2종 — `analyzes`/`ExamAnalysis` 절대 재사용 안 함**: 모의고사 원본(`wiki_exam_originals` V956, PDF) + 분석 자료(`wiki_analysis_materials` V957, PDF/HWP/HWPX/DOCX 등). 관리 `/admin/wiki-originals`·`/admin/wiki-analysis`(ADMIN), 공개 목록 + 로그인 회원 다운로드 `/api/wiki/originals|analysis-materials/{id}/download`(attachment).
- **문서 업로드 검증은 공용 `infrastructure/storage/DocumentUploadValidator`** — 확장자 화이트리스트(pdf/hwp/hwpx/doc/docx/xls/xlsx/ppt/pptx/zip) + 첫 8바이트 매직 교차검증(PDF / OLE2 / ZIP) + 실행파일(MZ/ELF/#!) 차단 + `UploadSizeGuard`(20MB). 다운로드 응답은 공용 `infrastructure/web/FileDownloadResponses`(pdf/image만 inline, RFC5987 파일명, `nosniff`).
- 게시글 문서 첨부: `community_post_attachments`(V958), `variant`/`share` 게시판만, 게시글당 10개·파일당 20MB.
- 내신 Wiki(`/problem-generator`) 연결은 하단 1줄로만. 과도한 광고 금지.

### Academy Blog / SEO 콘텐츠

강사는 본인 Academy 단위로 블로그 글을 작성할 수 있다. 글은 Tiptap JSON 원본과 sanitizer 적용 HTML을 함께 저장하며, 본문 이미지는 `academy_blog_source`에 메타데이터를 보관하고 기존 FileStorage를 재사용한다. 공개 상태(`PUBLISHED` + `is_visible=true`)인 글만 `/academies/{academySlug}/posts`와 sitemap에 노출된다.

- `academy_blog_posts`: Academy별 글, slug, SEO 제목/설명, Tiptap JSON, sanitized HTML, 발행 상태
- `academy_blog_source`: 본문 이미지 메타데이터, storage key, content type, 파일 크기
- `academy_blog_post_likes`: 공개 Academy 블로그 글 추천. 로그인 사용자별 1회만 저장하고 다시 누르면 취소한다. 추천 가능 기간은 `app.academy-blog.like-allowed-days` 기본 30일이며 운영 환경 설정으로 변경 가능하다.
- `/academy-posts`는 공개 Academy 블로그 글을 한눈에 보는 목록 중심 허브다. 1차 범위는 검색어, 최신순, 추천순이며 상세는 기존 `/academies/{academySlug}/posts/{postSlug}`를 유지한다.

## Lesson community 화면 컨벤션

Lesson community 화면은 공통 favicon/head fragment와 계정 메뉴 fragment를 포함한다.

- 화면: `/lessons/{lessonId}/community`
- Template: `templates/lesson_community.html`
- CSS: `static/css/lesson_community.css`
- JS: `static/js/lesson_community.js`
- 글/댓글 본문은 사용자 입력을 HTML로 렌더링하지 않고 escape 후 `white-space: pre-wrap`으로 표시한다.
- 첨부 링크는 `/api/community/attachments/{attachmentId}/file`을 사용하고, conversation 자료/숙제 자료 UI와 섞지 않는다.
