# BuildIt 디자인 자산(buildit2) 연동 설계 — 조사·설계 단계 (구현 없음)

> 상태: 설계 확정(§7 결정 4건 모두 A안 승인, 2026-10-12). §8에 확정 사항·P0 구현 범위를 정리했다. **구현은 별도 승인 후.** 원본 조사 본문(§1~§6)은 그대로 보존한다.

기준: `woori73/buildit2` @ `42221fd1f9c4` (자산 10종: 템플릿 3·컴포넌트 4·애니메이션 3), BuildIt main @ 844aa257 + PR #623.

## 1. 조사 결과 — 사실

### 1.1 buildit2 자산
| 항목 | 확인한 사실 |
|---|---|
| 구조 | README는 `templates/ components/ animations/`라고 쓰지만 **실제 경로는 `assets/<id>/`** (README↔실제 불일치). 자산당 `demo.html / style.css / (script.js) / README.md`, 미리보기 PNG 4종(m360/m390/m430/pc) |
| `assets.json` | `id, type, category, tags, license, license_note, source_url, version, compatibility{buildit_renderer, korean_font_needed, requires}, buildit_apply, mobile_verified, files, preview`. `natural_language_examples` 5개. **슬롯(바인딩 위치)·삽입 대상 섹션·충돌 위험·의존 버전 정보가 구조화돼 있지 않다** (`buildit_apply`는 자유 문장) |
| 검증 범위 | `verified:true`는 "독립 데모가 Playwright에서 JS 오류 없이 렌더"만 뜻함. **BuildIt 렌더러 적용·실데이터 연동·성능·접근성은 미검증**(자체 `verification_scope`에 명시). `compatibility.buildit_renderer:"high"`는 근거 없는 주장 → 우리가 재검증해야 함 |
| 라이선스 | `license:"MIT"`인데 `license_note`는 "오리지널 제작 (원본 저장소 소실… 패턴 참고)". 즉 **코드는 새로 쓴 자체 코드이고 MIT 표기는 영감 출처 표기**. 출처 라이선스 신뢰보다 "자체 작성물 + 영감 출처"로 정리하고 저작권자(우리) 라이선스를 명시해야 함. GSAP는 "Standard(상업 무료)" 표기 — 약관 재확인 필요 |
| 하드코딩 데이터 | 데모에 가짜 학원 정보가 박혀 있음: "한빛학원", 강사 김서연·박지훈 등 **가상 경력·"서울대 12명 합격"·수능 만점자 40명**, 주소 `서울특별시 강남구 테헤란로 123`, 전화 `02-1234-5678`, 후기. **회원 미제공 정보 임의 생성 금지 원칙상 그대로 게시되면 안 되는 내용** → 반드시 슬롯화 |
| CSS | 접두사 `bi-`(자산 내부는 일관). 그러나 템플릿 3종은 **전역 리셋**(`*{margin:0}`, `html`, `body`) 포함, 컴포넌트는 섹션 단위 클래스. `:root{--bi-*}` 변수 사용(템플릿) |
| JS | `script.js` 3종 템플릿 + GSAP 2종. 속성 훅 `data-bi-reveal`, `data-bi-count`(+`-suffix/-format/-decimals`). `prefers-reduced-motion` 대응·GSAP 미로드 시 최종값 표시(폴백 양호). 위험 API(`innerHTML/eval/fetch`) 사용 없음 — 확인함 |
| 외부 의존 | GSAP **3.13.0** (`cdn.jsdelivr.net`), Noto Sans KR(Google Fonts). 폼 있는 템플릿(전화 입력)은 **전송 대상 없는 데모 폼** |

### 1.2 BuildIt 쪽
- 공개 홈페이지 = `public/academy_public.html` + `academy_public.js` + **`academy_sections.js`(sec-* 마크업 정본, JS 템플릿 문자열로 `AcademyResponse`에서 생성)**. `sec-hero, sec-intro, sec-philosophy, sec-principal, sec-lessons, sec-videos, sec-social, sec-featured(opt-in), sec-digitaltwin(opt-in), sec-minilectures(opt-in), sec-cta(opt-in)` 등. 표시 여부는 `sectionVisibility`, 순서 `sectionOrder`, 스타일은 `builderDraft`의 `designPreset/heroLayout/cardStyle/color*` → `academy_brand.js`가 `data-design-preset` + `--brand-*` 변수로 적용.
- **이미 GSAP 3.12.5(cdnjs)를 로드**(sec-digitaltwin 전용). 자산은 3.13.0(jsdelivr) → 한 페이지에 **두 버전/두 CDN** 충돌 위험.
- 실제 회원 데이터 모델: `name, intro, subjects, targetGroups, regions, lessons(수업), businessInfo{phone,address,email,대표자…}, builderDraft(소개 문구·월 수강료 monthlyTuition·CTA 문구·원장 소개 등 문자열 key/value)`. **구조화된 "강사진" 테이블은 없다**(원장 소개 draft 키만). 합격 실적/후기 모델도 없다.
- 시각 편집기(`homepage.renderMode=custom`): Jsoup 정제 + Shadow DOM(`visual_editor_render.js`)로 사용자 HTML/CSS 렌더 — **자산 HTML/CSS를 격리해 넣는 기존 인프라**가 이미 있다. 스크립트는 허용하지 않는다(정제기가 script/on* 제거).
- 작업본·게시 구조: 회원 `academy_working_copy`, 관리자 `academy_admin_working_copy`(별도 테이블), 게시는 학원 행 잠금 + 버전 보관 + base 지문 충돌 검출. 공개 CSP 헤더는 홈페이지에 없음.

## 2. 호환성 분석 (3번 요구 1·2)

### 2.1 "sec-* 렌더러와 직접 호환" 주장 → 그대로는 아님
- BuildIt 섹션은 데이터→마크업 생성기. 자산은 **완성된 정적 마크업 + 가짜 데이터**. 둘은 층이 다르다: 자산은 `sec-*`를 **대체**하거나 **스타일만 입힐** 수 있을 뿐 "삽입"이 곧바로 되지 않는다.
- 템플릿 3종은 페이지 전체(전역 리셋·body 스타일)라 현 페이지에 직접 넣으면 기존 `#publicView` CSS를 깨뜨린다(이미 시각 편집기에서 같은 문제로 Shadow DOM을 도입한 전례).

### 2.2 프리셋 10종 vs 자산 10종 — 관계: 직교(보완)
| 축 | 기존 프리셋 10종 | 신규 자산 10종 |
|---|---|---|
| 정체 | **테마**: 색 10토큰·타이포·hero 레이아웃·카드 스타일·기본 섹션 on/off (데이터 구동) | **구현체**: 완성 마크업/CSS/JS 조각 (템플릿 3·컴포넌트 4·모션 3) |
| 겹침 | 템플릿 `tmpl-minimal-landing` ≈ 프리셋 MINIMAL, `tmpl-open-school/demo-school` ≈ 종합/학교형(프리셋 STUDY·EXAM 일부)과 **분위기 중복**. 나머지(컴포넌트·모션)는 프리셋에 없는 축 |
| 조합 | 프리셋(색·분위기) × 컴포넌트(섹션 변형) × 모션(속성) 로 **독립 조합 가능**. 템플릿은 "페이지 골격" 한 개만 선택 |

→ 권장 모델: **Design Recipe = { preset(테마) + 섹션별 variant(컴포넌트) + motion(0~2) }**. 템플릿 3종은 "미리 짜 둔 recipe"로 재해석(별도 렌더 경로를 만들지 않음).

## 3. 아키텍처 (원칙: 새 렌더러를 만들지 말고 기존 sec-* + 프리셋 + 작업본 재사용)

### 3.1 자산 = 데이터 구동 "섹션 변형(variant)"으로 이식
1. 각 컴포넌트를 **슬롯이 있는 템플릿**으로 변환(자산 변환 단계, 사람이 1회): 가짜 값 → `{{academy.name}}`, `{{contact.phone}}`, `{{#teachers}}…` 등 슬롯, 스타일은 `.bi-<asset>` 스코프 + `--brand-*` 변수 사용으로 프리셋 색을 따르게 함(자산의 하드코딩 색 제거).
2. `academy_sections.js`의 해당 섹션 렌더러가 `builderDraft.sectionVariant.<sec>=<assetId>`를 읽어 **variant 마크업으로 분기**(없으면 기존 마크업 → 기존 사이트 불변). 공개 렌더·미리보기·작업본 미리보기가 같은 함수를 쓰므로 편집=미리보기=공개 일치(기존 원칙) 유지.
3. 모션은 `builderDraft.motion=<assetId>` + `data-bi-*` 속성만 부여. 기존 `academy_reveal.js`(IntersectionObserver)와 역할이 겹치므로 **CSS/IO 기반 자산 우선, GSAP 자산은 보류/옵트인**(§5).

대안(비채택): 자산 HTML을 시각 편집기 `custom` 모드로 통째 주입 — 격리는 쉬우나 ① 데이터 바인딩(회원 데이터 변경 시 반영) 불가, ② script 금지로 모션 불가, ③ 편집기 전용 화면 종속. **정적 템플릿 1회 생성용(§3.5 Phase 4 옵션)** 으로만 활용.

### 3.2 안전한 삽입·조합 규칙
- 허용 삽입 지점은 **레지스트리에 선언된 섹션 슬롯**만(임의 DOM 위치 금지). 자산 메타에 `targets:["sec-hero"]` 같은 필수 필드.
- 렌더 결과는 서버/클라이언트 **정제 통과**: 슬롯 값은 전부 텍스트 이스케이프(현 `escapeHtml` 경로), URL은 `https`만, 자산 템플릿 자체는 레지스트리에 사전 검수·버전 고정된 것만(사용자·AI가 HTML을 쓰지 못함 → XSS 면 최소).
- CSS: `.bi-<asset>` 접두 + 전역 선택자(`*`,`html`,`body`) 제거 후 등록(자동 린트로 거부). 충돌 방지 위해 variant 루트에 `isolation` 컨테이너, 필요 시 Shadow DOM 대신 접두+린트로 시작(편집기와 달리 기존 페이지 CSS와 함께 쓰므로 Shadow는 부분 적용이 어려움).
- JS: **자산 JS는 BuildIt 정적 번들로 이식**(CDN 직접 로드 금지), 속성 훅만 사용, 중복 초기화 방지 플래그.

### 3.3 실데이터 바인딩 (원칙: 미제공 정보 생성 금지)
슬롯 계약을 자산별로 선언:
```
slots: { academyName: {src:"academy.name", required:true},
         phone: {src:"businessInfo.phone", required:false, onMissing:"hide"},
         teachers: {src:"builderDraft.teachers[]", required:false, onMissing:"hide-section"},
         monthlyTuition: {src:"builderDraft.monthlyTuition", onMissing:"hide"} }
```
- 값이 없으면 **숨김(hide) 또는 섹션 제외**, 가짜 대체 문구 금지. AI에게는 "비어 있는 슬롯 목록"만 알려 **질문**으로 되묻게 한다(현 2-A '필요한 정보만 대화로 확인'과 동일).
- 현재 데이터가 없는 항목(강사진 구조화, 합격 실적, 후기)은 **데이터 모델 선행 필요**: 우선 `builderDraft` JSON 키(예: `teachersJson`)로 시작(스키마 변경 없음, 회원이 입력·승인한 값만), 정식 테이블은 후속.
- 실적 숫자 등 검증 불가 수치는 `data-bi-count`에 **회원이 직접 입력한 값만** 연결.

### 3.4 자연어 → 자산 선택 (AI 선택 구조)
1. **메타 확장**(자산 변환 시): `assets.json` → 운영 레지스트리 스키마(§4)에 `intents[]`(예: "강사진 카드", "합격자 수 카운트"), `audience`, `tone[]`, `targets[]`, `slots`, `conflicts[]`를 추가.
2. **2단계 검색**: ① 규칙·키워드 필터(type/targets/tone; 현 `AcademyAiCreateService` 스타일 키워드 매핑 재사용) → 후보 ≤ 8, ② LLM이 후보 메타(JSON)만 보고 recipe를 **구조화 출력(JSON schema)** 으로 선택. LLM은 자산 코드를 보지 않고 `assetId`만 고른다(코드·HTML 생성 금지).
3. 100종 이상: 키워드 필터로 후보 축소 + 임베딩(기존 NewStart `knowledge_embedding` 인프라 재사용 검토) 후 LLM 재순위. 프롬프트에는 메타만 싣고 Prompt Caching(카탈로그 앞부분 캐시)으로 비용 관리.
4. 서버 검증: 선택된 `assetId`가 레지스트리·`status=ACTIVE`·`targets` 규칙에 맞는지, 필수 슬롯이 채워지는지 확인 → 아니면 되묻기.

### 3.5 작업본 적용 → 승인 → 게시 (기존 구조 그대로)
- 선택 결과를 `recipe`(builderDraft 키들)로 **회원 작업본(또는 관리자 작업본)에만 저장** — 공개는 불변. 미리보기는 기존 `/preview`. 사용자가 '게시하기'를 눌러야 반영(2-A/B 흐름, 최초/수정 게시 구분 유지). 관리자는 관리자 작업본 + 게시 전 대상 확인(PR-C).
- 모든 recipe 변경은 키 단위(`designPreset`, `sectionVariant.*`, `motion`)라 **#623의 필드 단위 병합·충돌 보호·감사·버전 복원이 그대로 적용**된다(추가 개발 최소).
- 되돌리기(2-C 한 단계 undo)와 자연스럽게 결합: recipe 변경 = builderDraft 부분 패치.

## 4. DB/API 변경안 (제안 — 구현 안 함)

**원칙**: 자산 코드는 **저장소(Git)가 원본, 서버 번들(정적 리소스)로 배포**. DB에는 **메타·상태·버전 포인터**만 둔다(운영 중 임의 코드 주입 경로 차단 + 장애 시 정적 번들로 폴백).

```sql
-- V10xx (신규 테이블만, 기존 데이터 불변)
CREATE TABLE design_asset (
  asset_id VARCHAR(64) PK,            -- 'comp-team-tailblocks'
  type ENUM('TEMPLATE','COMPONENT','ANIMATION'),
  title, description, status ENUM('DRAFT','ACTIVE','DEPRECATED'),
  version VARCHAR(20),                -- 번들 버전(변경 시 새 행 아님: asset_version 참조)
  meta_json JSON,                     -- tags/intents/tone/targets/slots/conflicts/requires
  license, license_note, source_url,
  content_hash CHAR(64),              -- 번들 파일 해시(무결성)
  created_at, updated_at);
CREATE TABLE design_asset_version (asset_id, version, content_hash, bundle_path, released_at, PRIMARY KEY(asset_id,version));
```
- 동기화: `buildit2`(Git) → CI가 `catalog/` 검증·린트(전역 선택자·하드코딩 전화/주소·`script` 금지·라이선스 필드 필수) → BuildIt 리소스(`/static/design-assets/<id>/<ver>/`)로 **번들링+해시** → 관리자 전용 동기화 작업이 `design_asset` upsert(수동 승인 후 `status=ACTIVE`). 실시간 외부 fetch는 하지 않는다(장애·변조 면역).
- 폴백: ① 자산이 DEPRECATED/없음 → 해당 섹션은 **기본 sec-* 마크업**으로 렌더(사이트 안 깨짐), ② GSAP 로드 실패 → 최종값 즉시 표시(자산이 이미 지원), ③ DB 메타 장애 시 번들 내 `assets.json` 스냅샷으로 검색.
- recipe 저장은 신규 컬럼 없이 `builderDraft` 허용 키 추가(`sectionVariant.<sec>`, `motion`, `designAssetVersions`)만 — 키 allowlist(AcademyService)에 등록.
- API(안): `GET /api/builder/design-assets?type=&target=&q=`(메타 검색, 로그인 TUTOR/ADMIN), `POST /api/tutor/academy/assistant/turn`(기존)에 recipe 선택 단계 추가, 관리자 동기화는 `/api/admin/design-assets/sync`(ADMIN, 감사 기록). 자산 번들 파일은 정적 `GET /design-assets/**`(공개, 불변 해시 URL, 캐시).

## 5. 위험·충돌 검토 (요구 6)

| 영역 | 위험 | 대응 |
|---|---|---|
| CSS 충돌 | 템플릿의 `*`/`html`/`body` 리셋이 `#publicView` 전체·`academy_public.css`를 파괴. `#publicView :is(h1…p)` 고특이도 규칙과 컴포넌트 규칙이 충돌(편집기에서 이미 겪음) | 등록 린트로 전역 선택자 금지, `.bi-*` 접두 강제, 특이도 테스트(작업본 미리보기 시각 회귀). 템플릿은 전체 페이지가 아닌 **recipe로 분해** |
| GSAP | 페이지에 이미 3.12.5(cdnjs); 자산은 3.13.0(jsdelivr) — 이중 로드·플러그인 등록 충돌, 서드파티 CDN 의존(장애·프라이버시·CSP) | **한 버전으로 통일**(기존 3.12.5로 자산 재검증 또는 전체 업그레이드 별도 판단), 가능하면 자체 호스팅. 1차 범위는 **CSS/IO 기반 자산(slide-in-bottom)만**, GSAP 자산은 2차 |
| 보안 | AI가 HTML/JS를 생성·주입하면 XSS. 슬롯 값(회원 입력)이 마크업에 들어감 | 자산은 사전 검수된 정적 번들만, 슬롯은 이스케이프·URL 스킴 allowlist, AI는 `assetId`·슬롯 선택만(코드 생성 금지), 서버 allowlist 검증. CSP 헤더 도입은 별도 과제(지금 공개 페이지는 CSP 없음) |
| 모바일 | 자산은 360/390/430 검증 완료지만 BuildIt 페이지 맥락(고정 헤더·scene flow·`academy_scene.js`)에서는 미검증 | 적용 PR마다 360/390/430 + 실데이터 Playwright 스냅샷 |
| 데이터 | 가짜 정보 게시 | 슬롯 계약+hide 기본, 데모 문구 자산 본문 반입 금지(린트: 전화·주소·실적 패턴 검출) |
| 라이선스 | MIT 표기와 license_note 불일치, GSAP 약관 | 자산별 `copyright`/`origin` 정리·법무 확인 후 `ACTIVE` |
| 성능 | 자산 CSS 누적(템플릿 최대 13KB CSS + 16KB HTML), 외부 폰트(Noto Sans KR) 이중 로드 | 사용 recipe의 CSS만 로드, 폰트는 기존 로딩 재사용 |
| 운영 | 운영 홈페이지 변경 | recipe는 작업본에만 저장, 게시 전 불변(검증된 기존 구조), 기존 학원은 variant 없음=불변 |

## 6. 단계별 구현 계획 (각 단계 별도 PR·승인)

| 단계 | 내용 | 검증/산출 |
|---|---|---|
| **P0 정비**(buildit2 쪽) | README/경로 정합, 라이선스·저작 표기 정리, 데모 가짜 데이터 → 슬롯 표기, `assets.json` 스키마 확장(`targets/slots/intents/conflicts`) + 린트 스크립트 | 린트 통과 CI |
| **P1 변형 1종 PoC** | `comp-team-tailblocks`(강사진) 1개를 `sec-principal`/신규 강사 섹션 variant로 이식. 데이터가 있을 때만 렌더, 없으면 숨김. 키 allowlist 추가. 작업본 미리보기·공개 일치 | 단위+MySQL 통합+Playwright 360/390/430 |
| **P2 레지스트리** | `design_asset`/`_version` 테이블, 번들 배포·동기화 CLI(관리자), 검색 API | 마이그레이션 테스트, 폴백 테스트 |
| **P3 AI 선택** | 규칙필터 + LLM 구조화 선택, 슬롯 부족 시 되묻기, 작업본 적용(2-A 턴 확장). 2-C 되돌리기와 결합 | 선택 정확도 케이스셋, 회귀 |
| **P4 확장** | hero/후기/네비·푸터 variant, 모션(CSS 우선→GSAP 통일 후), 템플릿 3종을 recipe로 제공, 100종 대비 임베딩 검색, (선택) 시각 편집기 import | 시각 회귀·성능 예산 |

## 7. 결정이 필요했던 사항 (→ §8에서 확정)
1. 강사진·후기·실적 **데이터 모델** 도입 시점(draft JSON으로 먼저 시작해도 되는지).
2. GSAP **3.12.5 vs 3.13.0** 통일 방향과 자체 호스팅 여부.
3. 자산 저작권/라이선스 표기 정책(자체 작성물로 정리) 확정.
4. 1차 범위를 CSS/IO 자산 + 컴포넌트 1종 PoC로 한정할지.

## 8. 확정 사항 (사용자 승인: 4건 모두 A안)

| # | 결정 | 확정 내용 |
|---|---|---|
| 1 | 강사진·후기·실적 데이터 | **`builderDraft` JSON 키**로 시작(스키마·DB 변경 없음). 회원(또는 회원 대신 관리자)이 입력·승인한 값만 사용. 정식 테이블은 쓰임새 확인 후 후속 |
| 2 | GSAP | **1차 구현 제외.** 기존 CSS/IntersectionObserver(`academy_reveal.js`) 활용. GSAP 통일은 후속(자체 호스팅 포함) |
| 3 | 저작권·라이선스 | 자체 작성 코드와 외부 출처·라이선스를 **구분 표기**. `ACTIVE` 전환 전 정리(P0 포함), GSAP 약관은 도입 시점에 확인 |
| 4 | 1차 범위 | **P0 정비 + 강사진 컴포넌트 1종 PoC**(`comp-team-tailblocks` 기반) |

**이번 단계에서 하지 않는 것**: 운영 DB 변경, 디자인 자산 레지스트리(`design_asset*` 테이블), AI 자동 검색·선택, 2-C AI 수정 턴, GSAP 자산.

## 9. P0 구현 범위 (구현 전 정리 — 별도 승인 후 착수)

### 9.1 원칙
- 기존 `sec-*` 렌더러(`academy_sections.js`)를 재사용한다. **신규 렌더러 개발 금지** — 강사진은 기존과 같은 방식의 섹션 렌더 함수 1개(`renderTeachers`)를 추가하는 것으로 한정한다.
- 실제 회원이 입력한 강사 정보만 표시한다. **데이터가 없으면 섹션을 렌더하지 않는다(숨김).** 기존 `sec-principal`처럼 "○○○ 원장" 플레이스홀더를 채우지 않는다. 가짜 강사·경력·사진·수치 생성 금지(AI 포함).
- 공개 홈페이지는 게시 전까지 불변. 입력·미리보기는 작업본(회원 `academy_working_copy` / 관리자 `academy_admin_working_copy`)만 사용하고, 권한·데이터 분리는 #622/#623 구조를 그대로 유지한다.
- 기존 학원은 변하지 않는다(키가 없으면 `renderTeachers`는 빈 문자열).

### 9.2 데이터 계약 (`builderDraft` 키)
| 키 | 내용 | 비고 |
|---|---|---|
| `teachersJson` | JSON 배열 문자열 `[{"n":"이름","r":"역할/과목","c":["경력1","경력2"],"p":"사진 URL(선택)"}]` | `LONG` 키 목록 등록, 최대 길이 상향 검토(현재 long 상한 2,000자 → 강사 6명 × 약 300자 기준 약 2,000자로 수용 가능한지 테스트 후 결정), 항목 수 ≤ 8 |
| `teachersSectionTitle` | 섹션 제목(선택, 기본 "강사진") | 일반 길이 키 |

- 서버 검증(`AcademyService.validateBuilderDraft` 확장): JSON 파싱, 항목 수·필드 길이 제한, 이름 필수, 경력 항목 수 제한, 사진 URL은 `https` 또는 BuildIt 업로드 경로만 허용(그 외 거부), HTML/스크립트 문자열은 저장하되 **렌더 시 항상 이스케이프**. 비어 있거나 파싱 불가한 값은 "없음"으로 취급(렌더 안 함).
- 렌더 허용 필드는 `n, r, c, p`뿐(나머지 키 무시).

### 9.3 변경 파일(예상)
| 영역 | 파일 | 내용 |
|---|---|---|
| 서버 | `AcademyService.java` | `teachersJson`·`teachersSectionTitle` allowlist 등록(+LONG), `validateBuilderDraft`에 강사 JSON 검증 추가, `DEFAULT_SECTION_ORDER`·`OPT_IN_SECTION_IDS`에 `sec-teachers` 추가 |
| 서버 | `AcademyAdminAuditService` 등 | 변경 없음(키 단위라 기존 감사·병합·충돌 보호가 그대로 적용). `AdminDraftMerger`는 문자열 키로 처리하므로 변경 없음 — 단, `teachersJson` 한 키 안의 강사별 병합은 하지 않음(키 단위 충돌) |
| 프런트 | `academy_sections.js` | `renderTeachers(view, ctx)` 추가, `sec-teachers` opt-in(명시적 true일 때만) + 유효 데이터가 있을 때만 렌더, 모든 문자열 `escapeHtml`, 사진은 URL 검증 후 `<img loading=lazy>`, 없으면 이름 첫 글자 아바타(실제 이름에서 파생, 가짜 사진 아님) |
| 프런트 | `academy_public.js`, `academy_builder.js` | 섹션 ID 목록·표시명(`sec-teachers`: '강사진')에 등록(서버+JS 3곳 중복 정의 → 같은 PR에서 동기화, 기존 드리프트 허용 로직과 일치) |
| CSS | `academy_public.css`(또는 신규 `academy_teachers.css`) | `comp-team-tailblocks/style.css`를 **`bi-teachers` 접두 + `--brand-*` 변수** 기반으로 이식(하드코딩 색 제거 → 기존 프리셋 10종 색을 따름). 전역 선택자 없음. 브레이크포인트: PC, 모바일 360/390/430 |
| 입력 UI | 관리자 관리 화면(`admin_manage.html/js`) | 강사 목록 편집 폼(이름·역할·경력·사진 URL) — 저장은 기존 `PUT /api/admin/academies/{id}/builder`의 `pageSettings.builderDraft.teachersJson`. **회원 측 입력 UI는 P0 범위 밖**(회원은 기존 빌더/차기 대화 화면에서 후속) — 승인 시 범위 조정 가능 |
| 문서·CI | `docs/API.md`, `docs/DOMAIN.md`, `build-check.yml` | 키 계약·섹션 추가 문서화, 신규 테스트 CI 목록 등록 |
| buildit2(별도 저장소) | `README`·`assets.json`·`assets/comp-team-tailblocks/*` | 경로 정합, 가짜 강사 데이터 제거(슬롯 표기), 라이선스/출처 구분 표기, 린트 스크립트 — 별도 승인 후 buildit2에서 작업 |

DB 마이그레이션은 **없다**(`builderDraft`는 기존 JSON 컬럼).

### 9.4 API
신규 엔드포인트 없음. 기존 작업본 API를 그대로 사용한다.
- 회원: `PUT /api/tutor/academy/working-copy` (`pageSettings.builderDraft.teachersJson`), 미리보기 `GET .../preview`
- 관리자: `PUT /api/admin/academies/{id}/builder`, 미리보기 `GET .../builder/preview`
- 게시: 기존 게시 API(전/후 비교·충돌 보호·버전 보관 동일)

### 9.5 테스트 범위 (given/when/then)
- **단위**: 강사 JSON 검증(정상/빈 값/항목 수 초과/필드 길이 초과/잘못된 JSON/잘못된 사진 URL 스킴/HTML 포함 값 → 이스케이프 대상) · `renderTeachers`용 순수 정규화 함수(JS는 Node 단위 또는 Playwright DOM 확인)
- **MySQL 통합**: 작업본에 `teachersJson` 저장 → 공개 API 불변 → 게시 후 공개 반영 · 관리자/회원 작업본 분리 유지 · 같은 키 양쪽 수정 시 충돌 보호(rebase) · TUTOR/STUDENT/비로그인 권한 변화 없음 · 키가 없는 기존 학원 응답 불변
- **브라우저(Chromium)**: PC + 360/390/430에서 (a) 데이터 없음 → 섹션 없음 (b) 강사 1명/4명/8명 렌더 (c) 프리셋 10종 중 대표 3종에서 색 일관 (d) 가로 스크롤·JS 오류 없음 (e) 사진 URL 없음 → 이름 아바타, 가짜 값 없음 (f) 작업본 미리보기만 보이고 공개는 게시 전 불변
- **회귀**: 기존 `sec-principal`·다른 섹션 렌더 변화 없음(스냅샷), 섹션 순서 정규화 테스트에 `sec-teachers` 반영

### 9.6 위험과 완화
| 위험 | 완화 |
|---|---|
| `sec-teachers` 추가가 기존 학원 레이아웃을 바꿈 | opt-in(명시적 true) + 유효 데이터 있을 때만 렌더 → 기존 학원 불변. 순서 정규화가 기존 저장 순서에 섹션 ID를 추가하더라도 렌더 결과는 빈 문자열 |
| 섹션 ID 목록 중복 정의(서버+JS 3곳) 드리프트 | 같은 PR에서 4곳 동기화 + 계약 테스트 |
| `teachersJson` 길이 상한(2,000자) | 상한 상향 또는 항목당 길이 제한으로 수용성 검증 후 결정 |
| 한 키 안 강사 단위 충돌 | P0는 키 단위 충돌 보호로 충분(충돌 시 공개 값 유지 + 목록 표시). 강사 단위 병합은 후속 |
| 외부 사진 URL(추적·혼합 콘텐츠) | `https`·업로드 경로만 허용, 필요 시 후속에 업로드 연동 |
