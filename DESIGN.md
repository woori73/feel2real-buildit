# Design

## Source of truth
- Status: Draft
- Last refreshed: 2026-07-26
- Primary product surfaces: public landing/search pages, student dashboard, conversation workspace, tutor/admin management pages.
- Evidence reviewed:
  - `src/main/resources/templates/fragments/_sidebar.html`
  - `src/main/resources/templates/student/*.html`
  - `src/main/resources/static/css/conversation_dashboard.css`
  - `src/main/resources/static/css/conversation_materials.css`
  - `src/main/resources/static/css/conversation_review.css`
  - `src/main/resources/static/css/stdt_dashboard.css`
  - `src/main/resources/static/css/tutor_academy.css`
  - `src/main/resources/templates/tutor/lesson_product_editor.html`
  - `src/main/resources/static/css/lesson_product_editor.css`
  - `src/main/resources/static/js/lesson_product_editor_state.js`
  - `src/main/resources/static/css/exam_review.css`
  - `docs/archive/archived_html/new_landing_0722.html`
  - `src/main/resources/templates/public/landing.html`
  - `src/main/resources/static/css/landing.css`

## Brand
- Personality: practical, trustworthy, student-friendly, study/workspace oriented.
- Trust signals: clear hierarchy, calm blue accents, restrained cards, predictable actions.
- Public `/` product name: `모두의 학원`. The F2R logo remains the corporate trust mark in the header and shared footer.
- Avoid: decorative redesigns that reduce readability or hide learning actions.

## Product goals
- Goals:
  - Keep desktop workflows intact.
  - Make core pages usable on portrait phones before a full mobile design system exists.
  - Ensure students/tutors/admins can still navigate, upload, review, and manage records on small screens.
- Non-goals:
  - Pixel-perfect native-app redesign.
  - Replacing current templates or controller routing.
  - Introducing a new component framework.
- Success signals:
  - 390px-430px widths do not require horizontal page scrolling for core flows.
  - Primary CTAs remain visible and tappable.
  - Modals, file inputs, cards, and navigation remain operable.

## Personas and jobs
- Primary personas: students, tutors, admins.
- User jobs: find/open a learning record, upload/review files, complete homework, inspect wrong notes, manage academies/lessons/students.
- Key contexts of use: desktop-first study sessions plus opportunistic phone checks/uploads.

## Information architecture
- Primary navigation: role-specific headers and conversation sidebar.
- Core routes/screens: `/stdt_dashboard`, `/conversation/*`, `/exam_review`, `/academies`, `/lesson-search`, tutor/admin list pages.
- Content hierarchy: current desktop hierarchy remains source of truth; mobile stacks sections vertically and keeps page title + primary CTA above content.

## Design principles
- Principle 1: Mobile fallback first — usable beats visually final.
- Principle 2: Preserve desktop behavior — responsive rules should not change data flow or permissions.
- Tradeoffs: conversation sidebar becomes a horizontal top rail on mobile instead of a separate hamburger overlay to avoid JS-heavy navigation state.

## Visual language
- Color: keep existing F2R blue accents and white cards. The public `/` landing uses a lighter `#2F6FED` blue with a restrained gold CTA accent based on the 2026-07-22 reference.
- Typography: existing system fonts; reduce large headings only on narrow screens.
- Spacing/layout rhythm: desktop spacing preserved, mobile uses 16px page gutters and tighter card padding.
- Shape/radius/elevation: keep current card radius/elevation, reduce oversized shadows only when necessary.
- Motion: public marketing pages may use non-essential CSS-only rotation/ticker motion with a `prefers-reduced-motion` fallback; avoid hover-dependent interactions on touch.
- Imagery/iconography: keep existing emoji/icon usage.

### Public root landing (`/`)
- The 2026-07-22 visual direction is a direct marketing page: a video-backed hero, blue/gold CTAs, compact feature cards, real product-demo videos, beta voice cards, and simple three-step onboarding.
- The F2R trust mark is paired with a segmented `모두/의/학원` product wordmark in the header, and the same non-essential reveal/spring/fade sequence is reused in the hero with a reduced-motion fallback. Hero CTA destinations remain `#example` and `#builder-try`.
- At desktop widths, the live Builder stays a compact two-column experience, shorts use four centered cards plus a full-width channel card, and the report cards balance against the existing square demo video without replacing or cropping its asset.
- The real `#builder-try` form and live preview remain the homepage example. Styling and adjacent marketing videos must not break the existing `/builder` handoff contract (`academyNameInput`, `sloganInput`, `subjectBtns`, `tuitionInput`, `publishBtn`).
- Production navigation reuses the shared account controls/login modal. Production footer content reuses the shared public footer and contact modal.
- Academy-owned public home and academy blog pages use an academy-specific business footer as fixed page chrome after ordered content. It cannot be hidden, deleted, restyled, or reordered. Seven direct business fields come from raw `pageSettings.businessInfo`; the FTC verification URL is derived from ten registration-number digits. Null/blank raw values display masked dummy values without persisting them. Builder selection follows the same field-scoped inline text UX as other sections: only the seven value texts become `contenteditable`, yellow field affordances identify editable targets, and the footer gets a top-right Apply control instead of a separate form/modal. Never copy the shared public footer's real business values into an Academy fallback.
- Do not ship reference placeholders such as `/signup`, `/assets/*`, EmailJS demo keys, or client-only fake feedback submission as working product behavior.

### Tutor lesson product editor (`/tutor/academy/lessons/editor`)
- Decision: lesson authoring uses a dedicated collection editor instead of create/edit modals inside Academy management or lesson operations.
- Desktop hierarchy: wrapping lesson tabs first, then one left authoring form and one sticky right preview. At 980px the panels stack; tabs use 4 columns by default, 2 at 900px, and 1 at 560px.
- Tabs are native buttons inside a labeled `nav`; the active tab uses `aria-current`, and the add button remains the final focusable tab. Switching tabs preserves each lesson draft while keeping a single form.
- The add tab shares the sibling tabs' two-line `small` + `strong` structure (`새 수업` / `+ 추가하기`) so line grid and type hierarchy stay uniform. Uniformity comes from markup only; the add tab gets no accent styling of its own beyond the shared typography and `:disabled` rules.
- Enter never submits the form from a single-line control. `textarea` keeps newline entry and buttons keep intentional activation, so persisting a lesson is always an explicit save.
- Typing updates the preview immediately: each keystroke moves the active draft into the rendered preview, refreshing both the right-hand preview and the tab copy. There is no separate apply step, so no preview-dirty indicator.
- Save is one atomic collection request. Validation, stale revision, lock/race, and network failures keep every local draft. Stale recovery updates only server snapshots/revisions and asks the tutor to review before retrying.
- Persist stable theme keys (`NAVY`, `TEAL`, `PURPLE`, `ORANGE`, `SKY`, `PINK`, `GREEN`), not CSS values. Background style is academy-wide, not per lesson: it sits in a `공통 디자인` section above the lesson fields and writes the same key to every lesson draft.
- The left panel collects only what the client's reference layout asks for: tab guidance label, tab short name, product name, kicker, summary, highlight, learner need, core detail title, and three explanations, plus the background style. Lesson type, target group, publish state, price, capacity, and schedule are not collected here and go to the server as null; publish state is normalized to `PUBLISHED`. Product name is the only required field.
- Operational values stay on the lesson entity but are owned elsewhere. A null capacity means unlimited enrollment, so public cards omit the capacity line and keep the apply action enabled.
- AI assistance is visibly and semantically disabled. Samples, media upload, raw JSON editing, and automatic live preview are outside this implemented editor.
- The secondary action next to save deletes the selected lesson after a confirmation. Existing lessons go through the hide endpoint; unsaved drafts only drop their tab; removing the last one leaves a fresh empty draft.
- The Academy card body still opens operational detail; create/edit actions enter the dedicated editor. Delete/hide, applications/students, community entry, and public/Builder legacy cards keep their existing behavior.


## UI shell and account-action contract
- Area-level visual systems may differ. Admin can stay management-tool oriented, public pages can stay landing/marketing oriented, student pages can stay learning-dashboard oriented, and tutor pages can stay operation/content-management oriented.
- Shared requirement: every primary authenticated surface must offer account escape hatches within the visible shell or page topbar. Prefer the shared account dropdown over exposing multiple account buttons directly.
  - Logout.
  - `/my/profile` profile access.
  - A role-appropriate home/dashboard route or obvious service navigation path.
- Shared account dropdown: `templates/fragments/_account_menu.html` with assets loaded by `fragments/_head :: favicon` (`/css/account_menu.css`, `/js/account_menu.js`).
- New authenticated pages should either reuse an existing shell fragment or include the shared account menu in the first visible navigation/topbar region.
- Public/anonymous pages do not need logout/profile actions, but login-state CTAs should still route users to their role home when possible.

### Admin UI shell
- Admin pages must use `templates/fragments/_admin_topnav.html` for shared topbar/nav.
- Include the shared admin head in `<head>`:
  ```html
  <th:block th:replace="~{fragments/_admin_topnav :: head}"></th:block>
  ```
- Include the admin topnav near the beginning of `<body>`:
  ```html
  <th:block th:replace="~{fragments/_admin_topnav :: topnav('students')}"></th:block>
  ```
- Supported `activeMenu` values: `students`, `academies`, `analyzes`.
- The admin shell owns logo, admin identity, shared account dropdown, and primary admin nav. Body layout, cards, tables, and modals remain page-owned unless a later admin design pass explicitly extracts them.

## Components
- Existing components to reuse: `.app-shell`, `.sidebar`, `.main`, `.main-header`, `.card`, `.materials-grid`, `.lesson-list`, `.modal-*`, `.lm-modal`, `.assign-modal`.
- New/changed components: shared account dropdown, responsive sidebar top rail, mobile full-width card/action stacks, mobile-safe modals/lightboxes.
- Variants and states: loading/empty/error states stay visible; actions stack vertically if width is insufficient.
- Token/component ownership: page CSS remains owner for now; no new global CSS bundle until a broader design-system pass.

## Accessibility
- Target standard: pragmatic WCAG-friendly baseline.
- Keyboard/focus behavior: do not remove focusable anchors/buttons; maintain scrollability for nav rails and modals.
- Contrast/readability: preserve existing color contrast; avoid tiny text below 12px on mobile.
- Screen-reader semantics: keep existing headings/buttons/links.
- Reduced motion and sensory considerations: no new essential animation.

## Responsive behavior
- Supported breakpoints/devices:
  - `1024px`: tablet/narrow laptop.
  - `768px`: phone/portrait fallback begins.
  - `480px`: small phone tightening.
- Layout adaptations:
  - Sidebars become horizontal sticky nav rails.
  - Two-column grids collapse to one column.
  - Header actions wrap or stack.
  - Modals use near-full viewport width/height and scroll internally.
  - Tables/list rows become stacked cards where possible.
- Touch/hover differences: keep hover as enhancement only; all important actions must be visible without hover.

## Interaction states
- Loading: existing placeholders remain centered and full-width.
- Empty: empty cards should not collapse to zero height.
- Error: existing alert/error blocks remain visible.
- Success: existing success text/badges unchanged.
- Disabled: disabled buttons remain visibly dimmed.
- Offline/slow network: no new offline behavior in this pass.

## Content voice
- Tone: direct, concise Korean microcopy.
- Terminology: `학습기록`, `학원 찾기`, `숙제 및 피드백`, `오답 노트` stay consistent. On `/`, use `모두의 학원` for the academy-homepage product and retain F2R as the corporate brand.
- Microcopy rules: avoid introducing new labels unless layout requires shorter mobile text.

## Implementation constraints
- Framework/styling system: Spring MVC + Thymeleaf + static CSS/vanilla JS.
- Design-token constraints: no Tailwind/design token migration in this pass.
- Performance constraints: CSS-only responsive behavior preferred; minimal JS only when navigation state requires it.
- Compatibility constraints: modern mobile Safari/Chrome baseline.
- Test/screenshot expectations: build must pass; manually inspect `localhost` at ~390px when possible.

## Open questions
- [ ] Whether conversation mobile nav should eventually become bottom tabs or hamburger drawer / owner: product / impact: medium visual/IA change.
- [ ] Whether admin/tutor tables require dedicated mobile card components / owner: product / impact: medium management UX.
- [ ] Whether tutor pages need their own `_tutor_topnav` fragment or should keep page-owned topbars with the account-action contract / owner: product / impact: low-medium consistency change.
