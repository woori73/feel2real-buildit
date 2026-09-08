# Eduholic 2026 2학기 중간고사 LIVE — 최종 구조 및 구현 원칙

> 상태: 2026-09-07 합의된 방향 기록
> 대상: `/eduholic-midterm-2026.html`, `/lms/eduholic/midterm-live`, TUTOR Wiki 연동, Eduholic homepage `2학기 영어 내신 Build It`
>
> **최우선 규칙: REFERENCE IS THE SPEC.**
> 화면 개선 지시는 추상적인 분위기 설명이 아니라 **실제 Shopify 페이지 화면/URL + 구현할 DOM/CSS 골격 + F2R 데이터 치환표**를 제공한다.

## 1. 최종 제품 정의

TUTOR Wiki는 실제 시험 자료를 만드는 **Source of Truth**다. Midterm LIVE는 같은 자료를 다시 작성하는 CMS가 아니라, 시험 기간에 실제 제작 중인 자료와 현장 영상을 보여주는 **seasonal merchandising/storytelling layer**다.

```text
TUTOR Wiki
= 실제 자료 제작/저장
        ↓
LIVE server
= 캠페인 대상 eligible source 판정 + 안전한 preview
        ↓
최소 운영 제어
= 공개/숨김/대표/순서 정도
        ↓
Public Midterm LIVE
= 실제 HTML visual + 실제 현장 영상
```

학부모가 문서를 읽거나 다운로드하는 자료실이 아니다. 대상은 압구정권 중학생 학부모이며, 첫 인상에서 실제 시험 준비가 진행되고 있다는 것을 시각적으로 확인하게 한다.

## 2. 폐기할 #220 초기 CMS 구조

현재 Phase 1 관리자에는 다음 구조가 남아 있다.

- 캠페인
- 학교 컬렉션
- 자료 Showcase
- 업데이트 로그
- Making-of
- Next Exam
- SCHOOL_PRINT / ANALYSIS / WORKSHEET / VOCABULARY / GRAMMAR / VARIANT / FINAL_REVIEW / OTHER taxonomy
- 학교별 설명, 시험 특징, 준비상태 등 중복 작성 필드

이 구조를 최종 운영 구조로 사용하지 않는다. 특히 TUTOR Wiki에서 이미 만든 자료를 `자료 Showcase`에 다시 등록하지 않는다.

관리자가 자료 제목/분류/설명/업데이트를 재작성하는 workflow를 제거하는 방향으로 전환한다.

## 3. Production에서 확인된 현재 blocker

PR #228의 source discovery는 현재 다음과 같다.

```text
Published Campaign
        ↓
Published MidtermSchoolCollection
        ↓
5-axis exact match
school / grade / year / semester / exam
        ↓
TUTOR Wiki
        ↓
eligible HTML preview
```

2026-09-07 Production 확인 결과:

- published campaign: 존재 (`2026 / 2학기 / 중간고사`, PREPARING)
- `schoolCollections`: `[]`
- `wiki-sources`: `[]`
- 따라서 TUTOR Wiki 조회/exact-match/preview 단계까지 도달하지 않음
- 실제 TUTOR Wiki 화면에는 `신구중 / 3학년 / 2026 / 2학기 / 중간` 학교 프린트 저장 2건이 보임
- 관리자에는 신구중 3학년 Collection이 존재하지만 비공개 상태이며, Collection 자체를 publish하는 명확한 UI가 보이지 않음

이 문제를 `published 체크박스 하나 추가`로 해결하지 않는다. 그것은 폐기하기로 한 중복 CMS 운영을 유지하는 방향이다.

다음 backend 작업의 목적은 **#228의 구형 SchoolCollection 필수 의존을 제거하고, TUTOR Wiki → LIVE candidate 구조로 전환하는 것**이다.

단, SchoolCollection을 제거한다는 이유로 TUTOR Wiki 전체를 public fallback으로 노출해서는 안 된다. 캠페인에 포함할 자료를 서버에서 안전하게 판정할 exposure rule을 조사/정의해야 한다.

또한 TUTOR Wiki의 실제 시험 표기 `중간`과 Midterm Campaign의 `중간고사`가 exact-match에서 어떤 영향을 주는지 실제 API 값으로 확인한다. 추측 normalization/fallback을 임의로 추가하지 않는다.

## 4. HTML 자체가 product visual

공개 LIVE에서 HTML 자료를 문서 viewer로 보여주지 않는다.

금지:

- 파일명
- `[HTML]`, `[보기]`
- 다운로드 버튼
- 원본 URL
- storageKey
- 문서 목록 UI
- viewer toolbar
- scrollbar
- iframe 내부 클릭
- A4 전체를 작게 축소한 화면

실제 HTML 자체를 상품 이미지처럼 사용한다.

```html
<section class="midterm-feature">
  <div class="midterm-feature__media">
    <iframe
      class="midterm-feature__document"
      sandbox=""
      tabindex="-1"
      aria-hidden="true">
    </iframe>
  </div>

  <div class="midterm-feature__meta">
    <!-- API가 실제 제공하는 최소 context만 -->
  </div>
</section>
```

기본 visual behavior:

```css
.midterm-feature__media {
  position: relative;
  overflow: hidden;
}

.midterm-feature__document {
  border: 0;
  pointer-events: none;
}
```

실제 HTML을 확대/crop하여 문제 번호, 영어 문장, 밑줄, 표, 분석 표시가 실제 자료임을 알아볼 정도의 크기로 보여준다. #229의 1회성 약 8초 Y 이동을 기반으로 실제 Production 자료를 보며 transform만 조정한다. 무한 반복 animation을 만들지 않는다. `prefers-reduced-motion`에서는 static crop이다.

## 5. Public LIVE에서 학교의 역할

Midterm LIVE는 학교 검색/학교 자료실/학교 portal이 아니다.

공개 UI에서 사용하지 않을 것:

- `우리 학교`
- 학교별 준비 현황
- school navigator
- school card
- 학교별 자료실
- sticky school CTA

학교/학년은 실제 자료에 붙는 작은 context metadata다.

예:

```text
신구중 3학년 · 9.7
```

API에 없는 학교/날짜/상태는 만들지 않는다.

## 6. 최소 관리자 구조

목표 관리자 UX:

```text
2026 2학기 중간고사 LIVE

Wiki에서 감지된 자료
────────────────────
[실제 HTML visual]
신구중 3학년 · 실제 날짜
공개 ●   대표 ☆

[실제 HTML visual]
...
공개 ●

현장 영상
────────────────────
[영상 추가]
```

운영자는 자료를 새로 authoring하지 않는다. Wiki 자료는 자동 소싱하고, 필요하다면 공개/숨김/대표/순서 같은 최소 merchandising control만 제공한다.

새 taxonomy, 중복 metadata, 중복 자료 authoring을 추가하지 않는다.

## 7. 영상

자료와 영상의 입력 경로는 다르다.

- 자료: TUTOR Wiki에서 자동 소싱
- 영상: 관리자가 직접 업로드

기존 AcademyVideo / Midterm Making-of 자산을 최대한 재사용한다. 새 방송/스트리밍 시스템을 만들지 않는다.

공개 영상은 5~12초 정도의 실제 수업/학생 문제 풀이/원장 자료 검토/프린트 검토 장면을 ambient visual로 사용한다.

```html
<video muted playsinline loop></video>
```

공개 화면에는 playlist, lecture UI, progress/chat/broadcast UI를 만들지 않는다.

`LIVE`는 livestream 의미가 아니다. 실제 자료가 생성·수정되고 실제 현장 영상이 추가되면서 **현재 시험 준비가 진행되고 있음**을 보여주는 의미다. 가짜 `2시간 전`, 가짜 진행률, 가짜 활동 로그를 만들지 않는다.

## 8. Public LIVE 콘텐츠 흐름

목표 narrative:

```text
압구정에듀홀릭
2026 2학기 중간고사

[최신 실제 HTML 자료 — 크게]

[최근 실제 자료 visual들]

[같은 자료 detail crop]

[실제 수업/원장 ambient video]

[다음 실제 자료]

[최근 자료 horizontal rail]

[상담 CTA]

footer
```

세로 방향은 story/progression, 가로 rail은 browse/collection에만 사용한다. 모바일에서 다음 item이 10~20% 정도 peek하는 horizontal rail은 허용한다. 전체 narrative를 horizontal carousel로 만들지 않는다.

## 9. Shopify Reference 규칙 — 가장 중요

### REFERENCE IS THE SPEC

Claude/Work에게 다음과 같은 추상 지시를 하지 않는다.

- `Shopify스럽게`
- `고급스럽게`
- `상품 페이지처럼`
- `동적인 느낌`
- `감성적으로`

대신 **실제 Shopify 페이지 모습과 구현 코드를 함께 제공한다.**

모든 visual 작업 지시는 아래 순서를 따른다.

```text
1. REFERENCE
   실제 Shopify page URL + 실제 화면/capture

2. IMMUTABLE ELEMENTS
   reference에서 유지할 구조를 정확히 지정
   - first viewport 구성
   - media/text 관계
   - aspect ratio
   - spacing
   - crop
   - rail/peek
   - desktop asymmetry

3. ALLOWED CHANGES
   F2R 콘텐츠로 치환되는 부분만 명시

4. TARGET DOM/CSS
   구현해야 할 실제 skeleton/code 제공

5. DATA SUBSTITUTION
   Shopify product media → TUTOR Wiki HTML preview
   product metadata → 실제 school/grade/date
   lifestyle video → 실제 academy video

6. FORBIDDEN
   reference에 없는 section/button/card/taxonomy/API/user flow를 임의 추가 금지
```

새 UI가 필요해 보이면 임의 구현하지 말고 중단/보고한다.

### Reference 역할 고정

#### Shopify Atelier — 60%

전체 skeleton, first viewport, large media, airy editorial spacing의 기준.

Official reference:
`https://themes.shopify.com/themes/atelier/presets/atelier`

Reference image:
`https://cdn.shopify.com/theme-store/j22cd1ii74xwbre5nxm51jjn55v1.jpg`

치환:

```text
Atelier large product/media
→ 최신 eligible TUTOR Wiki HTML visual

Atelier product metadata
→ 실제 school / grade / date의 최소 context
```

#### Shopify Motion — 30%

최근 자료가 이어지는 collection/collage와 ambient video/motion의 기준.

Official reference:
`https://themes.shopify.com/themes/motion/presets/motion`

Reference images:
`https://cdn.shopify.com/theme-store/ioupdmfgqzxthwp6gvrffzyhth9f.jpg`
`https://cdn.shopify.com/theme-store/9urggzqx5vx9c1l6hodzzgnbx8s2.jpg`

치환:

```text
Motion evolving collection
→ 최신 실제 Wiki material 3~5개

Motion lifestyle video
→ 실제 수업/원장 ambient video
```

#### Shopify Heritage — 10%

하나의 자료를 여러 확대 crop/detail로 보여주는 방식의 기준.

Official reference:
`https://themes.shopify.com/themes/heritage/presets/heritage`

Reference images:
`https://cdn.shopify.com/theme-store/p6hnt0qyeoesq53919qptrhdjoe6.jpg`
`https://cdn.shopify.com/theme-store/695p6ukpppk7w4m1pz3y3ah3wcr9.jpg`

치환:

```text
Heritage product detail crop
→ 동일 Wiki HTML의 서로 다른 clipped detail view
```

세 reference를 개발자가 자유롭게 blend하도록 맡기지 않는다. 각 reference의 역할을 위와 같이 고정한다.

Shopify의 beige/serif/card 모양을 흉내내는 것이 목적이 아니다. **실제 media 배치 문법을 가져오고 내용만 Eduholic 실제 자료로 치환한다.**

## 10. 모바일/데스크톱 기준

모바일 우선: 360~430px, 기준 검증 viewport `390 × 844`.

첫 2~3 scroll 안에 여러 실제 material/person/update signal이 보여야 한다. 자료 visual은 화면의 주인공이며, 문서가 회색 패턴처럼 보일 정도로 작아지면 실패다. text padding은 편안하게 유지하되 media는 필요하면 더 넓게 bleed할 수 있다.

Desktop 1440px에서는 모바일을 가운데 놓고 확대한 800px 문서형 layout을 만들지 않는다. reference에 따라 비대칭 media/text와 넓은 visual을 사용한다.

## 11. Copy 규칙

한국어 우선, 짧은 사실만 사용한다.

허용 예:

- `압구정에듀홀릭`
- `2026 2학기 중간고사`
- `중간고사를 준비하고 있습니다.`
- `신구중 3학년 · 9.7`

금지 표현:

- `A가 아니라 B`
- `단순히`, `단순한`
- `그 이상의`, `넘어`
- `새로운 기준`
- `혁신적인`
- `압도적인`
- `완전히 새로운`
- `본질`
- `진짜`
- `경험하세요`
- `증명합니다`
- `차이를 만듭니다`
- `가능성`
- `패러다임`

주장을 copy로 쓰지 말고 실제 자료/영상/실제 날짜로 보여준다.

## 12. 보안 경계

PR #228에서 만든 보안 경계를 약화시키지 않는다.

유지할 것:

- server-synthesized signed tutor identity
- 서버측 exposure validation
- preview 재검증
- strict CSP
- `script-src 'none'`
- `connect-src 'none'`
- `form-action 'none'`
- `object-src 'none'`
- `sandbox=""`
- `pointer-events:none`
- storageKey 비노출
- original URL 비노출
- public download endpoint 없음
- Python upstream header를 public response에 그대로 전달하지 않음

SchoolCollection dependency를 제거하더라도 위 보안 목적은 유지해야 한다.

## 13. Eduholic homepage도 같은 visual system으로 변경

LIVE 완성 후 별도 PR에서 현재 Eduholic homepage의 `2026 FALL / 2학기 영어 내신 Build It` 영역을 같은 source/visual language로 변경한다.

현재 학교별 Digital Twin/count/exam-list card 중심 UI를 최종 형태로 유지하지 않는다.

목표:

```text
Eduholic Home

2학기 영어 내신 Build It

[실제 Wiki HTML crop]
신구중 3학년
중간고사 준비 중

[다음 실제 material visual →]

2026 중간고사 LIVE →
```

Homepage와 LIVE가 별도로 자료를 관리하지 않는다. 같은 실제 source를 사용하며, Homepage에서 본 visual이 LIVE로 자연스럽게 이어지게 한다.

## 14. 작업 순서

1. #220 구형 CMS 구조와 #228 SchoolCollection dependency를 조사한다.
2. 안전한 TUTOR Wiki → LIVE candidate exposure rule을 확정한다.
3. 구형 SchoolCollection 필수 dependency를 제거한다.
4. Production에서 실제 Wiki HTML 1건 이상이 `wiki-sources`와 preview로 연결되는지 확인한다.
5. 실제 자료가 뜬 뒤에만 visual 작업을 시작한다.
6. **실제 Shopify reference 화면 + target DOM/CSS code**를 제공하여 LIVE를 section 하나씩 구현한다.
7. LIVE 완성 후 별도 PR로 Eduholic homepage Build It 영역을 같은 source/visual system으로 교체한다.

## 15. 구현 금지 원칙

다음은 요구사항에 없는 한 임의 추가하지 않는다.

- 새 school navigator
- school collection public UI
- material authoring UI
- 새 taxonomy
- dashboard
- fake progress
- fake update timestamp
- process card
- `HOW WE PREPARE`, `SOURCE`, `ANALYSIS` 같은 decorative English 단계
- sticky school CTA
- public download/original document link
- 새 DB field/API/user flow

**Do not add UI, buttons, taxonomy, DB fields, APIs, user flows, or sections not present in the supplied reference/spec. If something new seems required, stop and report before implementing.**

## 16. 현재 완료된 기반

- PR #227: public school navigator/sticky CTA 제거
- PR #228: TUTOR Wiki HTML → LIVE read-only safe preview connection
- PR #229: public LIVE first Wiki HTML iframe visual 기반

현재 다음 핵심 과제는 visual decoration이 아니라 **#228의 구형 published SchoolCollection dependency를 최종 구조에 맞게 제거하는 것**이다. 실제 source 연결이 확인된 후 Shopify reference 기반 visual 구현을 진행한다.
