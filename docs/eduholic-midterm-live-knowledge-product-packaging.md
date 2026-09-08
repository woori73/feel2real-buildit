# Eduholic Midterm LIVE — Knowledge Product Packaging / Video Display Spec

> 상태: 2026-09-08 합의된 구현 방향
> 적용 대상: `/eduholic-midterm-2026.html`, Midterm LIVE visual work, TUTOR Wiki material presentation, short academy videos
> 관련 문서: `docs/eduholic-midterm-live-final-direction.md`
>
> **MANDATORY READ BEFORE MIDTERM LIVE VISUAL WORK.**
> Midterm LIVE의 자료/영상 UI를 수정하는 모든 작업자는 구현 전에 이 문서와 `docs/eduholic-midterm-live-final-direction.md`를 먼저 읽는다.
>
> **REFERENCE IS THE SPEC.** 추상적인 “Shopify처럼”, “고급스럽게”, “상품처럼”, “동적으로”라는 지시만으로 디자인하지 않는다. 실제 Shopify reference의 화면 틀을 정확히 제공하고 그 틀을 구현한 뒤, product-media 영역에 실제 Wiki HTML 또는 실제 학원 영상을 치환한다.

## 1. 확정된 제품 개념

TUTOR Wiki에 올라온 실제 HTML 학습자료가 **상품의 내용물**이다.

Midterm LIVE는 자료 내용을 다시 설명하거나 재작성하지 않는다. LIVE의 역할은 실제 자료를 학부모에게 친숙한 **상품형 이미지 / 쇼윈도 / collection presentation**으로 포장하고 진열하는 것이다.

```text
TUTOR Wiki HTML = 상품 본체
LIVE product frame = 패키지
school / grade / createdAt = 최소 상품 context
collection = 진열대
짧은 실제 영상 = lifestyle / panorama media
```

대상 학부모가 자료의 문법 내용을 읽고 분석하게 만드는 것이 목적이 아니다. 화면을 보는 순간 “실제 시험 준비 자료가 만들어지고 관리되고 있다”는 것을 시각적으로 확인하게 한다.

## 2. 절대 금지 — 자료 밖에 학습 내용을 다시 쓰지 않는다

상품 frame 바깥에 다음을 새로 만들지 않는다.

- `가정법 과거완료`
- `관계대명사 핵심 정리`
- `학교 프린트 단어 정리`
- 문법 용어
- 문제 유형 설명
- 자료 요약
- 자료에 이미 있는 학습 내용을 다시 설명하는 copy
- AI가 만든 상품명
- NEW badge
- 좋아요/heart icon
- 임의의 “전체 자료 보기” 버튼
- 임의의 화살표 action button
- 가격/cart/quick-add/search/filter

자료 내부에 이미 존재하는 문법명/문제/영어 문장은 그대로 보일 수 있다. 금지 대상은 **LIVE가 그 내용을 바깥에서 다시 포장 문구로 생성하는 것**이다.

외부 metadata는 원칙적으로 다음 정도만 허용한다.

```text
압구정중 2학년 · 9.2 업데이트
신구중 3학년 · 9.3 업데이트
```

API가 실제 제공하지 않는 정보는 만들지 않는다.

## 3. Shopify product collection 틀 — 고정 구조

우리가 원하는 것은 자료 크기에 따라 외곽 레이아웃이 제각각 바뀌는 document collage가 아니다.

**사람들에게 익숙한 Shopify product collection의 상품 이미지 틀을 먼저 만들고, 그 product-media window 안에 실제 Wiki HTML을 넣는다.**

Desktop 기본 구조:

```text
최근 준비 자료

┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│                 │ │                 │ │                 │ │                 │
│  PRODUCT MEDIA  │ │  PRODUCT MEDIA  │ │  PRODUCT MEDIA  │ │  PRODUCT MEDIA  │
│  Wiki HTML      │ │  Wiki HTML      │ │  Wiki HTML      │ │  Wiki HTML      │
│                 │ │                 │ │                 │ │                 │
├─────────────────┤ ├─────────────────┤ ├─────────────────┤ ├─────────────────┤
│ 압구정중 2학년  │ │ 신구중 2학년    │ │ 신구중 3학년    │ │ ...             │
│ 9.2 업데이트    │ │ 9.2 업데이트    │ │ 9.3 업데이트    │ │                 │
└─────────────────┘ └─────────────────┘ └─────────────────┘ └─────────────────┘
```

핵심:

- 외부 product frame은 안정적인 상품 진열 규격으로 고정한다.
- 실제 HTML 자료는 frame 안의 media window에서만 보인다.
- 자료의 원본 비율이 서로 달라도 외부 collection rhythm을 깨지 않는다.
- media window는 `overflow: hidden`.
- 자료 자체가 가장 큰 visual surface다.
- background color, border, spacing, paper depth/shadow는 **패키지 역할**만 한다.
- 장식이 자료보다 강해지면 실패다.
- SaaS dashboard 카드처럼 보이면 실패다.

## 4. 외부 패키지의 역할

상품화는 문구를 추가하는 것이 아니라 **visual container를 제공하는 것**이다.

허용되는 presentation 요소:

- 각 product media 뒤의 차분한 배경색
- 얇은 border
- 일정한 outer radius (실제 reference 기준)
- 충분한 내부 여백
- 종이가 놓인 듯한 아주 약한 depth/shadow
- 실제 school / grade / createdAt metadata

배경색은 product collection을 친숙하게 보이게 하는 데 사용할 수 있다. 예: 매우 옅은 blue / gray / sage / warm neutral. 단, 임의의 화려한 gradient/glow나 자료별 의미 taxonomy를 색으로 만들지 않는다.

각 자료가 서로 다른 “문법 상품”으로 브랜딩되는 것이 아니라 **하나의 Eduholic packaging system 안에서 실제 내용물만 달라지는 구조**다.

## 5. HTML 자료의 동적 움직임

상품 frame은 고정한다. **움직이는 것은 media window 안의 실제 Wiki HTML 자료다.**

개념 DOM:

```html
<article class="knowledge-product">
  <div class="knowledge-product__media">
    <iframe
      class="knowledge-product__document"
      sandbox=""
      tabindex="-1"
      aria-hidden="true"
      scrolling="no">
    </iframe>
  </div>
  <p class="knowledge-product__meta">신구중 3학년 · 9.3 업데이트</p>
</article>
```

개념 CSS:

```css
.knowledge-product__media {
  position: relative;
  overflow: hidden;
}

.knowledge-product__document {
  border: 0;
  pointer-events: none;
  transform: translateY(var(--start-y)) scale(var(--doc-scale));
}
```

Motion rule:

- iframe/document는 viewport보다 크게 렌더링한다.
- 움직임은 주로 `translateY()`만 사용한다.
- 약 7~9초 동안 한 방향으로 약 8~12% 정도 천천히 이동한다.
- 목적은 document viewer scroll이 아니라 **shop-window 안에서 실제 상품 detail이 천천히 지나가는 것**이다.
- 빠른 움직임 금지.
- bounce 금지.
- 무한 왕복 금지.
- scroll-jacking 금지.
- 내부 scrollbar 금지.
- iframe interaction 금지.
- `prefers-reduced-motion: reduce`에서는 대표 crop에 정지한다.

실제 transform/crop 값은 Production 자료를 눈으로 보고 조정한다. 문서의 빈 영역/툴바/표지만 지나가게 만들지 않는다.

## 6. Reference를 개발자에게 주는 방법

앞으로 visual 구현 지시는 반드시 다음 형태로 제공한다.

```text
1. REFERENCE
   실제 Shopify 공식 page URL
   실제 reference screenshot/image
   복제할 정확한 화면 영역

2. IMMUTABLE FRAME
   상품 frame 개수
   media 영역 비율
   card/frame width/height
   gap
   outer padding
   background
   border
   radius
   metadata 위치
   desktop 배열
   mobile 배열/peek

3. CONTENT SUBSTITUTION
   Shopify product image → 실제 TUTOR Wiki safe HTML preview
   Shopify product metadata → 실제 school / grade / createdAt
   Shopify lifestyle media → 실제 Eduholic short video

4. MOTION
   media window 내부 HTML의 start crop
   end crop
   duration
   easing
   iteration count
   reduced-motion

5. FORBIDDEN
   reference에 없는 UI/button/badge/taxonomy/copy/API/user flow 추가 금지
```

개발자가 reference를 보고 새로운 디자인을 해석/창작하는 것이 아니라 **정해진 틀을 코딩**한다.

## 7. “REFERENCE IS THE SPEC”의 구체적 의미

다음 표현만으로 작업을 시키지 않는다.

```text
Shopify스럽게 만들어라
상품처럼 고급스럽게 만들어라
동적인 느낌으로 만들어라
감성적으로 만들어라
지식상품처럼 보여라
```

대신 다음처럼 지시한다.

```text
제공한 Shopify reference screenshot의 product collection frame을 그대로 기준으로 한다.
Desktop에서 reference와 같은 상품 진열 구조를 구현한다.
외부 frame/background/border/media ratio/metadata 위치는 reference가 immutable이다.
product image 영역만 TUTOR Wiki preview iframe으로 치환한다.
iframe은 media window 안에서만 8초 동안 Y축으로 천천히 이동한다.
새 badge/button/title/설명은 만들지 않는다.
```

## 8. 영상 — Panorama / Lifestyle Media

짧은 실제 영상도 LIVE의 중요한 visual 상품이다.

입력:

- 관리자가 직접 업로드
- 실제 수업 장면
- 학생 문제 풀이 장면
- 원장 자료 검토 장면
- 프린트/자료 제작 장면
- 권장 길이 약 5~12초

가능하면 기존 AcademyVideo / Midterm video 자산과 backend를 재사용한다. 별도의 방송/스트리밍/lecture CMS를 만들지 않는다.

공개 화면에서 영상은 “영상 게시판”이 아니다. **Shopify collection 사이에 들어가는 lifestyle/panorama media**로 취급한다.

Desktop 개념:

```text
[ Wiki 상품 ][ Wiki 상품 ][ Wiki 상품 ][ Wiki 상품 ]

┌──────────────────────────────────────────────────────────────┐
│                                                              │
│                   ACTUAL ACADEMY VIDEO                       │
│                     PANORAMA MEDIA                           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
                     실제 날짜/context

[ Wiki 상품 ][ Wiki 상품 ][ Wiki 상품 ] ...
```

영상 기본 behavior:

```html
<video muted autoplay playsinline loop></video>
```

- controls 없음
- playlist 없음
- lecture UI 없음
- chat 없음
- broadcast UI 없음
- progress dashboard 없음
- 큰 play button을 임의로 추가하지 않는다.
- 영상은 상품 이미지처럼 즉시 이해되는 ambient visual이어야 한다.

여러 짧은 영상이 있을 경우 **파노라마형 visual rail / collection band**로 진열할 수 있다. 전체 페이지 narrative는 vertical을 유지하며, horizontal interaction은 collection/browse 용도에만 사용한다.

## 9. 자료와 영상의 최종 visual hierarchy

```text
1. 실제 Wiki HTML 자료
2. 상품형 packaging frame
3. 실제 짧은 학원 영상
4. school / grade / actual date metadata
5. 필요한 최소 campaign copy
```

문구가 visual보다 위로 올라오지 않는다.

부모에게 자료를 설명하는 페이지가 아니라, **실제 준비물을 진열하는 seasonal storefront**다.

## 10. 기존 보안 invariant 유지

상품화 때문에 preview security를 약화하지 않는다.

Wiki iframe:

- server-controlled `previewUrl`만 사용
- `sandbox=""`
- `tabindex="-1"`
- `aria-hidden="true"`
- `scrolling="no"`
- `pointer-events: none`
- storageKey 노출 금지
- filename 노출 금지
- original URL 노출 금지
- download 금지
- public frontend에서 `/api/tutor/*` 직접 호출 금지

자료가 “상품처럼 보인다”는 이유로 클릭/다운로드 가능한 상품으로 만들지 않는다.

## 11. 구현 순서

다음 순서를 권장한다.

### Phase 3A — Knowledge Product Packaging

현재 Production에서 검증된 Wiki → LIVE HTML 연결과 #235 collection을 유지한다.

변경 대상은 presentation layer:

- Shopify product collection frame
- background
- border
- spacing
- paper depth
- fixed media window
- HTML slow motion
- metadata placement

이 단계에서 backend/API/DB/admin/video를 섞지 않는다.

### Phase 3B — Panorama Video

Phase 3A 실화면 확인 후 별도 PR.

- 기존 video backend 재사용 가능성 먼저 조사
- 관리자 short video upload 최소 UX
- public panorama/lifestyle media
- 자료 collection과 동일한 storefront visual grammar

Reference 하나 → 변경 하나 → Production 화면 확인 → 다음 변경.

## 12. 현재 Production 실측에서 배운 점

#235 Production에서 실제 exposed Wiki 자료 4건으로 확인한 결과:

- 실제 자료 자체의 모습은 좋다.
- Wiki → LIVE 데이터 흐름은 성공했다.
- 여러 실제 HTML 자료가 서로 다른 crop으로 정상 표시된다.
- 문제는 자료 내용이 아니라 **외부 presentation/packaging이 부족한 것**이다.
- 현재는 브라우저에 iframe들을 배치한 인상이 남는다.
- 다음 visual 작업은 문법 설명/copy를 추가하는 것이 아니라 **사람들에게 익숙한 상품형 image frame 안에 자료를 넣는 것**이어야 한다.

따라서 단순 crop correction보다 product packaging layer를 먼저 확정하고, 그 frame 안에서 crop/motion을 최종 조정한다.

## 13. 작업 시작 전 체크리스트

Midterm LIVE visual 작업자는 코딩 전에 반드시 답한다.

- [ ] `docs/eduholic-midterm-live-final-direction.md`를 읽었는가?
- [ ] 이 `knowledge-product-packaging` 문서를 읽었는가?
- [ ] 사용할 실제 Shopify reference URL/screenshot이 지정됐는가?
- [ ] reference에서 복제할 정확한 frame 영역이 지정됐는가?
- [ ] product media → Wiki HTML 치환이 명시됐는가?
- [ ] reference에 없는 UI를 추가하지 않는가?
- [ ] 자료 바깥에 문법/학습 설명을 생성하지 않는가?
- [ ] 실제 metadata만 쓰는가?
- [ ] iframe security invariant를 유지하는가?
- [ ] reduced-motion을 지원하는가?
- [ ] video 작업이라면 실제 short video를 lifestyle/panorama media로 취급하는가?

하나라도 불명확하면 임의 구현하지 말고 먼저 보고한다.
