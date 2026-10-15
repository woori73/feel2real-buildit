# Academy 공개페이지 — AI 생성/채움 로드맵

> 공개 학원페이지(`/academies/{slug}`, `academy_public.js` CSR)를 AI로 채우거나 생성하는 두 단계 로드맵. 현재는 고정 렌더러 + 타입드 데이터 구조(page_settings). [academy-publish-design.md](academy-publish-design.md) 연장선.

## 핵심 개념: 섹션 `id` = 콘텐츠 슬롯 계약
공개페이지의 현재 안정적 semantic id는 `sec-hero`, `sec-intro`, `sec-principal`, `sec-lessons`, `sec-social`, footer다. `sec-lessons`는 등록된 수업상품/강의 목록 슬롯이다. 커리큘럼은 추후 curriculum 테이블과 academy/lesson 연결 모델 확정 전까지 future 슬롯(`sec-curriculum`)으로 둔다. 학습 효과/디지털 트윈/마켓/결제 등은 아직 클릭·토글·AI 채움 대상이 아닌 future slot 후보로만 둔다. 이 id 집합을 **"데이터 슬롯 계약"**으로 고정하면, AI가 무엇을 생성하든 실데이터/편집이 이 슬롯에 매핑된다. 두 모델 모두 이 계약을 공유한다.

## 모델 A — 고정 렌더러 + AI가 타입드 콘텐츠 채움 (우선 착수)
- 앱(`academy_public.js`)이 마크업/CSS/id를 **독점 소유**. AI는 **HTML 안 만들고 구조화된 콘텐츠(섹션 id별 타입드 필드)만 생성**.
- AI 출력 = 콘텐츠 스키마 JSON → 기존 create/update 경로로 저장(`page_settings` + 엔티티 필드 + 향후 섹션별 타입드 모델).
- 장점: 안전(raw HTML 없음, allowlist 유지), 전역 디자인 수정 1곳, 일관성. 디자인은 템플릿 공유.
- 제약: AI가 채우려면 **그 섹션에 타입드 콘텐츠 모델이 필요**. 현재 준비중 기능(학습 효과/디지털 트윈/마켓/결제)은 타입드 모델 없음 → AI-fillable 범위 = 현재 슬롯의 타입드 필드 있는 곳까지. 섹션별 콘텐츠 스키마를 점진 정의.
- `page_settings`(Phase A)가 이 패턴의 첫 조각.
- **관련 이슈**: AI 내부 데이터 자동 채움.

## 모델 B — AI가 전체 홈페이지 생성 (Lovable식, 고도화·보류)
- AI가 CSS 종속 없이 **완전 커스텀 HTML/CSS를 생성 → DB 저장 → 매번 렌더**. 학원마다 완전 다른 디자인.
- 난제 3:
  1. **보안**: 생성 HTML/CSS(/JS) 저장·서빙 = XSS/인젝션. 해결 = sanitize(위험 태그/이벤트핸들러 제거, allowlist-HTML) **또는** iframe sandbox 격리(데이터는 postMessage). → 현재 "raw HTML 저장 금지" 정책의 피벗.
  2. **id = 데이터 주입 앵커**: AI가 디자인 자유롭게 하되 약속된 id(모델 A와 동일 slot 계약)를 반드시 포함 → 앱이 실시간 데이터(수업/블로그/오너툴바/로고) hydrate. 커스텀 디자인 + 고정 주입점.
  3. **저장**: `page_html`/`page_css`(또는 구조화 layout document) LONGTEXT 컬럼/테이블 + 버전관리.
- 트레이드오프: 디자인 자유↑ vs 보안 부담·전역 디자인 수정 불가(학원별 동결).
- **A→B 진화**: 모델 A의 id 체계를 그대로 B의 hydration 앵커로 재사용. 지금 하는 A 작업이 B의 토대.
- **착수 전 deep-interview 필수**(보안 모델·저장 구조·hydration 프로토콜 확정).
- **관련 이슈**: AI 홈페이지 통째 생성(고도화).

## 결정 요약 (2026-07-10)
- 지금은 **모델 A(내부 데이터 AI 채움)부터** 진행. 모델 B는 무거워서 고도화로 보류(문서+이슈만).
