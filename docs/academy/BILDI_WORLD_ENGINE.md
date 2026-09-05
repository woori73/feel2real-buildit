# Bildi(빌디) & Academy World Engine

BuildIt Academy 전용 캐릭터형 AI Sidekick과, Builder Preview / Published Academy가 공유하는
World/Environment layer의 설계 문서.

> PR #184(Phase 5B, AI Guided Academy Builder)의 backend/8-step/session/Publish safety를 **그대로
> 재사용**한다. 이 작업은 Guide의 **Presentation / Interaction layer**만 "정적 패널 → 살아 움직이는
> Bildi Sidekick"으로 바꾼다. Backend AI를 다시 만들지 않는다.

---

## 1. 캐릭터: Bildi / 빌디

첨부 시안(`Bildi character sheet`)을 공식 visual reference로 사용한다. 시안 이미지를 그대로
페이지에 삽입하지 않고, 아래 특징으로 **BuildIt 고유 캐릭터**를 SVG로 제작한다.

| 항목 | 값 |
|---|---|
| 종 | 귀여운 병아리 |
| 비율 | 큰 머리 · 작은 몸 · 큰 눈 · **작은 날개**(접힘 기본) |
| 컬러 | body `#ffd23f`→`#f5b716`, 볼 `#ff9db0`, 부리 `#ff9f1c` |
| 포인트 | BuildIt-blue 스카프/케이프(`#4A7FD4`→`#0C238A`) + 가슴 배지, 이마 고글 |
| 톤 | 활기찬 판타지 러닝게임 캐릭터의 역동성 (윈드러너류는 **grammar만** 연구, asset·그래픽 복제 없음) |
| 이름 | Bildi / 빌디 |

Visual asset 구조(§7): `createBildi()` 가 `img.bildi-sprite`(state별 Production asset) + 인라인
`BILDI_SVG` fallback 을 함께 만든다. 1차 asset 은 `src/main/resources/static/img/bildi/bildi-<state>.svg`
8종(`dev-assets/bildi/generate.mjs` 생성, transparent, 커밋됨). 아트 패스에서 같은 파일명의
transparent WebP/PNG(@2x)로 덮으면 코드 변경 없이 반영된다. **state machine · scroll story ·
movement 로직은 visual 교체와 무관하게 그대로.** emoji/🐣/CSS 원 아님.

### Signature interaction (§2)
평소 날개 접힘 → 사용자가 스크롤 시작 → `WINGS_OPEN` → 날개 크게 펴고 `FLY` → 다음 섹션 접근
→ `LAND` → `POINT`/`TALK` → 섹션 콘텐츠 reveal → `IDLE`. 위로 스크롤하면 `data-facing="up"`으로
방향을 바꿔 위쪽 섹션으로 이동. 빠른/느린 스크롤은 `speed` 버킷(slow/normal/fast)으로 비행 강도만 조절.

---

## 2. Architecture

```
Academy World Engine  (academy_world.js  →  window.F2RAcademyWorld)
├─ Environment Engine   createSky(host, {scenes})        scene registry + 크로스페이드
├─ Bildi Character      createBildi(host)                인라인 SVG + 상태머신 + 반응
├─ Scroll Story Engine  createScrollStory({scrollEl,...}) 스크롤=세계 진행(rAF-coalesced + IO)
└─ Dialogue Engine      createDialogue(host)             Bildi tether 말풍선(say + actions)

academy_world.css   표현(수치): scene 그라데이션/파티클, 날개/표정 keyframe, 말풍선, reduced-motion

Creator Mode (Builder)   → academy_builder.js  wireAiGuide()  가 위 엔진들을 mount·구동
Visitor Mode (Published) → §6 연결 지점(이번 PR 미배선)
```

- **Scroll = 세계 진행 / Time = 세계가 살아 움직이는 CSS 애니메이션**(scene 내부 구름·빗방울·별빛).
  큰 환경 전환은 30초 timer가 아니라 **스크롤 progress**가 결정한다(§6).
- 모든 애니메이션은 **CSS transform/opacity**. rAF는 scroll 이벤트 coalescing 1곳에만.
- `prefers-reduced-motion` / 저사양(`deviceMemory<=2` or `hardwareConcurrency<=2`) →
  `reducedOrLowPower()` true → 이동/파티클 없이 즉시 최종 상태.

---

## 3. Bildi 상태머신 (`BILDI_TRANSITIONS`)

상태: `IDLE RUN WINGS_OPEN FLY LAND POINT TALK THINK HAPPY WAIT`

| from | event | to |
|---|---|---|
| idle / land / point / talk | `scrollStart` | wings_open |
| wings_open / run | `move` | fly |
| fly | `arrive` / `scrollStop` | land |
| land / wings_open | `settle` | idle |
| * | `point` `talk` `think` `happy` `wait` | 해당 상태 |

`nextBildiState(state, event)` 는 순수 함수 — 단위 테스트로 계약 고정(`academy_world.test.js`).
scene 반응은 `reactionForScene(sceneId)` → `shake`(rain) / `gaze`(night·stars) / `drift`(clouds·wind)
/ `dust`(ash) / `float`(space). CSS `.bildi[data-react=...]`가 절제된 강도로 표현.

---

## 4. Scroll Story Engine

`createScrollStory({ scrollEl, sections, onUpdate })`:
- `scrollEl` — Builder는 `#pageContent`(overflow:auto), Published는 `window`.
- scroll 이벤트를 `requestAnimationFrame`으로 coalesce(매 프레임 루프 없음) → `onUpdate({progress,
  direction, speed, activeSection, reduced})`.
- `IntersectionObserver`(threshold 0.25/0.5/0.75, root=scrollEl)로 현재 섹션 판정.
- `detach()` / `refresh()` — CSR 재렌더마다 refresh, 이전 IO·리스너 정리.

progress 구간별 scene(`DEFAULT_SCENES`, override 가능):

| progress | scene |
|---|---|
| 0.00–0.14 | 맑은 아침 morning |
| 0.14–0.30 | 화창한 낮 day |
| 0.30–0.44 | 구름 clouds |
| 0.44–0.56 | 비 rain |
| 0.56–0.70 | 비 갠 노을 sunset |
| 0.70–0.86 | 밤 night |
| 0.86–1.00 | 별 / 우주 stars |

확장 예정 scene: `forest` `wind` `ash`(화산 fantasy event) `clouds-top`. `registerScene` 없이도
`createSky({scenes:[...]})` 로 소비자가 자신의 registry를 넘긴다.

---

## 5. 홈페이지 콘텐츠 Scroll Reveal (§8)

**새 reveal 엔진을 만들지 않는다.** 이미 있는 `F2RReveal`(`academy_reveal.js`,
`[data-reveal]`/`[data-reveal-item]`, 1회성, IO, reduced-motion 즉시, 3초 failsafe)를 재사용한다.

- Published: `academy_public.js`가 이미 `F2RReveal.wire(root)` 호출 중.
- Builder(이번 PR): `renderPublicPreview` 후 `F2RReveal.wire(pageContent)`를 호출해 프리뷰에도 동일
  staged reveal을 건다. Bildi가 섹션에 도착 = 섹션이 뷰포트 진입 = reveal 발화가 자연히 동기화된다.
- **실제 DOM/content/accessibility는 그대로**. reveal은 opacity/transform presentation layer.
  JS 실패 시 `html.js-reveal` 게이트가 안 붙어 콘텐츠가 정적으로 완전히 보인다.

---

## 6. Visitor Mode 연결 지점 (이번 PR 미배선 — 설계만)

Published Academy에 Bildi를 붙이려면 `academy_public.js` `render(academy)` 끝에서:

```js
// (가칭) wireVisitorSidekick(academy)
const WORLD = window.F2RAcademyWorld;
if (WORLD && !ownAcademy) {
  const sky   = WORLD.createSky(document.querySelector('.page'), { className: 'world-sky-visitor' });
  const stage = /* .page 위 pointer-events:none 오버레이 */;
  const bildi = WORLD.createBildi(stage);
  const bubble = WORLD.createDialogue(stage);
  const story = WORLD.createScrollStory({
    scrollEl: null,                       // window 스크롤
    sections: () => lastSectionOrder.map(id => root.querySelector('#' + id)).filter(Boolean),
    onUpdate: ({ progress, activeSection, direction, speed }) => {
      sky.setProgress(progress);
      bildi.face(direction === 'up' ? 'up' : 'down');
      moveBildiToSection(activeSection);
      bubble.say(visitorLineFor(activeSection, academy));  // ← 실제 등록 정보만
    }
  });
}
```

**Visitor Mode 원칙:**
- `visitorLineFor(section, academy)` 는 **실제 등록된 Academy 정보만** 문자열로 조립한다
  (원장 이름/소개, 프로그램 개수, 상담 연락처 유무 등). AI 호출 없음. 없는 정보를 지어내지 않는다.
- 소유자(`ownAcademy`)에게는 Bildi를 띄우지 않거나 Creator Mode 안내로 대체.
- 상담/신청 버튼을 Bildi가 가리지 않도록 `pointer-events` 및 mobile 위치 조정.
- `?bildi=off` 쿼리 / localStorage opt-out 제공.

**미배선 이유:** Published 렌더 경로 전체(다국어 문구, 섹션별 대사 카피, QA, 성능 측정)를 한 PR에
넣으면 scope가 과하다(§9). 공통 엔진 + Creator Mode를 먼저 안정화한다.

---

## 7. 캐릭터 Visual Asset 구조

### 7.0 로딩 구조 (Phase 5B.1a)

`createBildi()` 는 캐릭터를 3층으로 만든다:

```
.bildi                         ← 이동/상태(transform, data-state) — 여기에 상태머신·movement 로직
└ .bildi-art
  ├ img.bildi-sprite           ← Production asset. state 마다 src 교체
  └ .bildi-fallback > svg      ← 인라인 fallback (BILDI_SVG). 이미지 미로드/비활성 시
```

- `setState(s)` → `img.src = bildiAssetUrl(s)` → `load` 되면 `.bildi.bildi-has-sprite` (sprite 표시, fallback 숨김), `error` 면 클래스 제거(fallback SVG 표시).
- `bildiAssetUrl(state, {assetBase, assets})` — `assets` (state→url) 또는 `assetBase + BILDI_STATE_ASSETS[state]`.
- `.bildi` 컨테이너 애니메이션(bob/hop/land, 이동 transform)은 sprite/fallback **양쪽에** 적용된다 → **state machine · scroll story · movement 로직은 visual 교체와 무관하게 그대로**.

### 7.1 상태별 asset (1차 — 벡터, 커밋됨)

`dev-assets/bildi/generate.mjs` 로 생성 → `src/main/resources/static/img/bildi/` :

| 파일 | state | 포즈 |
|---|---|---|
| `bildi-idle.svg` | idle (+ `wait` alias) | 날개 접힘, 발 딛고 서서 |
| `bildi-wings-open.svg` | wings_open | 날개 반쯤 펴는 순간, 눈 크게 |
| `bildi-fly.svg` | fly (+ `run` alias) | **날개 크게 펼침**, 앞으로 기울임, 발 오므림, 속도선 |
| `bildi-land.svg` | land | 착지(반 접힘, 발 벌림, 먼지) |
| `bildi-point.svg` | point | 한쪽 날개 들어 가리킴, 시선 위 |
| `bildi-talk.svg` | talk | 입 벌리고 말하기, 날개 살짝 앞 |
| `bildi-think.svg` | think | 갸웃 + 생각 방울, 시선 위 |
| `bildi-happy.svg` | happy | 날개 위로, 눈 웃음(^ ^), 폴짝, 반짝 |

전부 200×200 viewBox, transparent. 캐릭터: 노란 병아리 · 큰 눈 · 이마 aviator 고글 · BuildIt-blue 스카프 + 흰 원 안 navy 다이아 배지 · 정수리 tuft. **비행 시 날개가 크게 펼쳐지는 것이 signature.**

### 7.2 Production 래스터 교체 (아트 패스)

같은 파일명으로 **transparent WebP(권장) 또는 PNG @2x** 를 `img/bildi/` 에 덮으면 코드 변경 없이 반영된다:

- 파일명 유지 + 확장자만 바꾸려면 `createBildi(host, { assetBase, assets: { fly: '/img/bildi/bildi-fly.webp', … } })` 로 넘기거나, `BILDI_STATE_ASSETS` 파일명을 `.webp` 로 수정.
- 권장 캔버스 **256×256**(캐릭터 여백 ~12%), 8개 state 간 무게중심·시선·스카프 위치 일관.
- 프레임 애니메이션이 필요하면 sprite sheet + `background-image` steps 로 확장(현재는 state당 1장).
- 이미지가 하나라도 없으면 그 state만 자동으로 인라인 fallback SVG.

### 7.2 표정 스프라이트 (선택 — 눈/입만 교체)
`expr-default` `expr-happy` `expr-wink` `expr-think` `expr-surprised`

### 7.3 환경별 코스튬/반응 (선택 — §7)
| key | 상황 |
|---|---|
| `bildi-rain` (+우산) | 비 |
| `bildi-night` (+작은 랜턴 light) | 밤 |
| `bildi-wind` | 강풍에 밀림 |
| `bildi-snow` (+니트) | 눈 |
| `bildi-ash` (+먼지 터는 동작) | 화산재 |
| `bildi-space` (+헬멧, zero-g) | 우주 |
| `bildi-sleep` | 졸림 |

### 7.4 환경(Environment) 배경
이번 PR은 순수 CSS 그라데이션+파티클로 7 scene을 구현. 고품질이 필요하면 scene별
**시차(parallax) 레이어 3장**(far sky / mid clouds / near foreground)을 1600×900 WebP로 발주하고
`.world-sky[data-scene="..."]`의 `background-image`만 교체한다(JS 변경 불필요).

---

## 8. 성능 / 접근성 체크리스트 (§11)

- [x] desktop / mobile 레이아웃 분기 (`@media (max-width:980px)`)
- [x] `prefers-reduced-motion` → 이동/파티클 정지, scene 즉시 전환
- [x] 저사양 힌트 → `world-sky-reduced` / `bildi-reduced`
- [x] rAF 남용 금지 — scroll coalescing 1곳, 매 프레임 측정 루프 없음
- [x] IntersectionObserver로 섹션 판정 (scroll 이벤트 최소화)
- [x] Bildi `pointer-events:none`, 말풍선 버튼만 `auto` — 실제 버튼 클릭 안 막음
- [x] 배경 대비: scene 위에 콘텐츠 카드가 항상 불투명, 텍스트 readability 유지
- [x] JS 실패/비활성 → World layer 미생성, 콘텐츠 정상 노출, layout shift 없음(오버레이는 absolute)
- [x] CSS transform/opacity 우선 (레이아웃 트리거 애니메이션 없음)
