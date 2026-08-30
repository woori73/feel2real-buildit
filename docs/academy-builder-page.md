# Academy Builder 페이지 (`/builder`) 설계 노트

> 출처: 2026-07-13 deep-interview (5라운드, 최종 ambiguity ~13%). 원문 스펙: `.omc/specs/deep-interview-academy-builder.md`.
> 관련: [academy-publish-design.md](academy-publish-design.md)(발행 API 계약), [FLOWS.md](FLOWS.md), [API.md](API.md).
> 상태: **1차 구현 진행 중**. 착수 전 이 문서 로드.

## 0. 한 줄 요약
로그인 tutor가 랜딩폼에서 학원 정보를 입력해 `/builder`로 이동하고, 실제 지원되는 인라인 편집·섹션 표시·레이아웃·미디어·공개정보 기능만 사용해 발행한다. 신규 발행 전 상태는 client sessionStorage에 보관하고 `POST /api/tutor/academy` multipart 요청으로 발행하며, 기존 Academy는 SSR 데이터로 Builder 내부 DOM에 공개 페이지를 렌더한다. 추가예정 5개 섹션(`sec-reviews`/`sec-curriculum`/`sec-effect`/`sec-market`/`sec-payment`)은 legacy 저장값과 무관하게 항상 OFF이고 공개 페이지에서도 렌더하지 않는다. Education Storefront 섹션 `sec-featured`·`sec-digitaltwin`은 opt-in(기본 OFF, §10 참고).

## 1. 플로우 (확정)
1. **랜딩 입력** — `templates/public/landing.html` `#builder-try` 섹션: 이름/슬로건/과목/수강료 + `updatePreview()` 실시간 프리뷰. `#publishBtn`("학원 페이지 발행하기").
2. **진입 라우팅** — `#publishBtn` 클릭 → 비로그인이면 로그인으로, 로그인 tutor가 이미 Academy를 보유하면 기존 공개 URL(`publicUrl`, 로컬은 `/academies/{slug}`)로 이동, Academy가 없으면 `/builder`(랜딩 입력값 이월).
3. **관리 화면 진입** — 기존 Academy 관리 화면(`/tutor/academy`)의 `Academy 수정`은 `/builder`로 이동한다. 기본정보 수정은 builder 상단 `데이터 편집(임시)` 버튼으로 `/tutor/academy/setup`에 임시 연결한다.
4. **Builder 페이지** — `/builder`는 topbar, 블록/레이아웃 패널, canvas만 표시한다. 실행취소·다시실행·일반 저장·블록 검색·줌·우측 스타일 패널·drag/drop·결제 mock UI는 노출하지 않는다. 실제 지원되는 텍스트 inline 편집, 히어로/원장 업로드 영상 또는 YouTube, 로고, handle, 대상/과목/지역, 외부 URL, footer 사업자정보, 데이터 섹션 표시/순서, desktop/tablet/mobile preview 전환만 제공한다. 텍스트 섹션 컨트롤은 `적용`과 `삭제`만 표시한다. 신규 발행 전 변경은 pending payload에, 기존 Academy 변경은 기존 POST/PUT 계약에 반영한다.
5. **발행** — Builder "발행" 버튼 → 보유 client 데이터를 `academy` JSON part + 선택 `logo` part의 multipart `POST /api/tutor/academy` 1회 호출 → `AcademyService.create()` → `status=PUBLISHED`, handle=`academy-{hash}` 자동 → 서브도메인 즉시 활성.

## 2. 확정된 결정
- **DRAFT = client-side only.** 발행 전까지 서버 저장 없음. 신규 발행 요청은 `/builder`의 JS/sessionStorage가 유일한 보관처. 기존 Academy 조회용 초기값은 `/builder` SSR 모델로 주입한다. DB DRAFT 상태·별도 publish 엔드포인트 **도입 안 함** → [academy-publish-design.md](academy-publish-design.md) §1-1(단일 write) 존중.
- **발행 = 기존 `create()` 호출.** `/builder`는 로고가 없어도 `academy` JSON part를 담은 multipart로 호출하고, 로고가 있으면 같은 요청의 `logo` part에 포함한다. 별도 publish/temp-upload 엔드포인트는 만들지 않는다.
- **URL 식별자 = `handle`** (`academy-{hash}`, SHA-256 base36 · tutorUserId 기반, `AcademyService.resolveCreateHandle`). slug 아님. 자동 생성.
- **actor = 로그인 tutor**, tutor-only + 1인 1학원(기존 학원 있으면 create 409).
- 로고 미첨부 → 서버 기본 로고 폴백(기존 동작). 신규 `/builder`에서 업로드한 로고는 sessionStorage data URL preview로 보관했다가 발행 multipart의 `logo` part로 함께 전송한다.

## 3. Non-Goals (이번 범위 밖)
- canvas 드래그앤드롭 / 블록 추가·삭제·이동 실제 동작
- 우측 스타일 컨트롤러(테마·색·폰트·패딩·섀도우) 실제 적용
- topbar 저장/미리보기/실행취소 실제 동작
- 결제 블록(PortOne) 실제 결제
- 서버측 DRAFT status, 별도 publish 엔드포인트
- 기존 homepage builder 인프라(`AcademyPageLayoutService`, `AcademyLayout*Controller`, 13bad99) 연동 → **나중**
- ~~서브도메인 라우팅 스코핑(기존 URL이 `{handle}.feel2real.co.kr/*`에서도 열리는 문제)~~ → **해소됨**: `SubdomainAcademyFilter`가 서브도메인의 공통 페이지 경로를 apex로 301한다. → [FLOWS.md](FLOWS.md) 서브도메인 스코핑

## 4. 열린 결정 (구현 중 확정)
- **라우팅 vs 409 edge:** `/builder`는 학원 **없는** tutor의 최초 생성용으로 가정. 이미 학원 보유 tutor가 발행 시 409를 사용자에게 안내. (초기 라우팅 답변 "tutor+학원 있으면 Builder"와의 미세 충돌 — 최초생성 해석으로 수렴)
- **canvas 프리뷰 방식:** 기존 Academy와 신규 발행 전 Academy 모두 iframe을 사용하지 않는다. `/builder` SSR Academy JSON 또는 `pendingAcademyPreview` client 데이터를 같은 JS 렌더러에 넣어 `#pageContent` 내부 DOM으로 공개 페이지 모양을 직접 렌더한다. 브라우저 목업 주소바는 `{academy_slug}.feel2real.co.kr` 형식으로 표시한다. API 요청 body(`pendingAcademyPublish`)에는 preview 전용 필드를 섞지 않는다. 단 신규 발행 전 섹션 표시 토글은 서버 계약 필드인 `pageSettings.sectionVisibility`로 저장한다.
- **텍스트 편집:** 실제 텍스트 target은 기본 `contenteditable="false"`이며 섹션 또는 field 클릭 시 해당 섹션만 편집 모드가 된다. 우상단 컨트롤은 `적용`/`삭제`만 제공하고 우측 스타일 패널과 `스타일` 버튼은 제거했다. 적용은 `applySectionTextChanges`, 삭제는 지원 섹션의 `sectionVisibility=false`를 사용하며 hero는 삭제할 수 없다. blur는 저장하지 않고, 기존 Academy는 상단 `발행`으로 full PUT, 신규 Academy는 pending payload를 최초 POST에 포함한다.
- **홈페이지 영상 편집:** 원장 소개 영상 박스를 클릭하면 YouTube 링크 또는 학원 영상 보관함의 `READY` 업로드 영상을 선택할 수 있다. YouTube watch/youtu.be/embed URL은 `pageSettings.principalYoutubeUrl`에 저장하고 iframe으로 정규화해 표시한다. 업로드 영상 ID는 `pageSettings.builderDraft.principalVideoId`에 문자열로 저장하며, 공개/Builder 렌더는 `GET /api/academies/{slug}/videos` 응답에서 같은 ID의 공개 stream URL을 찾아 HTML5 `<video>`로 표시한다. `heroVideoId`/`heroYoutubeUrl`은 legacy 저장 호환 key로만 남기고 새 Build It 히어로에는 영상 카드를 렌더하지 않는다. 신규 업로드는 기존 `/tutor/academy/videos` 관리 화면에서 수행한다. 기존 Academy는 상단 발행(Full PUT), 신규 발행 전에는 pending payload에 포함한다.
- **히어로 이미지 슬롯 편집(원장사진 `.tablet`):** iPad 프레임의 원장사진 슬롯을 클릭하면 OS 파일 선택창을 띄운다(모달 없음). 기존 Academy는 `POST /api/tutor/academy/media/hero/principal`로 즉시 저장한다. **발행 전에는 Academy row가 없어 이 endpoint가 본문 없는 `404`를 내므로 호출하지 않는다** — 로고와 같은 방식으로 파일을 보류했다가(`pendingHeroMedia`) 발행 성공 직후 `flushPendingHeroMedia()`가 업로드한다. 보류 중 미리보기는 `URL.createObjectURL`로 만든다. 로고와 달리 sessionStorage에 담지 않는다 — 애니메이션 WebP/GIF는 十MB 단위라 base64로 넣으면 용량 한도를 넘긴다(대신 새로고침하면 보류분이 사라진다). 보류분 삭제는 서버를 호출하지 않고 로컬에서만 지운다.
- **시간표 달력 이미지(`schedule` 슬롯, V948):** `sec-lessons` 하단 개설 시간표(`.lesson-schedule-inner`) 안의 달력 이미지다. 수업상품에서 파생하는 자동 시간표 목록이 실제 달력형 시간표로 보이지 않는다는 요구에서 나왔다. 원장사진과 같은 슬롯 메커니즘(`HeroMediaSlot.SCHEDULE` → `POST/DELETE /api/tutor/academy/media/hero/schedule`)을 그대로 쓰므로 발행 전 보류·`flushPendingHeroMedia()`·objectURL 미리보기가 모두 동일하게 동작한다. 렌더 차이만 둔다 — **공개 페이지는 이미지가 없으면 블록 자체를 그리지 않고**(빈 자리 금지), **빌더는 비어 있어도 placeholder를 그린다**(그래야 클릭해 올릴 수 있다). 그래서 수업상품이 하나도 없어도 빌더에는 시간표 섹션이 남는다. **달력이 이 섹션의 유일한 본문이다** — 수업상품에서 파생하던 행 목록(`.schedule-list`/`.sch-row`)은 달력형으로 보이지 않아 마크업·CSS 모두 걷어냈고, 그래서 공개 페이지의 시간표 섹션 존재 여부는 lessons가 아니라 이 슬롯이 좌우한다. `시간표 공개` 토글(`is-schedule-hidden`)을 끄면 달력이 숨는다. 업로드 후 캔버스 리렌더는 `sec-hero`가 아니라 `sec-lessons`로 스크롤한다(`afterHeroMediaChange`).
- **로고 이미지 편집:** 로고 영역을 클릭하면 이미지 업로드 모달을 띄운다. 압축 처리는 하지 않고 클라이언트에서 JPG/PNG/WebP/GIF, 최대 2MB로 제한한다. 기존 Academy는 multipart `PUT /api/tutor/academy`의 `logo` 파트로 즉시 저장한다. 신규 발행 전에는 이미지 파일을 2MB 이하로 검증한 뒤 sessionStorage preview(`pendingAcademyLogo`)에 data URL로 보관하고 미리보기에 즉시 반영한다. 발행 버튼은 `academy` JSON part와 `logo` part를 같은 multipart `POST /api/tutor/academy` 요청으로 전송한다.
- **수업대상/과목/지역 편집:** Builder 상단 편집 바는 제거하고, preview 안의 대상 뱃지 row 전체와 과목 뱃지 row 전체를 클릭하면 모달로 수정한다. 지역은 히어로 화면에서 제거했으며 기존 설정 화면의 데이터로만 유지한다. 수정 가능한 요소는 hover/focus 시 노란 glow 테두리로 표시한다. 기존 Academy는 현재 Academy 필수값을 보존한 JSON `PUT /api/tutor/academy`로 즉시 저장하고, 신규 발행 전에는 `pendingAcademyPublish.subjectIds/targetGroups/regions`와 preview snapshot에 반영한다.
- **히어로 대표 수업 선택:** 새 Build It 히어로는 `대표 수업 보기` CTA로 `sec-lessons`에 스크롤한다. 등록 수업 1~4개 선택값(`pageSettings.builderDraft.featuredLessonIds`)과 선택 모달 배선은 legacy/후속 섹션 호환을 위해 유지하지만, 공개 히어로에는 `collection-panel`을 렌더하지 않는다.
- **footer 사업자 정보 편집:** Footer는 저장 모델상 `sectionOrder`/`sectionVisibility` 밖 fixed chrome이고 숨김·삭제·이동하지 않는다. 다만 Builder UI에서는 `sec-footer` 고정 섹션으로 노출하고, 블록/레이아웃 탭 모두 Hero와 같은 `LOCK`으로 표시해 페이지 끝에 고정한다. LOCK 항목을 클릭하면 Canvas Footer로 스크롤하고 일반 섹션과 같은 blue selection 강조를 표시한다. `수정할 곳 보기`에서는 Footer wrapper 전체가 아니라 로고와 7개 실제 사업자정보 필드를 각각 강조한다. Builder preview footer 또는 특정 값 텍스트를 클릭하면 7개 direct field의 값만 다른 섹션과 같은 field-scoped `contenteditable` 상태가 되며, 표준 section selection과 footer 우상단 Apply 컨트롤을 표시한다. 별도 form/modal은 렌더하지 않는다. Editor raw는 저장 raw로만 초기화하고 masked display dummy는 `:empty::before` placeholder로만 보여 입력/payload에 복사하지 않는다. 사업자등록번호는 입력 중 숫자만 최대 10자리까지 받아 `000-00-00000` 형식으로 자동 포맷한다. 단일행은 Enter를 막고 주소만 줄바꿈을 허용하며 Escape는 현재 field를 snapshot raw로 되돌린다. Validation/PUT/storage 실패 시 draft를 유지한다. Dirty 상태에서 section/GNB/canvas 밖을 클릭하면 저장을 즉시 시작하고 원래 클릭은 계속 진행하며, saving 중 추가 Apply/전환/Escape만 막는다. 기존 Academy는 `PUT /api/tutor/academy/page-settings`의 `businessInfo`만 보내 성공 후 preview를 갱신하고, 신규 발행 전에는 `pendingAcademyPublish.pageSettings.businessInfo`를 persisted authority로 저장한다. `businessInfoUrl` 입력은 없고 FTC link는 사업자등록번호 숫자 10자리에서 파생한다. Null/blank는 공개 화면에서 숨기지 않고 마스킹하며 raw에는 그대로 blank로 남는다. 공개 홈과 학원 블로그 목록/상세가 공통 helper/CSS를 사용한다. 이 중 `phone`/`email`은 공개 홈 우상단 `상담 신청` 모달의 연락 수단으로도 재사용한다(강사 입력값만, 마스킹 dummy 제외 → [FLOWS.md](FLOWS.md) §5.5). `phone`은 사업자등록번호와 같은 방식으로 입력 중 자동 포맷하며, 저장 검증은 아래 상담 연락처 모달과 `validateContactPair` 하나를 공유한다.
- **상담 연락처 편집(`상담 신청` 버튼):** Builder preview의 `상담 신청`은 다른 `cv-btn-primary`와 달리 `pointer-events:auto`로 살려 두고(`builder-consult-edit`), 클릭 시 방문자 동작 대신 `상담 연락처 수정` 모달을 연다. 모달은 `phone` 입력 1개와 이메일 `local` + 도메인 `select`(naver.com/gmail.com/daum.net/hanmail.net/nate.com/kakao.com/outlook.com/icloud.com + 마지막 `직접입력`)로 구성하고, `직접입력`을 고르면 숨겨둔 커스텀 도메인 입력이 열린다. 저장된 도메인이 preset에 없으면 `직접입력`이 선택된 상태로 복원한다. 전화는 입력 중 자동 포맷(`formatPhoneNumber`)하고 `0`으로 시작하는 9~11자리만 통과시키며, 서울 `02`는 국번이 한 자리 짧아 `2-3-4`/`2-4-4`로, 그 외는 `3-3-4`(10자리)/`3-4-4`(11자리)로 잡는다. 이메일 `local`이 비면 도메인 선택과 무관하게 `email`을 빈 값으로 저장한다(`@naver.com` 저장 방지). 저장 위치는 footer와 동일한 `pageSettings.businessInfo`이므로 **편집 결과가 footer에 그대로 반영**되며, 나머지 5개 field는 현재 preview 값을 함께 실어 보내 지워지지 않게 한다. 기존 Academy는 `PUT /api/tutor/academy/page-settings`, 신규 발행 전에는 `pendingAcademyPublish`에 저장하는 footer와 같은 두 경로를 태운다. footer 인라인 편집이 진행 중(`editing`/`saving`)이면 draft를 덮어쓰지 않도록 모달을 열지 않는다. 별도 endpoint/입력 field는 추가하지 않는다.
- **섹션 표시/숨김:** 실제 토글 대상은 `sec-intro`, `sec-principal`, `sec-lessons`, `sec-social`뿐이다. `sec-reviews`, `sec-curriculum`, `sec-effect`, `sec-digitaltwin`, `sec-market`, `sec-payment`는 Builder 카드에서 OFF/`aria-disabled=true`이며 mouse/keyboard 토글 대상이 아니다. legacy `true`/`null`/누락도 Builder·공개 렌더에서 effective OFF로 처리한다. Builder 진입 시에는 write하지 않고, 신규 pending/POST와 기존 Academy의 다음 정상 POST/PUT payload에서 6개 값을 명시적 `false`로 정규화한다.
- **섹션 순서:** canonical default full order는 `sec-hero`, `sec-intro`, `sec-principal`, `sec-videos`, `sec-reviews`, `sec-lessons`, `sec-curriculum`, `sec-effect`, `sec-digitaltwin`, `sec-market`, `sec-payment`, `sec-social`이다. `sectionOrder`가 null/missing이면 공개/Builder 렌더 helper가 이 순서로 fallback한다. non-null `sectionOrder`는 모든 관리 섹션을 정확히 1번씩 포함해야 하며 hero는 index 0/LOCK이다. `sectionVisibility`와 순서는 독립이다. 레이아웃 탭은 ON 섹션만 표시/수정하고, 숨긴 섹션도 full order에 남아 다시 표시하면 같은 slot에 복귀한다.
- **텍스트 UX API 영향:** G003 텍스트 UX 변경은 static JS/template/docs 범위다. `academy_builder_text_utils.js`는 `academy_builder.js`보다 먼저 로드되며, 기존 `POST /api/tutor/academy`, `PUT /api/tutor/academy`, `PUT /api/tutor/academy/page-settings` endpoint와 request/response 계약은 변경하지 않는다.
- **편집 가능 영역 안내:** Canvas toolbar의 `수정할 곳 보기` 버튼은 실제 동작이 연결된 텍스트·로고·영상·수업대상/과목/지역·외부 URL·Footer 사업자 정보·공개 주소만 한꺼번에 강조한다. 준비 중/mock 섹션은 강조하지 않는다. 안내 중 강조 요소를 클릭하면 안내 상태만 먼저 종료하고 기존 편집 이벤트를 같은 첫 클릭으로 실행한다. 버튼 재클릭·Escape·화면의 다른 위치 클릭으로도 종료한다. 최초 `/builder` 진입에는 localStorage 기반 coachmark를 1회만 표시한다. Builder 조작 UI 자체는 desktop 전용이지만, Canvas의 tablet/mobile 토글은 `canvas-frame` container query로 발행 홈페이지의 768px/480px 반응형 레이아웃을 동일하게 미리 보여준다.
- **left-panel 분류:** 실제 데이터 섹션은 `sec-hero`(LOCK), `sec-intro`, `sec-principal`, `sec-lessons`, `sec-social`, `sec-footer`(LOCK)다. 추가예정 6개 카드는 현재 범위를 알리기 위해 보이되 OFF/비활성 상태이며 focus·hover·toggle affordance가 없다. 레이아웃 탭에는 실제 ON 섹션과 고정 Hero/Footer만 나타난다.
- **수업상품 탭 프리뷰:** `lesson_products.target_group`은 Academy 설정의 수업대상과 동일한 `TargetGroup` enum(`ELEMENTARY`/`MIDDLE`/`HIGH`/`UNIVERSITY`/`ADULT`)을 저장한다. 공개/Builder 페이지의 `sec-lessons`는 `대표 수업` eyebrow + 편집 가능한 h2 헤더, 탭, 접이식 수업 패널로 구성한다. **h2는 `builderDraft.curriculumTitle`로 원장이 자유롭게 고친다**(빌더 h2에 `data-builder-draft="curriculumTitle"`). 기본값은 학원마다 달라야 하므로 `<학원명> 커리큘럼`으로 파생한다 — `F2R 커리큘럼` 같은 전 학원 공통 고정 문구는 쓰지 않는다. **패널 본문 골격은 수업 관리 편집기(`/tutor/academy/lessons/editor`)의 미리보기(`.preview`)와 같다** — `selected-hero`(kicker/제목/설명/강조 + 대표 미디어) + `detail-section`(learnerNeed/coreDetailTitle 헤딩, OT 영상, explain 카드). 원장이 편집기에서 본 결과와 공개 화면이 어긋나면 안 되므로 클래스 이름과 테마 변수까지 `lesson_product_editor.css`와 맞춘다. **탭은 닫힌 채 렌더한다**(`.lesson-product-collapse`가 `grid-template-rows: 0fr`) — 탭을 클릭하면 `.is-expanded`가 붙어 그 패널이 열리고, 열린 탭을 다시 누르면 통째로 닫히며, 다른 탭을 누르면 닫지 않고 내용만 바꾼다. 닫힌 탭에는 클릭 가능 신호가 없으므로 탭 위에 `.lesson-product-tab-hint` 안내 문구를 고정으로 둔다. 여닫는 CSS 애니메이션은 붙이지 않는다. 탭 안내 문구가 비어 있으면 일정/대상 값을 보조 문구로 대체한다. 이 프리뷰에는 신청 CTA를 포함하지 않는다. Builder 미리보기에는 프리뷰 아래 `수업 관리` 버튼을 추가한다. 발행된 Academy만 `/tutor/academy/lessons/editor`로 이동하며, 발행 전 client-only Academy는 `학원 홈페이지를 먼저 발행한 후 수업을 등록할 수 있습니다.` 안내만 표시하고 이동하지 않는다. 공개 페이지에는 관리 버튼을 노출하지 않는다. 기존 수업상품은 마이그레이션에서 `HIGH`로 백필한다. 커리큘럼은 추후 curriculum 테이블과 academy/lesson 연결 모델 확정 후 재도입한다.
- **Build It 3 시안 섹션 대응:** 시안의 `class="notes snap"`(000 원장의 교육 노트)은 기존 관리 섹션 `sec-intro`/좌측 패널 "학원 소개"에 매핑한다. 별도 칼럼/블로그 테이블은 아직 없으므로 `builderDraft.introHeading`/`introMotto`/`introBodySecondary`와 `introFeatures` 저장소를 재사용해 정적 노트 카드로 렌더한다. `class="philosophy snap"`(학습 철학)은 독립 관리 섹션 `sec-philosophy`로 분리해 `sec-principal` 바로 뒤에 렌더한다. 별도 입력 필드는 아직 없고 시안의 정적 카드 4개를 사용한다.
- **랜딩 입력값 이월 수단:** 쿼리스트링 / sessionStorage — 구현자 재량.

## 5. Acceptance Criteria
- [x] 비로그인 또는 비강사 사용자가 `/builder`를 직접 요청하면 Academy 조회/Builder 렌더 없이 랜딩(`/`)으로 리다이렉트.
- [ ] `/builder` 라우트가 `academy_builder_demo.html` 디자인 서빙(`fragments/_head :: favicon` 준수).
- [ ] `#publishBtn`: 비로그인 → 로그인, 로그인 tutor+기존 Academy 있음 → 기존 공개 URL, Academy 없음 → `/builder`(입력값 이월).
- [x] Builder에는 실제 동작하는 편집·발행·레이아웃·preview 전환만 노출하고, drag/drop·스타일·결제·undo/redo·일반 저장·검색·줌 mock UI는 제거한다.
- [x] 기존 Academy의 `/builder` 초기값을 Thymeleaf SSR 모델로 주입하고 canvas가 iframe 없이 공개 페이지 DOM을 표시.
- [x] Builder 섹션 표시 토글은 `pageSettings.sectionVisibility`를 저장하고 공개 페이지 preview DOM에 반영.
- [x] Builder 레이아웃 탭 섹션 순서는 ON 섹션만 표시해 `pageSettings.sectionOrder`로 저장하고, 공개/Builder 섹션 stack은 저장 순서로 렌더.
- [ ] 랜딩 주력 과목 복수 선택 → `/builder` pending preview에 복수 과목 표시 → 신규 발행 "발행" → 복수 `subjectIds`와 pending `pageSettings.sectionVisibility`/`builderDraft`를 담은 `academy` JSON part + 선택 `logo` part의 multipart `POST /api/tutor/academy` 1회 → 200. 신규 pending 텍스트/히어로 영상/네이버 블로그·지도 URL/수업대상/지역 변경은 최초 POST에 포함. 기존 Academy 텍스트 편집: inline 수정 후 "발행" → JSON `PUT /api/tutor/academy` → 200.
- [ ] 발행 후 `academy-{hash}.feel2real.co.kr` 접근 시 공개페이지 200.
- [ ] 기존 학원 보유 tutor 발행 시 409 안내.
- [x] `sec-videos`(수업 영상)가 관리 섹션으로 등록되어 빌더 `sectionVisibility` ON/OFF와 `sectionOrder` 순서 변경이 서버 검증을 통과한다. 기본 순서는 `sec-principal` 뒤.
- [x] 공개 페이지 `sec-videos`가 READY 영상 최신순 최대 10개를 유튜브 재생목록형(메인 플레이어 + 썸네일 리스트)으로 렌더하고, 영상이 0개면 준비중 placeholder를 노출한다(섹션을 숨기지 않는다).
- [x] `sec-videos` 섹션 골격은 다른 섹션과 동일한 `section-header`/`section-badge`/`section-title`/`section-sub` 구조를 쓴다.
- [x] `/tutor/academy`에 `수업 영상` 카드가 있어 `/tutor/academy/videos`로 이동할 수 있다.

### 5.1 `sec-videos` 계약 (수업 영상)

- **빌더가 하는 일은 섹션 ON/OFF와 순서뿐이다.** 영상 업로드·삭제는 `/tutor/academy/videos` 대시보드가 맡는다(개별 영상 선택/정렬 UI 없음). 빌더의 섹션 안에는 미리보기와 "영상 관리" 진입 버튼만 둔다. 발행 전 client-only Academy는 영상 소유 주체가 없으므로 버튼 클릭 시 `학원 홈페이지를 먼저 발행한 후 영상을 등록할 수 있습니다.` 안내만 표시하고 이동하지 않으며, 발행된 Academy만 관리 화면으로 이동한다.
- 노출 집합은 해당 Academy의 **READY 영상 전체(최신순 최대 10개)**로 자동 결정된다.
- 영상 데이터는 `AcademyResponse`에 싣지 않고 `GET /api/academies/{slug}/videos`로 따로 받는다 — 목록 API까지 영상이 딸려가는 N+1을 피하기 위해서다.
- **⚠️ `DEFAULT_SECTION_ORDER`는 4곳에 중복 정의되어 있다.** 섹션을 추가할 때 전부 같이 고쳐야 한다:
  `AcademyService.java`(서버 검증), `academy_public.js`(공개 렌더), `academy_builder.js`의 `DEFAULT_SECTION_ORDER`와 `MANAGED_SECTION_IDS`.
  특히 `AcademyService.validateSectionOrder`는 **size-lock**(길이가 `DEFAULT_SECTION_ORDER`와 정확히 같아야 함)이라, 서버에만 추가하고 클라이언트를 빼먹으면 빌더의 순서 저장이 통째로 400이 된다. 반대로 `validateSectionVisibility`는 `sec-[a-z0-9-]+` 정규식이라 새 키가 그냥 통과하므로, **실제 게이트는 order 경로**다.

## 6. 검증 방법 (ralph / 자동 verifier 필수)
> ⚠️ **로컬 서브도메인 함정:** `SubdomainAcademyFilter`는 `PublicSiteProperties.hasBaseDomain()` false면 **no-op**. 로컬 bootRun/기본 test 프로필엔 base-domain 미설정 → `academy-{hash}.feel2real.co.kr` **라우팅 자체가 안 됨**. **라이브 서브도메인 200을 로컬 성공기준으로 쓰지 말 것.** (거짓 통과/무한루프 원인)

- **발행 배선 검증 = 통합테스트** (given/when/then, 라이브 DNS 불요):
  - given: 로그인 tutor(academy 없음) + 유효 academy JSON part(name/intro/subjects/targetGroups/regions/pageSettings) + 선택 logo part
  - when: multipart `POST /api/tutor/academy`
  - then: 201 + 응답 handle이 `^academy-[0-9a-z]{6,}$` + DB academy 1건
  - and(edge): 이미 academy 보유 tutor → 409
- **서브도메인 라우팅 검증**: base-domain 세팅한 `SubdomainAcademyFilter` 단위/슬라이스 테스트(기존 테스트 재사용·확장)로 handle→forward 확인. 라이브 DNS 불필요.
- **step1(템플릿)·step2(라우팅) 검증**: MockMvc — `/builder` GET 200 + 예상 요소 존재, `#publishBtn` 라우팅(로그인/비로그인 분기).
- **텍스트 편집 검증**: `/builder` 기존 Academy SSR JSON 포함, 텍스트 편집 컨트롤/inline marker, `PUT /api/tutor/academy` JSON delegation 테스트.
- **섹션 표시/숨김 검증**: `AcademyService.updatePageSettings` unit test — 기존 URL 유지 + `sectionVisibility` 교체(`sec-intro`/`sec-principal`/`sec-lessons`/`sec-social` 포함), 잘못된 section id 거부. `/builder` smoke — hero LOCK, 관리 섹션 토글 렌더, preview DOM 재렌더/스크롤 코드 확인.
- 신규 함수는 CLAUDE.md 규칙대로 given/when/then unit test 동반.

## 7. UI 노출 경계

- 유지: 발행, 수정할 곳 보기, 블록/레이아웃 탭, 실제 섹션 ON/OFF와 순서, 텍스트 적용/삭제, 로고·영상·handle·기본정보·외부 URL·Footer 편집, desktop/tablet/mobile preview 전환.
- 미노출: 실행취소, 다시실행, 일반 저장, 블록 검색, zoom/fit, 우측 스타일 컨트롤러, drag/drop/add block, PortOne 결제 demo.
- 미래 섹션 6개는 순서 호환을 위해 canonical `sectionOrder` key에는 남지만 UI·Builder preview·공개 페이지·레이아웃 탭에서는 effective OFF다.
- 제거된 mock 기능을 API나 영속화에 연결하려면 별도 요구사항과 설계 검토가 필요하다.

## 8. 2026-07-14 세션 체크포인트

### 완료한 작업
- `/builder` 라우트를 Thymeleaf 기반 Academy Builder 화면으로 연결하고, 기존 Academy 보유 강사는 서버 SSR 모델로 학원명/slug/공개 경로/`sectionVisibility`를 주입한다.
- 캔버스 영역은 기존 Academy가 있으면 SSR Academy JSON으로 공개 페이지 모양을 Builder DOM에 직접 렌더하고, 브라우저 목업 주소바는 `{academy_slug}.feel2real.co.kr` 형식으로 표시한다.
- `/tutor/academy`의 `Academy 수정` 진입은 `/builder`로 변경했고, builder 상단 `데이터 편집(임시)`는 `/tutor/academy/setup`으로 연결해 기존 기본정보 편집 경로를 보존했다.
- 랜딩 `학원 페이지 발행하기` 버튼은 로그인 tutor가 기존 Academy를 보유한 경우 기존 공개 URL로 이동하고, 신규 생성 흐름은 `/builder` sessionStorage preview/publish 데이터로 이월한다.
- `PUT /api/tutor/academy/page-settings`를 추가해 Builder에서 `AcademyPageSettings.sectionVisibility`/`sectionOrder`와 `blogUrl`/`naverMapUrl`을 저장한다. `blogUrl`/`storeUrl`/`naverMapUrl` 미포함 요청은 기존 값을 유지한다.
- 좌측 패널은 공개 페이지 섹션 기준으로 재정리했다.
  - `sec-hero`는 기본 고정/항상 ON/LOCK.
  - `sec-intro`, `sec-lessons`(수업상품/강의 목록), `sec-social`은 Academy 데이터 기반 섹션으로 ON/OFF 토글한다. `sec-intro` 좌측 컬럼 구성은 §9 참고.
  - `sec-principal`(원장 소개)은 Academy 데이터 섹션으로 실제 렌더한다: 이름/직함/소개/약력 리스트(가변)/인용구를 `pageSettings.builderDraft`(principalName/principalRole/principalDesc/principalQuote/principalCareer[])로 인라인 편집·저장하고, 원장 소개 영상은 `principalVideoId`(학원 업로드 영상) 또는 `principalYoutubeUrl`(YouTube URL)로 연결한다.
  - `sec-reviews`, `sec-curriculum`, `sec-effect`, `sec-digitaltwin`, `sec-market`, `sec-payment`는 추가예정 카드에서 OFF/비활성으로 표시하며 Builder·공개 렌더와 레이아웃에서 제외한다. legacy 저장값은 읽을 때 유효 OFF로 취급하고 다음 정상 저장 payload에서 `false`로 수렴시킨다.
- 좌측 섹션 카드는 ON/OFF 상태를 outline/색상/투명도로 표시한다.
- 공개 페이지 JS는 `sectionVisibility[id] === false`인 관리 섹션을 렌더하지 않는다. `sec-lessons`는 수업상품 탭과 선택한 수업의 접이식 프리뷰만 담당한다. `sec-curriculum`은 future 슬롯이다.
- Builder는 저장 성공 후 최신 Academy 응답을 iframe 없이 내부 DOM으로 다시 렌더하고 클릭한 섹션으로 smooth scroll한다.
- `sec-social`은 실제 공개 페이지에서는 blog/store URL이 모두 없으면 숨기지만, Builder preview에서는 토글 피드백을 위해 준비중 placeholder를 렌더한다.
- `AGENTS.md`, `CLAUDE.md`, `docs/WORKFLOW.md`의 dev/stg 검증 규칙을 정리했다.
  - 별도 `run-dev/run-stg` 검증 worktree를 만들지 않는다.
  - 현재 작업 worktree에서 변경분 커밋 후 `dev`/`stg`를 순차 checkout·merge·검증하고 원래 feature 브랜치로 복귀한다.
  - dev/stg profile은 각각 `dev`, `stg` 단일 profile만 사용하고 `local,dev` / `local,stg` 조합은 사용하지 않는다.

### 검증 완료
- `node --check src/main/resources/static/js/academy_builder.js` 통과.
- `node --check src/main/resources/static/js/academy_public.js` 통과.
- `git diff --check` 통과.
- `direnv exec . ./gradlew test --tests f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyServiceTest --tests f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller.AcademyControllerTest` 통과.
- `direnv exec . ./gradlew build` 통과.
- 로컬 `http://localhost:8820/builder` smoke 확인:
  - JSON 로그인 `test1234/test1234` 성공.
  - `/builder` 200.
  - SSR 데이터 `/academies/이게되나`, `이게되나.feel2real.co.kr` 확인.
  - hero LOCK, `sec-intro`, `sec-principal`, `sec-lessons`, `sec-social` 토글 렌더와 추가예정 6개 섹션 카드 OFF/비활성 및 Builder·공개 preview 미렌더 확인.
  - `data-section-toggle` 카드 inline `onclick` 충돌 제거 확인.

### 이번 범위 밖 / 다음 재개 지점
- drag/drop과 스타일 패널 UI는 Builder에서 제거했으며 실제 구현은 별도 범위다. 섹션 순서 저장은 레이아웃 탭 up/down 버튼에 한해 `pageSettings.sectionOrder`로 배선한다. 텍스트 직접 편집은 지정된 공개 preview 필드만 허용한다.
- Builder에서 분류/이미지, blog/store 외부 링크 편집은 아직 `/tutor/academy/setup` 링크로 대체한다. 학원명/소개 텍스트는 Builder preview에서 inline 편집하고, 히어로 YouTube 영상 링크는 영상 박스 클릭 모달, 로고는 로고 영역 클릭 업로드 모달, 수업대상/과목/지역은 preview의 해당 위치 클릭 모달로 1차 편집한다.
- `sec-social`의 blog/store URL 입력 UX는 아직 setup 데이터에 의존한다.
- dev/stg 검증은 새 workflow에 맞춰 현재 작업 worktree에서 순차 checkout 방식으로 수행한다.
- 후속 작업은 현재 BUILD IT 제품 방향 기준으로 새로 스펙화한다.

## 9. 2026-07-30 변경: 중복 정보 제거 + 수업상품 탭 안내

발행 홈페이지에서 학원 로고·이름이 nav → hero → sec-intro까지 세 번 반복되던 문제와, 수업상품 탭이 접힌 상태에서 클릭 가능함을 알 수 없던 문제를 정리했다.

- **GNB 지역 보조 텍스트 제거.** `.cv-gnb`의 `eh-logo-sub`(지역/`Academy` fallback)를 공개(`academy_public.js`)·빌더(`academy_builder.js`) 양쪽에서 삭제했다. 지역 편집은 hero eyebrow(`.cv-eyebrow[data-builder-basic="regions"]`)에 그대로 남아 있어 편집 경로는 유지된다. `.eh-logo-sub` CSS 자체는 footer 로고가 계속 쓴다.
- **`sec-intro` 좌측 컬럼 = 과목 + 학원 특징.** 로고(`intro-logo-css`, `data-builder-logo="intro"`)·학원명 `h3`·영문명(`introEnglishName`)·모토(`introMotto`) 대신 다음을 렌더한다.
  - **과목**: Academy 데이터(`subjects`)를 `.intro-subject-badges`에 hero와 같은 `.badge`로 표시. 빌더에서는 row 전체가 hero와 같은 `data-builder-basic="subjects"` 모달 편집 대상이다.
  - **학원 특징**: `pageSettings.builderDraft.introFeatures`(개행 결합 문자열, long key 2000자)에 저장하는 **가변 리스트**. 원장 약력(`principalCareer`)과 동일한 패턴으로 `data-intro-feature-item`/`-add`/`-remove`를 쓰고, 섹션 `적용` 시 `applySectionTextChanges`가 DOM에서 배열로 수집해 커밋한다. 추가/삭제는 즉시 커밋·저장한다. 저장값이 없으면 공개·빌더가 같은 안내 placeholder 3줄을 보여준다.
  - Build It 히어로는 시안(`docs/archive/archived_html/에듀홀릭_BuildIt_3.html`)의 좌측 문구/CTA/통계 + 우측 iPad 원장사진 구조를 따른다. 대표 교재 카드와 히어로 영상 카드는 렌더하지 않고, 대상·과목 배지는 공개 페이지에서 숨기되 빌더 캔버스에만 편집 통로로 유지한다. `introTextbookTitle` 등 기존 저장 key는 기존 row의 full PUT payload 호환을 위해 서버 허용 key에 남긴다.
  - 로고 편집은 GNB/Footer 로고(`data-builder-logo="gnb"|"footer"`)로 계속 가능하다. `introEnglishName`/`introMotto`는 **렌더·편집 대상에서 빠졌지만 서버 허용 key에는 남긴다** — 기존 row의 저장값이 그대로 full PUT payload에 실려 오므로 key를 지우면 기존 Academy 저장이 400이 된다.
- **수업상품/시간표 시안.** `lesson_product_showcase.js`의 공유 `render()`는 수업 관리 편집기 미리보기와 같은 골격(탭 + `selected-hero`/`detail-section`)을 접힌 상태로 렌더하고, 탭 클릭(`toggle()`)으로만 편다. `academy_sections.js`의 `sec-lessons`는 그 아래에 `개설 시간표` / `지금 모집 중인 반` 밴드를 함께 렌더한다. 이 밴드의 본문은 **원장이 올린 달력 이미지 1장**이다(`academies.schedule_image_storage_key`, V948 — 위 "시간표 달력 이미지" 항목). 과거에는 수업 데이터의 `name`/`targetGroup`/`scheduleSummary`로 리스트를 자동 생성했으나 달력형으로 보이지 않아 걷어냈다 — 시간표는 수업상품 데이터에서 파생하지 않는다. 별도 `sec-schedule` 관리 섹션은 만들지 않는다(순서/표시 설정 대상이 아니다). 방문자 카운터는 본문 플로팅 위젯이 아니라 footer copyright 옆 `<span class="counter"><span class="d"></span>오늘 n · 누적 n</span>` 형태로 표시한다.
- **검증**: `node --test "src/test/js/*.test.js"`(신규 `academy_intro_summary.test.js`, `academy_builder_consult_contact.test.js` 포함), `AcademyServiceTest`(introFeatures accept/2000자 초과 reject), `AcademyBuilderTemplateContractTest`(builder JS 캐시버스팅 버전을 문자열로 pin하므로 `?v=` 변경 시 함께 수정).

## 10. 2026-08-30 변경: Education Storefront Capability V1

Builder를 "학원 홈페이지 편집기"에서 "교육자의 교육서비스를 Storefront처럼 보여주는 편집기"로
한 단계 확장한다. Shopify Storefront UX 원리(Featured hierarchy, Collection 탐색성, 강한
typography hierarchy, 단일 CTA, mobile-first)를 교육 서비스로 번역해 적용하되, Shopify의 색상·
로고·문구·proprietary UI는 복제하지 않는다. 이번 범위는 **재사용 가능한 generic 섹션 2개 추가 +
섹션 추가 시 4곳 수동 동기화 취약성 완화**이며, 에듀홀릭 등 실제 콘텐츠 입력·발행은 별도 PHASE 2다.

### 추가된 재사용 섹션 (기본 OFF · opt-in)

| id | UI 이름 | 용도 | 입력 키(`builderDraft`) |
|---|---|---|---|
| `sec-featured` | Featured · 시즌 프로그램 | "지금 가장 알리고 싶은 프로그램 / 시즌 캠페인" | `featuredEyebrow`, `featuredTitle`, `featuredDescription`, `featuredItem1..4`, `featuredCtaLabel` |
| `sec-digitaltwin` | AI Native 교육 과정 | "경험 있는 교육자 + AI"의 교육 과정을 단계 흐름으로 | `aiProcessEyebrow`, `aiProcessTitle`, `aiProcessDescription`, `aiProcessStep1..5`, `aiProcessNote` |

- `sec-digitaltwin`은 기존 future 예약 id를 재사용한다(순서/저장 호환). future 목록에서 빼고
  opt-in 데이터 섹션으로 승격했다.
- **opt-in 규칙:** 두 섹션은 `sectionVisibility[id] === true`일 때만 공개/Builder canvas/레이아웃 탭에
  렌더된다. 키가 없으면(기존 모든 학원) 렌더되지 않아 기존 사이트 모습이 그대로 유지된다.
  기존 데이터 섹션은 반대로 명시적 `false`일 때만 숨는다. 판정은 `academy_sections.js`의
  `OPT_IN_SECTION_IDS` + `toSectionView.sectionVisible`, 공개 `academy_public.js`의
  `isSectionVisible`, 빌더 `academy_builder.js`의 `academySectionVisible`/`toggleSectionVisibility`/
  `applySectionVisibilityState`가 같은 규칙을 공유한다.
- 마크업 정본은 `academy_sections.js`의 `renderFeatured`/`renderAiProcess` 한 벌이고, 공개/빌더
  차이는 기존 ctx 슬롯 패턴(`editableText` + `featuredItem`/`featuredCta`/`aiProcessStep`)이 흡수한다.
- 문구·항목은 전부 `builderDraft` generic key다. `2026`, `압구정중`, `에듀홀릭` 같은 값을 코드에
  하드코딩하지 않는다 — 다른 원장도 같은 섹션으로 자기 프로그램/교육 과정을 표현할 수 있다.
- 공개 화면에는 `RAG`/`LLM`/`Embedding`/`Vector DB`/`MCP` 등 개발 용어를 쓰지 않는다.

### SECTION_ORDER 4곳 동기화 취약성 완화

기존: `DEFAULT_SECTION_ORDER`가 서버(`AcademyService`) + `academy_public.js` + `academy_builder.js`
(`DEFAULT_SECTION_ORDER`, `MANAGED_SECTION_IDS`)에 중복 정의되고, `validateSectionOrder`가
**size-lock**(정확히 같은 길이 + 모든 관리 섹션 포함)이라 서버에만 섹션을 추가하고 클라이언트
배열을 못 맞추면(혹은 브라우저가 옛 JS를 캐시하면) 옛 길이 순서가 넘어와 **레이아웃 저장 전체가 400**.

변경:
- `AcademyService.normalizeSectionOrderForWrite()` — 저장 전 순서를 canonical 완전 목록으로 정규화한다.
  부분 목록을 받아 빠진 관리 섹션을 canonical 위치(그 섹션 다음 순위 섹션 앞)에 채워 넣는다.
- `validateSectionOrderInput()` — 형식만 본다: 빈 ID·중복·미등록 ID·hero 포함 시 첫 번째 아님만 거부.
  **완전성(모든 관리 섹션 포함)은 더 이상 입력 조건이 아니다** — normalize가 보장한다.
- 결과: 서버에 섹션을 추가하고 3개 JS가 못 따라와도 사용자 저장이 깨지지 않는다. 새 섹션은
  JS가 따라잡기 전까지 canonical 기본 위치에 렌더될 뿐이다.
- `src/test/js/academy_sec_videos.test.js`가 여전히 서버/공개/빌더 3곳의 `DEFAULT_SECTION_ORDER`
  완전 일치를 CI에서 못박는다(파서를 `List.of(...)` 포함하도록 `;` 종결자 기준으로 고침).
- Builder 전체를 재작성하는 architecture refactor는 하지 않았다.

### 검증 (DB 불필요분)

- `node --test src/test/js/academy_storefront_sections.test.js` — opt-in 기본 OFF, 명시적 true 렌더,
  기존 섹션 회귀 없음, generic 기본값 fallback(하드코딩 없음), 편집 앵커 분리.
- `academy_builder_future_sections.test.js`, `academy_sec_videos.test.js` — 3곳 순서 일치, 승격 후 future 목록.
- `./gradlew test --tests AcademyServiceTest --tests AcademyBuilderTemplateContractTest` — 신규 draft key
  수용, opt-in visibility 토글, stale client 순서 backfill(400 없음), 입력 검증 유지.
- `@SpringBootTest` 계열(EduHolicSeedIntegrationTest, SubdomainRenderIntegrationTest 등)은 로컬 MySQL이
  필요해 CI에서 검증한다.

## 11. 2026-08-30 변경: PHASE 2.1 — Storefront 중앙 정렬 + 섹션 제목 독립

에듀홀릭 실제 발행 페이지에서 나온 수정.

### Featured 설명 / AI Native 주석이 왼쪽으로 밀리고 카드와 겹치던 버그

근본 원인: `#publicView :is(h1,h2,h3,h4,p):not(.notes-title,.notes-quote){margin:0;}` 리셋의
specificity가 (1,1,1)이라 `.featured-desc`/`.ai-process-desc`/`.ai-process-note`의
`margin:X auto 0`를 이겼다 → `auto`가 0으로(좌측 정렬), `margin-top`이 0으로(단계 카드와 겹침).
`.notes-title`/`.notes-quote`가 이미 예외로 빠져 있던 것과 같은 방식으로 세 클래스를 `:not()`에
추가했다. `.ai-process-note` 상단 여백은 26→32px, `line-height` 1.65→1.7. `text-align:center`만
넣지 않고 flow 원인을 고쳤다. `academy_public_mobile.test.js`가 예외 목록과 세 마진을 pin한다.

### 섹션 제목 독립 편집 (마이그레이션 없이)

`sec-videos` 제목이 `${academy.name}의 수업 현장`으로 **하드코딩**돼 있어 학원명을 바꾸면 같이
바뀌었다. `sec-social` 제목("최신 소식 & 방문 안내")도 고정. 각 섹션 제목을 독립 draft key로 뺐다.

| 섹션 | 제목 소스 | 미설정 시 fallback |
|---|---|---|
| GNB / 간판 | `builderDraft.headerTitle` (기존) | `academy.name` |
| Hero H1 | `academy.name` (`data-builder-text="name"`, 정체성값) | — |
| 수업 영상(Actual Class) | `builderDraft.videosSectionTitle` (신규) | `${academy.name}의 수업 현장` |
| 커리큘럼(Programs) | `builderDraft.curriculumTitle` (기존) | `${academy.name} 커리큘럼` |
| 외부 연동 | `builderDraft.socialSectionTitle` (신규) | `최신 소식 & 방문 안내` |

- Hero H1은 그대로 `academy.name`을 쓴다(정체성 = 히어로에서 편집, GNB·영상·커리큘럼은 각자 키).
  `academy-builder-page.md` §9의 "name에 직접 묶으면 히어로 제목까지 같이 바뀐다" 원칙을 유지.
- 새 키는 `ctx.editableText`를 통해 렌더되므로 빌더 편집 앵커/공개 non-anchor가 자동 분리된다.
  `academy_builder.js` 변경 없음(섹션은 이미 `data-builder-section-key`로 클릭·편집 대상).
- 기존 사이트: 새 키 없음 → fallback으로 기존 문구 그대로 → 회귀 0.
- 서버 `BUILDER_DRAFT_KEYS`에 `videosSectionTitle`·`socialSectionTitle` 추가(200자 제한).

### 이번 범위 밖 (PHASE 3-B — 설계 후 별도 PR)

Horizontal Collection primitive (Programs → Actual Class Shorts → Mini Lectures).
BuildIt Web V2: PAGE = Vertical Story × 각 Collection = Horizontal Browse.
→ §13에서 구현.

## 12. 2026-08-30 변경: PHASE 3-A — Storefront Motion (Vertical Storytelling)

VERTICAL STORYTELLING — 섹션이 스크롤 진입하면 "의미가 진행되는" 모션을 갖는다.

### 재사용 Motion primitive — `academy_storefront_motion.js` (`window.F2RStorefrontMotion`)

- `onEnter(el, cb)` — IntersectionObserver 1회. rAF + 4s 재확인으로 이미 뷰포트 안이면 즉시 실행
  (앵커 진입·짧은 페이지·IO 초기 콜백 누락 대비). **scroll 이벤트 없음.**
- `revealSequence(root, {start, step})` — root 안의 `.f2r-reveal`를 DOM 순서대로 stagger 등장(is-in).
  정보 위계는 CSS가 요소 종류별로(제목 무겁게 .62s, 칩 pop scale, CTA 한 박자 뒤) 정한다.
- `processProgress(list, {start, step, linkLead})` — `[data-step]`을 "커넥터 활성(is-linked) →
  linkLead 뒤 카드 활성(is-active)" 순으로 진행. 지나간 단계 is-done, 끝나면 list에 is-complete.
- `INSTANT`(reduced-motion 또는 IO 미지원) → 전부 즉시 최종 상태.

### 적용

- Featured: `revealSequence(.featured-inner)` — eyebrow→title→desc→칩4→CTA.
- AI Native: `revealSequence(.ai-process-head)` + `processProgress(.ai-process-steps)`.
  Desktop 좌→우, Mobile(<640px) 위→아래. connector가 진행의 핵심.
- `academy_public.js`의 `wireStorefrontMotion()`이 `render()`마다 호출. GSAP `applyReveal()`과 분리 —
  이쪽은 `.f2r-reveal`/`[data-step]`, 그쪽은 `[data-fade]`. Featured/AI Native에서 `data-fade` 제거.

### 안전 / 회귀

- 초기 `opacity:0`은 `html.js-storefront-motion` 스코프 아래에만. 모듈 미로드·JS 장애 시 클래스가
  안 붙어 콘텐츠가 정적으로 완전히 보인다. 빌더 캔버스도 이 클래스가 없어 최종 상태로 정적 표시.
- `prefers-reduced-motion: reduce` — CSS `@media`에서 `opacity:1;transform:none;transition:none`,
  커넥터/번호 최종 상태. 콘텐츠 이해가 모션에 의존하지 않는다.
- 성능: IO 2개(Featured head, AI steps). 콜백은 classList 토글만. `transform`/`opacity`만 사용
  (레이아웃/CLS 없음). CSS +~2KB, JS +~1.5KB(gzip 전). 새 라이브러리 없음.
- 캐시버스팅 `v=20260830-3` (css + sections.js + public.js + 신규 motion.js).

### PHASE 2.1 정렬 하드닝(이 PR에 포함)

`.featured-desc`/`.ai-process-desc`/`.ai-process-note` 중앙 정렬(`margin:X auto 0` + `:not()` 리셋 예외,
PHASE 2.1)은 유지. `.f2r-reveal` 초기 transform은 `:not(.is-in)`으로만 걸어 is-in 시 정확히 원위치
(`transform:none`)로 복귀 — per-type 규칙이 resting transform을 남기던 specificity 함정 제거.

## 13. 2026-08-30 변경: PHASE 3-B — Horizontal Content Collection

BuildIt Web V2 구조:  `PAGE → Vertical Story Sections → Horizontal Collection Sections → Content Cards`.
Story(3-A) = "페이지가 보여준다"(자동 모션).  Collection(3-B) = "사용자가 둘러본다"(직접 탐색).
→ 컬렉션 카드에 등장 애니메이션·자동 캐러셀을 넣지 않는다. 사용자 제스처(스와이프/휠 가로/화살표)만.

### 공통 구조 한 벌 —  Collection → head(hint + nav) → viewport → track → card

- 마크업 정본: `academy_sections.js` — `renderCollectionShell()` / `programCardHtml()` / `videoCardHtml()`.
  Program Card / Video Card 는 **같은 shell 위의 card type**이다(Programs용·Videos용 캐러셀을 따로 만들지 않는다).
  다음 단계의 Mini Lectures / School Exam Contents 도 같은 shell + 새 card type 으로 올린다.
- 배선: `academy_collection.js` (`window.F2RAcademyCollection`, 공개 페이지에서만 로드).
  `wire(root)` — 좌/우 화살표는 `track.scrollBy`(한 화면 남기고 이동), Video Card 클릭 → 그 자리 재생
  (업로드 스트림 → `<video>`, 임베드 URL → `<iframe>`). `scroll` 이벤트로 화살표 disabled 상태만 갱신.
  세로 휠을 가로로 강제 변환하지 않는다(native `overflow-x` 우선). 새 제스처/캐러셀 라이브러리 없음.
- CSS: `academy_collection.css` (page-owned, `academy_public.css` 토큰 재사용).
  Desktop(≥761px): 카드폭 300px 고정 → 여러 장 + 다음 카드 peek + 화살표.
  Mobile(≤760px): 화살표 숨김, 카드폭 78vw, `scroll-snap-type:x` + touch 스와이프 + peek.
  페이지 가로 밀림 방지: `.f2r-collection-viewport{overflow:hidden}` + track `overscroll-behavior-x:contain`.
  빌더 캔버스: `data-collection-static` → 화살표 없이 wrap 배치(편집 UX / public presentation 분리).

### opt-in — 다른 학원 사이트 불변

- `sec-lessons` / `sec-videos` 렌더는 `builderDraft.lessonsLayout` / `videosLayout == 'horizontal'` 일 때만
  가로 컬렉션으로 바뀐다. 키가 없으면 기존 레이아웃(수업상품 showcase / 유튜브 재생목록형) 그대로.
- 서버 `BUILDER_DRAFT_KEYS` 에 `lessonsLayout`·`videosLayout` 추가(200자 제한). `sectionVisibility`/`sectionOrder`
  는 건드리지 않는다.
- 빌더: `sec-lessons`/`sec-videos` 섹션 편집 컨트롤에 `레이아웃: 기본 / 가로` 토글 버튼
  (`toggleCollectionLayout` → draft 갱신 + 프리뷰 재렌더 + 발행 시 저장).

### Program Card / Video Card

- **Program Card**(sec-lessons): 표시 우선순위 `kicker → name → learnerNeed → highlight → coreDetailTitle → 상담 CTA`.
  존재하지 않는 필드는 렌더하지 않는다(가짜 target/student 데이터를 만들지 않는다). 상세 펼침 없음 —
  "둘러보기 + 상담 연결"까지만. 공개 CTA 는 `requestConsult()` 버튼, 빌더는 정적 `<span>`.
- **Video Card**(sec-videos = Actual Class Shorts): 기존 업로드 콘텐츠(세로 숏폼 mp4) 보존. thumbnail 중심,
  길이 배지, 클릭 → 그 자리 재생(`data-collection-play` + `data-video-src` 스트림). 방향(width>height)에 따라
  `is-landscape` 비율. YouTube URL 파서(`youtubeVideoId`/`youtubeEmbedUrl`)는 `academy_sections.js` 한 곳에
  centralize — 향후 다른 card type(Mini Lectures 등)이 임베드를 태울 때 공용으로 쓴다.

### Eduholic 적용

- `V955__eduholic_storefront_horizontal_collection.sql` — `handle='eduholic'` 한 행의 `page_settings` JSON 에
  `builderDraft.lessonsLayout='horizontal'` / `videosLayout='horizontal'` 두 키만 `JSON_SET`. 스키마 변경·
  기존 데이터 삭제/변환 없음. handle 이 없으면 아무 행도 바뀌지 않는다.

### 안전 / 회귀

- 배선 모듈 미로드·JS 장애 시: 콘텐츠는 정적 shelf 로 완전히 보인다(화살표만 동작 안 함).
- `prefers-reduced-motion` — 화살표 스크롤은 smooth 대신 즉시 점프.
- 캐시버스팅: `academy_sections.js?v=20260830-4`, `academy_builder.js?v=20260830-2`,
  `academy_public.js?v=20260830-4`, 신규 `academy_collection.js?v=20260830-1` / `.css?v=20260830-1`.
- 검증: `node --test src/test/js/academy_horizontal_collection.test.js` (opt-in 기본 OFF, horizontal 렌더,
  Program 카드 우선순위·빈 필드 생략, Video 카드 재생 앵커·방향 클래스, 공개/빌더 앵커 분리, FA-free,
  가로 밀림 방지 CSS). `AcademyBuilderTemplateContractTest.horizontal_content_collection_shares_one_shell_and_is_opt_in`.

## 14. 2026-08-31 변경: Storefront V2-7 — Program Editor (복잡성은 숨기고 데이터는 유지)

수업상품 편집기(`/tutor/academy/lessons/editor`)를 "필드 나열"에서 "수업 카드를 만드는 도구"로
재구성한다. **필드를 삭제하거나 데이터를 변환하지 않는다** — 라벨·노출·미리보기 표현만 바꾼다.

### 기본 화면 = 수업 카드를 구동하는 핵심 항목만

`fieldset.editor-group-core` 에 6개만 노출한다. 원장 화면 라벨은 DB/내부 명칭이 아니라
이해하기 쉬운 표현을 쓴다(저장 계약의 `name`/`id`/`maxlength` 는 그대로).

| 원장 라벨 | 내부 필드 | Program Card 위치 |
|---|---|---|
| 카드 라벨 | `tabShortName` | eyebrow |
| 수업명 (필수) | `name` | 제목 |
| 한 줄 설명 | `kicker` | 부제 |
| 이런 학생에게 | `learnerNeed` | need |
| 핵심 특징 | `highlight` | highlight |
| 대상 | `gradeLabel` | grade (없으면 `tabGuidanceLabel` fallback) |

대표 이미지·영상은 오른쪽 미리보기 카드를 눌러 등록한다. **CTA 는 데이터 필드가 아니라
고정 동작**(`requestConsult()`)이므로 새 입력을 만들지 않는다.

### 상세 설정 = 나머지 기존 필드 전부 보존

`<details class="editor-advanced">`(기본 접힘) 안에 이동: `theme`, `tabGuidanceLabel`,
`description`, `schoolName`, `priceAmount`/`priceUnit`, `coreDetailTitle`, `explanation1~3`,
그리고 OT 영상 등록 버튼(`#otVideoControl`). hidden input(`mediaImageKey`/`mediaVideoId`/
`otVideoId`)과 저장 요청(`PUT /api/tutor/academy/lessons/collection`)은 불변. AUTHORING_FIELDS
목록·`lesson_product_editor_state.js` 계약은 손대지 않았다.

### 미리보기 = 공개 storefront 와 같은 렌더 계약

"빌더에서 본 카드 ≠ 발행된 카드" 를 구조적으로 없앤다.

- **가로 컬렉션 학원**(`pageSettings.builderDraft.lessonsLayout == 'horizontal'`, 현재 eduholic):
  편집기 미리보기가 `#lessonProgramCardPreview` 에 `F2RAcademySections.programCardHtml(lesson, ctx)`
  를 그대로 호출한다 — 공개 페이지(`academy_public.js`)·빌더 캔버스(`academy_builder.js`)가 쓰는 것과
  **같은 함수 한 벌**. 편집기 ctx 는 슬롯 2개만 채운다: 미디어=클릭 시 미디어 모달, CTA=정적 `<span>`.
  `#courseMediaArea`/`#otMediaArea` 대신 카드 안의 `[data-editor-media]` 트리거와 카드 아래 OT 행을 쓴다.
- **그 외 학원**: 기존 showcase 골격(`#lessonPreview` = selected-hero + detail-section)과 데이터 계약
  그대로. 강제로 V2 Program Card 로 전환하지 않는다.

편집기 페이지가 `academy_sections.js` + `academy_collection.css` 를 로드한다. 카드 CSS 토큰
(`--ink`/`--line`/…)은 `academy_public.css` 가 없으므로 `.program-card-preview` 스코프에 정의.

### 캐시버스팅 / 검증

- `lesson_product_editor.html`: `lesson_product_editor.css?v=20260831-1`,
  `academy_collection.css?v=20260830-5`, 신규 `academy_sections.js?v=20260831-1`,
  `lesson_product_editor.js?v=20260831-1`.
- `node --test src/test/js/lesson_product_editor_program_card_preview.test.js` — 편집기가 정본
  `programCardHtml` 을 호출, draft 필드→카드 구조 매핑, 빈 필드 생략, `gradeLabel`→`tabGuidanceLabel`
  fallback. `LessonProductEditorTemplateContractTest`(핵심 6필드·친화 라벨·상세설정 보존·미리보기 공통화),
  `lesson_product_editor_state.test.js`(저장 계약 불변).

## 15. 2026-08-31 변경: Storefront V2-8 — Common Video Playback + Actual Class Collection

### P0: 두 영역의 재생 확대·crop 버그는 같은 원인이었다

Production 모바일 Eduholic 에서 두 곳이 동일 증상(썸네일 정상 → Play → 플레이어가 프레임보다
크게 렌더 → crop):
- CASE A — Actual Class(Phone Card, `sec-videos` 가로 컬렉션)
- CASE B — 대표 수업(Program Card, `sec-lessons`) 영상(내신 등)

**공통 원인** — `academy_collection.js onPlayClick` 이 미디어 트리거 버튼을
`trigger.replaceWith(<video|iframe class="f2r-video-card-player">)` 로 **치환**했다:
1. 트리거 버튼(`.f2r-phone-screen` / `.f2r-program-card-media`)이 곧 미디어 컨테이너였다 —
   `position`·`aspect-ratio`·`overflow:hidden`·`border-radius` 를 소유. 치환하면 이 컨테이너가
   사라진다.
2. `.f2r-video-card-player { position:absolute; width:auto; height:auto }` — 대체(replaced) 요소
   (`<video>`/`<iframe>`)는 `width/height:auto` 면 `inset` 이 아니라 **원본 해상도**로 렌더된다
   (예: 406×722). → 프레임 밖으로 넘침.
3. Containing block 이 비결정적: Program Card 는 positioned 조상이 하나도 없어(`.page` 까지 전부
   `static`) → **뷰포트** 기준(전체화면). is-dimmed 카드는 `transform` 때문에 카드 기준(crop).
   Phone frame 은 `position:relative` 지만 `overflow:visible` 라 원본크기 영상이 밖으로 흘렀다.

### 공통 해결 — Media Viewport 계약 (재사용 primitive)

```
Media Viewport (.f2r-media-viewport)  — 크기·비율·overflow·radius 소유. 재생 중에도 유지.
├ Thumbnail + ▶  (.f2r-media-play  button, position:absolute inset:0)   재생 전
└ Player          (.f2r-media-player,        position:absolute inset:0)   재생 후
```

- 마크업 정본: `academy_sections.js` `mediaViewportHtml()` — Actual Class(`collectionVideoMedia`)와
  대표 수업 영상(`programCardMedia` VIDEO 분기)이 **모두 이 함수**를 쓴다. `.f2r-phone-screen` /
  `.f2r-program-card-media` 는 이제 `<button>` 이 아니라 유지되는 **뷰포트 `<div>`** 이고, 그 안에
  `.f2r-media-play` 버튼이 들어간다.
- 배선: `academy_collection.js onPlayClick` — 트리거를 치환하지 않고 **뷰포트 안에** `.f2r-media-player`
  를 `appendChild` 한 뒤 트리거를 `hidden`. 중복 재생 가드(`.f2r-media-player` 이미 있으면 return).
- CSS(`academy_collection.css`): `.f2r-media-player { position:absolute; inset:0; width:100%; height:100% }`
  (auto 아님 → 뷰포트를 채움). `video.f2r-media-player { object-fit:contain }` — **원본 비율 보존,
  crop 금지(letterbox)**. `.f2r-video-card-player` 규칙 제거.

### 비율 처리 — 강제 단일 비율 금지

- **Actual Class**: 폰 프레임(9:19)은 유지(디자인). 플레이어 `object-fit:contain` — 세로 영상은
  꽉, 가로 영상은 letterbox(`is-landscape`).
- **대표 수업 영상**: stage 가 **실제 업로드 영상 비율**을 따른다. `AcademyVideoResponse.width/height`
  (공개 ctx `options.videos` 로 조회) → `mediaAspectRatio()` 가 9:16~16:9 클램프한 `--f2r-media-aspect`
  CSS 변수. IMAGE 대표 미디어는 기존 16:10 유지(`aspect-ratio: var(--f2r-media-aspect, 16/10)`).
  → 세로 내신 영상이 pillarbox 없이 stage 를 채운다. 재생 전/후 외곽 크기 동일(zero layout shift).
- YouTube: `data-video-embed` → `<iframe>` + `autoplay=1&playsinline=1&rel=0`. `youtubeEmbedUrl`
  (watch/youtu.be/shorts 지원) 재사용 — 새 파서 없음. (현재 academy video 는 업로드 전용이라
  collection 임베드는 미사용 경로지만 primitive 는 지원.)

### 안전 / 범위

- opt-in 컬렉션(eduholic) 만 영향. 비-horizontal 학원의 `lesson_product_showcase.js` /
  `videoPlaylist` (in-flow `width:100%` 플레이어)는 이 버그가 없어 건드리지 않는다.
- V2-7 Program Editor: `programCardHtml` SSoT 불변. 편집기 미리보기는 `EDITOR_CARD_CTX` 자체 경로라
  영향 없음(회귀 테스트 통과).
- 캐시버스팅: `academy_sections.js?v=20260831-2`, `academy_collection.js?v=20260831-2`,
  `academy_collection.css?v=20260831-2` (public·builder·editor 템플릿).
- 검증: `node --test src/test/js/academy_video_playback.test.js` (primitive·onPlayClick 계약·CSS·
  비율), `academy_horizontal_collection.test.js` (마크업 갱신), 로컬 harness 로 CASE A/B/C 재생 전후
  DOM 실측(플레이어가 뷰포트를 정확히 채움, 외곽 불변, 페이지 가로밀림 없음).

## 알려진 버그 / 후속 이슈 (별도 이슈로 처리)

### [BUG] handle 없는 기존 Academy는 `/builder` 발행(PUT) 시 400 — update 경로에 임시 handle 생성 누락

- **증상**: 기존 Academy(handle=NULL, 경로형 `/academies/{slug}` 유지)를 `/builder`에서 편집 후 발행하면 `PUT /api/tutor/academy`가 **400**으로 실패한다. 프론트는 일반 토스트("텍스트 저장에 실패했습니다")만 잠깐 띄워, 사용자 눈에는 "발행을 눌러도 아무 일도 안 일어남"으로 보인다. (dev-seed `sample-english-academy`가 handle=NULL이라 로컬에서 그대로 재현된다.)
- **근본 원인**: handle 빈 값 처리의 **create/update 비대칭**.
  - `AcademyService.resolveCreateHandle()` — 빈 handle이면 `generateTemporaryHandle()`로 `academy-{hash}` 자동 생성 (정상).
  - `AcademyService.resolveUpdateHandle()` — 빈 handle이면 곧바로 `HandlePolicy.validate(null)` → `IllegalArgumentException("handle(영문 주소)은 필수입니다.")` → 400. **임시 handle 생성 로직이 없다.**
  - 마이그레이션 `V926__add_academy_handle.sql`은 컬럼만 nullable+UNIQUE로 추가하고 **기존 행 backfill은 "별도 마이그레이션(retire 전제조건)"으로 명시적으로 미룸**. 즉 handle=NULL인 기존/시드 Academy가 정상 존재하는데, update 경로가 이를 처리하지 못한다.
  - 표시(display) 레이어는 이미 fallback이 있다: `AcademyBuilderPageController.displayUrl()`은 `handle == null ? slug : handle`. 하지만 저장/발행 검증에는 같은 fallback이 없어 불일치.
- **제안 수정 (별도 이슈에서 구현)**:
  1. `resolveUpdateHandle(rawHandle, academy, tutorUserId)`로 시그니처 확장 후, 빈 handle이고 `academy.getHandle()`도 비어 있으면 create와 동일하게 `generateTemporaryHandle(tutorUserId)`를 반환. 이미 handle이 있는데 빈 값이 오면 기존처럼 거부(빈 값 저장으로 handle 삭제 방지).
  2. (선택) 프론트 `saveTextChanges`/`onPublish`가 400 본문 메시지를 사용자에게 노출하거나, handle 미설정 시 "공개 주소(handle) 수정" 모달을 먼저 강제.
  3. (선택) 기존 handle=NULL 행 backfill 마이그레이션 — V926에서 예고한 retire 전제조건. dev-seed도 handle 채우도록 갱신.
- **검증 기준**: given handle=NULL Academy + request.handle=null → when `update()` 발행 → then 자동 `academy-{hash}` 부여 + 저장 성공(200). AcademyServiceTest에 given/when/then 케이스 추가.
- **범위 밖**: 이번 원장 소개(sec-principal) 작업과 무관한 선재 버그이며, 원장 영상 발행을 검증하려다 드러났다. 원장 영상 임베드(`principalYoutubeUrl` → `youtube.com/embed/{id}` 정규화)는 정상 동작 확인됨.
- **관련**: `AcademyService.java`(resolveCreateHandle/resolveUpdateHandle), `HandlePolicy.java`, `V926__add_academy_handle.sql`, `academy-publish-design.md`.
