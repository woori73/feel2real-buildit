# 프로그램 공개 첫 화면 + 네이버 로그인 복귀 설계

> 상태: 구현 PR #703(공통 기반·DesignCraft), #704(`/builder`), #706(문제지 제작기), #707(이지장부·Easy), #708(LMS 운영·대화형 학습). 입시상담 AI는 **미배포**라 설계만 반영한다(§6).
> 규칙·스펙 단일 출처는 `docs/`이며, 엔드포인트 계약은 [API.md](../API.md)의 "프로그램 공개 첫 화면" 절이 기준이다.

## 1. 원칙
1. 각 프로그램의 대표 URL은 비회원에게도 정상 첫 화면(HTTP 200)을 보여준다. 이름·주요 기능·설명·시작 버튼을 담는다.
2. 비회원이 실제 기능을 시작하면 **기존 네이버 로그인**(`/oauth2/authorization/naver`)으로 이동하고, 성공하면 누른 기능 화면으로 복귀한다. 새 로그인 체계를 만들지 않는다.
3. 로그인 상태면 재로그인 없이 바로 기능으로 간다.
4. 권한(STUDENT·TUTOR·ADMIN)이 필요한 기능은 로그인 후에도 검사한다. 권한 없는 로그인 사용자는 브라우저 기본 403 대신 **서비스 안내 화면**(상태 403)을 본다.
5. API 인증·인가는 완화하지 않는다. 화면 공개는 소개까지이며 회원 데이터·작업물은 서버가 보호한다.
6. **신규 네이버 회원에게 TUTOR·ADMIN을 자동 부여하지 않는다**(네이버 자기가입 role은 STUDENT). 강사 전환 절차는 이 설계의 범위 밖이다.
7. ADMIN은 모든 프로그램 화면에 접근한다(화면 접근 허용). **다른 회원의 작업물·개인 데이터를 API로 열람하는 관리자 권한은 별도 승인·개인정보 영향 점검 전까지 구현하지 않는다**(예: DesignCraft `requireOwned`, LMS 운영 화면의 소유 강사 전용 정책 `AcademyLmsSecurityIntegrationTest` AC-7).
8. NewStart·위메이드·TESTPREP의 기존 인증/화면은 이 설계가 바꾸지 않는다.

## 2. 구성요소 (`program` 패키지)
| 구성요소 | 역할 |
|---|---|
| `Program` enum | 프로그램 등록부: 이름·설명·주요 기능·소개/시작 경로·허용 role·안내 문구. 새 프로그램은 한 줄 추가 |
| `GET /auth/naver/start?returnUrl=` | 복귀 쿠키(`oauth_return_url`, HttpOnly, 5분, Lax, 내부 경로만, **등록된 프로그램 영역만**)를 심고 네이버 로그인으로 302 |
| `ReturnUrlPolicy` | open redirect 방지(외부 스킴·`//`·`\`·`..`·제어문자 차단). 로그인 성공 핸들러가 한 번 더 검증 |
| `ProgramEntry` | 상태 판정 ANON / FORBIDDEN / ALLOWED와 소개 렌더 |
| `program/intro.html` | 공통 소개·안내 화면. 네이버 시작 버튼 + 아이디 계정용 "아이디로 로그인하기"(기존 랜딩 로그인 모달, 복귀 URL 유지) |
| `ProgramIntroController` | 뷰 컨트롤러로 매핑된 대표 URL용 forward 대상 `/program/intro`, 소개 없는 하위 기능용 `/program/notice` |

## 3. 진입 방식 두 가지
- **컨트롤러형**(대표 URL을 처리하는 컨트롤러가 있을 때): 컨트롤러가 `ProgramEntry.stateOf`로 판정해 소개/안내 또는 실제 화면을 반환(DesignCraft, `/builder`, `/problem-generator`).
- **보안 계층형**(뷰 컨트롤러로 매핑된 URL): 비회원은 인증 진입점, 권한 부족은 접근 거부 처리기가 `/program/intro`로 forward(`/ledger`, `/easy`). MockMvc는 forward를 따라가지 않으므로 테스트는 forward 대상·속성과 소개 렌더를 각각 확인하고, 실제 forward는 브라우저 QA로 확인한다.
- 하위 화면은 소개를 따로 만들지 않고, 비회원 → `/?login=required&returnUrl=<원래 경로>`(랜딩 로그인 모달), 권한 부족 → 소개 경로 또는 `/program/notice` 안내.

## 4. 프로그램 현황
| 프로그램 | 대표 URL | 허용 role | 공개 첫 화면 |
|---|---|---|---|
| DesignCraft | `/designcraft`, `/designcraft/editor` | TUTOR, ADMIN | 신규(#703) |
| BuildIt 제작기 | `/builder` | TUTOR, ADMIN | 신규(#704) |
| 문제지 제작기 | `/problem-generator` | STUDENT, TUTOR, ADMIN | 신규(#706) |
| 이지장부 | `/ledger` | TUTOR, ADMIN | 신규(#707) |
| Easy 장부 | `/easy` | ADMIN | 신규(#707) |
| LMS 운영·대화형 학습 | `/lms/{handle}/manage` 등, `/tutor/conversation*` | 소유 강사 / TUTOR | 로그인 복귀 + 안내(#708) |
| TESTPREP | `/testprep` | (하위) STUDENT·TUTOR·ADMIN | 기존(#694) |
| 내신위키 | `/wiki` | (하위) 로그인 | 기존 |
| NewStart·위메이드 | `/newstart`, `/wemade` | 자체 정책 | 기존 유지 |

## 5. 새 프로그램 추가 체크리스트
1. `Program`에 항목 추가(권한은 기존 SecurityConfig 정책을 옮기고, 완화하지 않는다).
2. 대표 URL이 컨트롤러면 `ProgramEntry`로 분기, 뷰 매핑이면 SecurityConfig 인증 진입점·접근 거부 처리기에 forward 추가.
3. SecurityConfig에서 대표 URL(GET)만 `permitAll`, 하위 경로·API 규칙은 그대로.
4. 테스트(given/when/then): 비회원 200, 권한 부족 안내(403 본문), 허용 role 통과, 시작 엔드포인트 쿠키, 악성 URL 무쿠키, API 비회원 401.
5. **`.github/workflows/build-check.yml`의 `--tests` 허용 목록에 테스트 클래스 등록**(허용 목록 밖은 CI가 실행하지 않는다).
6. `docs/API.md` 동기화, PC 1440px·모바일 390px 브라우저 QA.

## 6. 입시상담 AI (미배포 — 설계 반영)
- 홈에는 "출시 예정"으로만 표시하고 오픈 서비스 목록에 넣지 않는다. 운영 jar에 화면·경로가 없다. 관련 PR #692는 main에 없다.
- 배포 시 이 설계를 따른다: ① 대표 URL을 확정하고 `Program`에 등록(URL은 구현 시 정한다 — 이 문서는 임의 URL을 만들지 않는다) ② 비회원에게 공개 소개(200: 이름·주요 기능·설명·"네이버 로그인하고 시작하기") ③ 상담 시작 시 네이버 로그인 → 복귀 ④ 허용 role과 일반 회원 이용 기능 / 강사·관리자 전용 기능의 구분은 구현 시 소유자 결정(자동 권한 부여 없음) ⑤ 상담 내용·학생 정보 API는 인증·소유자 격리 유지, 공개 소개에는 개인정보를 싣지 않는다 ⑥ 공식 입시자료 근거 안내는 소개 화면에도 같은 문구를 쓴다.
- 배포 전 확인: 홈 "출시 예정" → "오픈 서비스" 이동 시점은 운영 배포·화면 검증 후 소유자 승인으로 정한다.
