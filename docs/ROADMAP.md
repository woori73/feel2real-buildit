# Roadmap

> 기준일: 2026-08-09
> 정책: **2026-08-01 이전 생성 GitHub Issue는 현재 우선순위 판단에서 제외한다.**
> 오래된 이슈/PRD는 완료 히스토리로 보지 않고, 현재 코드·운영 문서(`API.md`, `DOMAIN.md`, `FLOWS.md`, `PROJECT_HANDOFF.md`)를 기준으로 다시 스펙화한다.

---

## Now / Active

### F2R · BUILD IT 방향 정렬

- BUILD IT을 “AI 기반 Education Talent Accelerator”로 설명하는 사업 정의를 제품/문서 전반의 기준 문장으로 맞춘다.
- 홈페이지·Wiki·Shorts·Report·AI 도구는 개별 SaaS가 아니라 교육자 발굴·성장·검증 인프라라는 메시지를 유지한다.
- 새 기능은 DISCOVER → BUILD → GROW → INVEST → SCALE 중 어느 단계에 기여하는지 명시한다.

### 2026-08 이후 GitHub 이슈

- [#283](https://github.com/Juyudang/private_tutor_solution_back/issues/283) 비로그인 목적지를 `/`로 통일 — 페이지 404 리다이렉트 정리
  - 현재 문서상 `/home`과 `/` 목적지가 혼재한다.
  - 라우트/인증 실패/404/존재하지 않는 LMS handle 정책을 한 번에 정리한다.
- [#284](https://github.com/Juyudang/private_tutor_solution_back/issues/284) edu-holic 데모 학원 — 코드와 운영 DB 정합 맞추기
  - 로컬 seed, 운영 DB, 공개 라우트에서 edu-holic의 역할을 명확히 분리한다.

### 최근 완료된 8월 작업 — 참조용

- [#271](https://github.com/Juyudang/private_tutor_solution_back/issues/271) 학원 히어로 레이아웃 및 대표 수업 선택 개선
- [#275](https://github.com/Juyudang/private_tutor_solution_back/issues/275) 홈페이지 빌더 업로드 영상 선택 지원
- [#277](https://github.com/Juyudang/private_tutor_solution_back/issues/277) 운영 학원 영상 업로드 실패 수정
- [#285](https://github.com/Juyudang/private_tutor_solution_back/issues/285) 학원 온라인 LMS 1단계
- [#287](https://github.com/Juyudang/private_tutor_solution_back/issues/287) 강사 진입점을 LMS로 이관
- [#291](https://github.com/Juyudang/private_tutor_solution_back/issues/291) 랜딩을 Build It 브랜드로 전면 개편
- [#294](https://github.com/Juyudang/private_tutor_solution_back/issues/294) BUILD IT CHART 웹 연결
- [#296](https://github.com/Juyudang/private_tutor_solution_back/issues/296) BuildIt 학원 페이지 시안 반영

---

## Next Specs To Rewrite

오래된 이슈를 그대로 이어받지 말고, 아래 주제는 현재 코드/사업정의 기준으로 새 이슈를 다시 만든다.

### 1. DISCOVER — BUILD IT CHART

- 공개 `/chart`와 `/admin/chart`의 운영 목적을 사업 정의에 맞게 정리한다.
- 후보 수집/승인/거절, LLM key 운영, Google Discovery 데이터의 개인정보·저작권·검증 정책을 확정한다.
- 투자자/운영자에게 보여줄 “발굴 근거”와 내부 운영 데이터의 공개 경계를 분리한다.

### 2. BUILD — Academy Builder / LMS / 콘텐츠 인프라

- `/builder`, `/academies/{slug}`, `/lms/{handle}`의 역할을 다시 정렬한다.
- 강사 직접 입력형, 템플릿 자동 생성형, 유료 제작 대행형 중 현재 제품의 기본 UX를 결정한다.
- 홈페이지·홍보 Shorts·학교별 Wiki·학부모 Report·AI 교육도구가 하나의 성장 패키지처럼 보이도록 IA를 정리한다.

### 3. GROW — 상담/학생모집/오프라인 매출 검증

- Academy 방문 통계, 상담 CTA, 전환 이벤트, 학부모 리포트가 어떤 성장 지표로 이어지는지 정의한다.
- 단순 조회수가 아니라 상담→등록→매출로 이어지는 funnel을 문서화한다.
- 운영 DB에 실제로 쌓이는 값과 마케팅 문구에 노출 가능한 값을 분리한다.

### 4. INVEST / SCALE — 사업 확장 준비

- 성장성이 확인된 교육자를 투자자와 연결할 때 필요한 데이터룸/리포트 항목을 정의한다.
- 투자수익·성과배분 구조는 코드가 아니라 사업/계약 문서에서 별도 설계한다.
- 교재·온라인 강의·교육 IP로 확장할 때 필요한 도메인 모델은 별도 deep-interview 후 설계한다.

---

## Platform / Security Backlog

이 항목들은 오래된 이슈 번호를 승계하지 않고 새 기준으로 재검토한다.

- PR 단위 `./gradlew test` CI 추가 — 현재는 수동 배포 workflow만 있어 semantic conflict가 main에 들어갈 수 있다.
- dev/stg 브랜치와 운영 마이그레이션 상태 동기화 절차 점검.
- CSRF/Origin/Referer 정책 재검토 — HTTP-only cookie 인증 상태변경 API 보호.
- service-layer ownership guard 누락 가능성 재점검.
- 공개 API가 엔티티를 직접 반환하며 감사필드를 노출할 가능성 제거.
- 업로드 정책 통일 — PDF/이미지/영상의 MIME/magic bytes/용량/페이지/프레임/에러 메시지 표준화.
- storage orphan 정리 — DB rollback과 파일 저장 순서 불일치 개선.
- LLM 파싱 실패 로그/학생 답안 로그의 PII 노출 점검.

---

## 문서 정리 원칙

- 2026-08-01 이전 이슈 번호는 새 ROADMAP에 넣지 않는다.
- 완료 히스토리 나열은 최소화한다. 이미 구현된 내용은 `API.md`/`DOMAIN.md`/`FLOWS.md`의 현재 계약으로 유지한다.
- 오래된 PRD가 현재 제품 방향과 충돌하면 삭제하거나 `docs/archive/`로 내린다.
- 새 작업은 GitHub 이슈를 새로 만들고 `docs/CONTRIBUTING.md` 형식을 따른다.
