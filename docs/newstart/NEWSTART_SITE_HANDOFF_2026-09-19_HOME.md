# NewStart Site & Login Home Handoff — 2026-09-19

> 학원 PC(Academy PC)에서 집 PC(Home PC)로 NewStart 공개 사이트 및 로그인/회원 연동 작업을 인계하기 위한 문서입니다.

---

## 1. GITHUB & BASE STATE

- **Base Main SHA:** `80ffbddef10ba88f5a60b33d303da4b4667a8f46`
- **Handoff Branch:** `newstart/site-handoff-20260919-home`
- **Commit SHA:** `ef395ba8d1b11ae10714ed997a0a6565191fcd53`
- **Related PRs & Commits:**
  - **PR #322** (`b67fdb6a`): `/newstart` 공개 MVP 페이지 생성 (`NewStartPageController`, `index.html`, `newstart.css`, `newstart.js`)
  - **PR #323** (`083a9333`): Navigation에서 Easy 링크 제거
  - **PR #324** (`7ce4539d`): 서비스 브랜드명을 "새출발기금 부실차주"로 변경

---

## 2. CURRENT IMPLEMENTATION STATUS

### 웹 컨트롤러 & 뷰
- **Controller:** [NewStartPageController.java](file:///c:/Users/VIEW%20LIFE/feel2real-buildit/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/newstart/controller/NewStartPageController.java) (`GET /newstart`, `Cache-Control: private, no-store`)
- **Template:** [templates/newstart/index.html](file:///c:/Users/VIEW%20LIFE/feel2real-buildit/src/main/resources/templates/newstart/index.html)

### 로그인 UI 상태 (Front-end)
- BuildIt 공통 `fragments/_account_menu`의 `accountMenuControls('/newstart', 'NewStart')` 및 `loginModal` 프래그먼트가 정상 탑재됨.
- 사용자는 `/newstart` 상단에서 로그인/회원가입 모달을 열고 인증을 진행할 수 있음.

### 회원 백엔드 연동 상태 (Back-end — Pending)
- **현재 상태:** 클라이언트 전용 MVP 단계.
- **미연결 항목:**
  1. ① 내 상황 메모 (채무 현황, 진행 단계, 기관 메모, 제출 서류 등) DB 저장 API
  2. ② 오늘 해야 할 일 체크리스트 개인별 저장/조회 API
  3. ③ NewStart AI 질문 전송 및 대화 히스토리 DB 연동
- 입력 양식은 민감정보 입력을 방지하고 안내 문구를 노출하며 서버 저장은 비활성화되어 있음.

---

## 3. 🚨 FREEBUFF AGENT CO-WORKING NOTICE & WARNING

- **상황:** 현재 다른 워커 Agent인 **Freebuff**가 NEWSTART 사이트의 **채팅 / AI 질의응답 기능**을 별도 작업 공간에서 독립 개발 중입니다.
- **중복 구현 방지 경고:**
  - Antigravity에서는 NewStart 소스코드 및 채팅/AI 기능을 중복 구현하거나 수정하지 않습니다.
  - Freebuff의 작업과 충돌할 수 있는 컨트롤러/서비스/테스트 파일 modifications를 금지합니다.
- **협업 원칙:**
  - 집 PC에서 재개 시 **Freebuff가 생성한 PR/Branch 결과를 가장 먼저 확인**합니다.
  - Freebuff의 채팅/AI 구현이 완료/머지되면 해당 베이스 위에서 회원별 DB 저장 연동 작업을 이어서 진행합니다.

---

## 4. NEXT STEPS AT HOME (집 PC 작업 순서)

1. `git fetch origin` 후 Freebuff Agent의 채팅/AI PR 또는 Feature branch 존재 여부 확인
2. Freebuff 결과물이 있을 경우 해당 브랜치를 우선 확인 및 베이스로 설정
3. 회원 로그인 세션(`SecurityContext` / `UserDetails`)과 연동되는 개인 상황 메모/체크리스트 백엔드 Entity, Repository, Service, API Controller 개발
4. Freebuff가 구축한 AI 채팅 컴포넌트와 회원 세션 대화 저장소 연동

---

## 5. SAFETY INVARIANTS

- ❌ Antigravity에서 NewStart 소스코드/테스트 임의 수정 금지
- ❌ Freebuff 채팅/AI 기능 중복 구현 금지
- ❌ Main 직접 commit / merge / deploy 금지

