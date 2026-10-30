# 정책 페이지 정식 문서화 (2026-10-30)

대상: `/policies`, `/policies/privacy`, `/policies/terms`, `/policies/refunds`, `/policies/business`, `/exam-service/privacy`, `/exam-service/terms`, `/faq` 환불 안내.
NewStart는 홈페이지와 관련 없는 서비스라 홈페이지 정책·FAQ에서 언급과 링크를 뺐다(사용자 지시, 2026-10-30). `/privacy`(NewStart 확장 프로그램 안내) 페이지 자체는 그대로 둔다.
검토안·초안·확인 중·미확정 등 내부 작업 표시를 모두 지우고, 시행일 2026년 10월 30일의 정식 문서로 바꿨다.

## 문서에 쓴 내용의 근거

| 항목 | 근거 |
| --- | --- |
| 사업자 정보, 보호책임자 박정민(대표) | 사용자 지시(2026-10-30) |
| 회원 항목 | `CreateStudentRequest`·`CreateTutorRequest`, `User`·`Student` 엔티티 |
| 네이버 로그인 항목 | `NaverProfileIdentity` (id·이름·닉네임·이메일·휴대전화) |
| 학원 페이지 문의 1년 | `AcademyInquiry.retention_until` = 접수 + 365일 |
| 방문·클릭 기록 13개월 | `SiteEventRetentionJob` 기본 395일 |
| 맞춤 문제 자료 30일 | `ExamPrivacyRetentionService` 30일 기준, 기존 exam_policy 문구 |
| 거래 기록 5년·3년·6개월 | 전자상거래법 시행령 제6조 |
| 처리 위탁 AWS(서울) | `application-prd.yaml` S3·DB, ap-northeast-2 |
| 쿠키 | `CookieUtil`(ACCESS_TOKEN 24시간), `SiteVisitFilter`(F2R_VISITOR 90일), 네이버 로그인 쿠키(3~5분) |
| 청약철회 7일, 제17조 제2항 제5호, 3영업일 환급 | 전자상거래법 제17조·제18조 |
| 회차별 환불 표 | 사용자 운영 기준(1회 제공 후 15,000원), `HomeExamOffer` |

## 실제 삭제 방식 (2026-10-30 확인)

| 자료 | 문서 표기 | 실제 동작 |
| --- | --- | --- |
| 회원 정보 | 탈퇴 시 바로 삭제 | `DELETE /api/me`가 users 행을 삭제(연결 정보 cascade). 커뮤니티 글 등 RESTRICT FK가 있으면 탈퇴가 막힐 수 있음 |
| 네이버 회원 탈퇴 | 이메일 요청 → 담당자 삭제 | 관리자 삭제 화면 없음. 담당자가 DB에서 직접 삭제해야 함 |
| 학원 페이지 문의 | 1년 후 담당자 직접 삭제 | `retention_until`만 저장, 삭제 작업·화면 없음 |
| 버튼 클릭 기록 | 13개월 후 매일 자동 삭제 | `SiteEventRetentionJob` 04:20, 395일 |
| 방문 페이지·내려받기 기록 | 13개월 후 담당자 직접 삭제 | 삭제 작업 없음 |
| 맞춤 문제 자료(카카오톡·이메일) | 30일 이내 담당자 직접 삭제 | 시스템 밖 자료 — 수동 |
| 맞춤 문제 자료(사이트 신청) | 30일, 관리자 화면에서 직접 실행 | `ExamPrivacyRetentionService.erase()` — 아래 설정이 없어 409로 막힘. 사이트 신청 기능은 운영에서 꺼져 있음 |

### 학생 자료 30일 삭제가 막히는 원인과 수정 방안

- 원인: `erase()`는 `app.exam-intake.retention-reviewed-policy-version`이 `ExamIntake.PRIVACY`(`exam-privacy-20261029-v1`)와 같을 때만 실행된다. 이 값이 prd yaml·deploy 워크플로·EC2 env 어디에도 없다.
- 수정 방안: 사이트 신청 기능을 켤 때 EC2 `/etc/f2r/env`에 `APP_EXAM_INTAKE_RETENTION_REVIEWED_POLICY_VERSION=<그때의 PRIVACY 값>`을 넣는다. 이번 PR에서 /exam-service 안내 문구가 바뀌었으므로, 켜기 전에 `PRIVACY`·`TERMS` 버전을 올리고 같은 값으로 맞추는 것이 맞다. 환불 승인도 같은 방식(`app.exam-intake.refund-reviewed-policy-version`)으로 막혀 있다.

### 외부 서비스 확인 결과

- GTM-MMJ4MGNN 컨테이너: 2026-10-30 공개 스크립트 확인 결과 태그 0개(GA4·광고 태그 없음).
- 메일 발송: 홈페이지 기능은 메일을 보내지 않는다(비밀번호 찾기는 임시 비밀번호 방식, 메일 미발송). 사이트 신청 알림 메일은 기본 꺼짐.
- 해외 AI: Claude(`anthropic.enabled`) 운영 OFF, 홈 AI 비서는 브라우저 안 규칙 기반(서버·AI로 대화 전송 없음). 차트·문제 생성 도구의 Gemini/OpenAI 사용 여부와 전송 내용은 EC2 env 확인이 필요.
- 비밀번호 찾기 요청 시 아이디·이메일이 서버 로그에 남는다(로그 보존 기간 미설정).

## 문서와 코드가 아직 맞지 않는 곳 (후속 작업)

- 학원 페이지 문의 1년, 방문 기록(`site_page_visits`) 13개월: 자동 삭제 작업이 없다(문의는 보존 기한 값만 저장).
- 맞춤 문제 자료 30일 삭제: 관리자 수동 삭제이며 `app.exam-intake.retention-reviewed-policy-version` 미설정으로 막혀 있다(사이트 신청 기능은 운영에서 꺼져 있음).
- 회원 가입에 만 14세 확인·법정대리인 동의 절차가 없다(약관·방침은 만 14세 미만 법정대리인 동의를 요구).
- 네이버 로그인 회원은 비밀번호가 없어 스스로 탈퇴할 수 없다(방침에 연락처로 탈퇴 요청 안내).
- Google Tag Manager 컨테이너 설정(GA 등 국외 전송 여부)은 저장소에서 확인할 수 없다.
- `/exam-service` 정책 버전 문자열(`exam-privacy-20261029-v1` 등)은 바꾸지 않았다.
