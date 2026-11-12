# HANDOFF — Eduholic Coverflow 3D Geometry / Depth Tuning

작성일: 2026-09-18
인계: 집 PC → 학원 PC
상태: **3D 튜닝 미착수. Production 코드 변경 없음. 본 문서는 인계용 기록のみ.**

---

## 1. CURRENT MAIN

- 최신 main SHA: `c1beb0a` (Merge PR #311: extend f2r-app diagnostic window)
- PR #307 merge: `3b24703f34b64f11fe2f7960d7c637a4b2553f25`
  - 제목: Merge PR #307: restore Eduholic Coverflow and Philosophy Stepper runtime
  - 내용: Coverflow mechanics 정상화 (active index / track translate / center 정렬),
    Philosophy Stepper 정상화 (JSON escape 교정 + directional transition)
- PR #308 merge: `37b140d4458ca277e412c7a53b8b0a0c60d9bf4b`
  - 제목: Merge PR #308: restore approved Preview visual layer for Coverflow
  - 내용: CSS only 1개 파일 (`src/main/resources/static/css/academy_public.css`),
    dark stage + navy gradient + glow/color 복원, JS 0줄 수정
- main에 #308 이후 무관한 변경 포함됨: PR #309/#311 (f2r-app 진단 워크플로, Coverflow 무관)

---

## 2. CURRENT PRODUCTION STATUS

### PASS (건드리지 마라)

- center alignment (active 카드 viewport 중앙, 오차 0px, 기준 ≤2px)
- Next / Prev
- indicator navigation
- drag / swipe (mouse + touch)
- Philosophy Stepper (1→2→3→4→Previous, progress, directional transition)
- dark stage (`#0a0a0c`)
- navy gradient 카드 헤더 (`135deg #1e3f9e → #0f2a7a → #0a1f5c`)
- glow / color (center blue outer glow, white arrows/dots)

### REMAINING (다음 작업 범위)

- **Coverflow 3D depth가 승인 Preview보다 약함.**
  사용자 평가: "처음 Preview의 Coverflow처럼 충분히 입체적이지 않고 되다가 만 수준"

---

## 3. SOURCE OF TRUTH

- Design Lab branch: `experiment/design-lab-21st-react`
- approved commit: `1e17d9a67c692b9b34e31dfec0c241792fd0e190`
- route: `/design-lab/eduholic-preview`
- 21st.dev reference: `@ruixen.ui/coverflow-carousel`, id `23997`
- 원칙: CONTENT = 현재 Production 실제 데이터, MECHANICS = 현재 Production(PR #307),
  VISUAL/GEOMETRY = 승인 Preview 1e17d9a6. Preview mock 콘텐츠를 Production에 넣지 마라.

### Preview 실측済み 파라미터 (SchoolB.tsx `CoverflowCarousel` @1e17d9a6)

- defaults: `rotate=44`, `depth=0.6`, `perspective=3`, `falloff=0.56`,
  `fade=0.1`, `cardWidth='clamp(148px, 22vw, 260px)'`, `gap=0.05`, `loop=true`
- paint 식 (DOM 직접 기록, 리렌더 없음):
  - `offset` = ring-fold loop 보정된 index 거리
  - `tilt = min(44 * distance^0.56, 82) * sign(offset)`
  - `transform = translateX(calc(-50% + offset*pitch)) translateZ(-0.6*width*distance^0.56) rotateY(-tilt)`
  - `opacity = max(0, 1 - 0.1*distance)`, `zIndex = 100 - round(distance)`
- frame: `perspective: calc(var(--cf-card) * 3)`, inner `preserve-3d`
- 카드: `aspect-square`, `rounded-2xl`(16px), `bg-navy(#0f2a7a)`, border 없음
- 참고: Preview 카드는 정사각형 mock 타이포 카드. Production은 실제 분석 콘텐츠 tall 카드이므로
  치수는 직접 이식 불가 — **각도/깊이/간격의 느낌을 맞추는 것이 목표.**

### Production 현재값 (academy_public.css, Desktop ≥900px — 수정 전 기준점)

- `.coverflow-viewport`: `perspective:1200px`
- `.coverflow-track`: `gap:32px` (기본 24px, 모바일 16px)
- center: `translateZ(80px) scale(1.2)`
- side: `translateZ(0) scale(0.8) rotateY(±15deg)`
- far: `translateZ(-60px) scale(0.65) rotateY(±30deg)`
- 모바일(≤640px): center `translateZ(40px) scale(1.1)` /
  side `scale(0.9) rotateY(±10deg)` / far `translateZ(-30px) scale(0.75) rotateY(±20deg)`

---

## 4. NEXT TASK — COVERFLOW 3D GEOMETRY / DEPTH TUNING

Preview와 Production을 Chromium 1280에서 실제 측정하여 비교한다:

- perspective
- card gap
- center translateZ / scale
- side rotateY / translateZ / scale
- far rotateY / translateZ / scale
- opacity / filter
- visible overlap (카드 간 겹침량)
- center-to-side distance (중앙→옆카드 중심 간격)

그 후 **CSS 중심으로 3D geometry만** 수정한다.
(값은 렌더 측정 후 결정. 아래 참고값을 바로 하드코딩하지 마라.)

---

## 5. DO NOT TOUCH

- track positioning JS (`updateTrackPosition`, `(viewportWidth - cardWidth)/2` 계산)
- center alignment JS
- active index 상태
- Next / Prev / indicator / drag / swipe 배선
- Production 실제 데이터 (`/api/analyzes`, 학교 데이터, Wiki/분석 URL, video URL/API, 상담/수업/커리큘럼)
- Philosophy Stepper
- HERO
- AI NATIVE
- Builder (`builder-public-preview` 오버라이드 포함)

---

## 6. 이전 QA 참고 (측정済み, 재측정 불필요)

PR #307 과정에서 Chromium 실측으로 확인된 geometry (Desktop 1280):

- center: `translateZ` 약 80px, `scale` 약 1.2
- side: `rotateY` 약 ±19deg (matrix3d 역산)
- far: `rotateY` 약 ±42deg, `translateZ` 약 -60px

단, 다음 작업에서 이 값을 바로 하드코딩하지 말고 Preview 실제 렌더 측정 후 결정한다.
(위 값은 Production 렌더 결과이지 Preview 승인값이 아니다.)

### QA 측정 시 주의 (2026-09-18 확인済み)

- Playwright `scroll_into_view_if_needed()`는 페이지 `scroll-behavior:smooth`와 만나
  headless Chromium에서 합성 지연을 일으켜 `getBoundingClientRect()`가 stale 값을 반환한다
  (computed transform은 정상인데 rect가 어긋남).
- 해당 현상은 origin/main CSS와 수정 CSS에서 **동일하게** 재현되어 코드 변경과 무관함을 입증済み.
- 정확한 측정법: `window.scrollTo({top: ..., behavior: 'instant'})` 사용 후 측정.
- drag 테스트 좌표는 `[data-coverflow-track]` bbox가 아니라
  `[data-coverflow-viewport]` bbox 기준으로 계산할 것
  (track bbox는 translateX가 포함된 변환済み 좌표라 화면 밖 좌표가 나온다).
- 팝업 `#eduholicMidtermPopup`이 포인터를 가로채므로 측정 전 `.emp-close`로 닫을 것.

---

## 7. ACCEPTANCE

Desktop 1280:

- center 카드가 확실하게 앞으로 돌출
- side 카드가 center 방향으로 명확히 꺾임
- far 카드가 공간 뒤로 후퇴
- 평면 carousel처럼 보이지 않음
- 카드 이동 시 3D 공간을 통과하는 느낌

Mobile 375:

- clipping / overflow 없음

Interaction regression (매 수정 후 전수):

- Initial / Next / Next / Prev / indicator / drag 모두 PASS
- active center difference ≤ 2px
- console error 0 (미로그인 `/api/auth/me` 401 제외), pageerror 0, horizontal overflow 0

---

## 학원 PC 시작 명령

```powershell
cd C:\Users\sxxra\Documents\feel2real-buildit  # 학원 PC 경로에 맞게 조정
git fetch origin main
git log --oneline -3 origin/main               # c1beb0a 이후 변동 확인
git checkout -b fix/eduholic-coverflow-3d-geometry origin/main
git show 1e17d9a67c692b9b34e31dfec0c241792fd0e190:frontend/eduholic-gallery/src/components/school/SchoolB.tsx
```

이 HANDOFF branch (`chore/handoff-coverflow-3d-20260918`)는 문서のみ이므로 이어서 작업할 필요 없음.
