# Feel2Real 색인 제외 조사·수정 전후 보고서 — 2026-10-26

## 결과

Google Search Console에 로그인해 URL-prefix 속성 `https://feel2real.co.kr/`의 보고서를 직접 확인했다. 요청한 5개 사유 **51개 URL 전부**를 확보하고 익명 운영 HTTP 응답과 대조했다. 전체는 색인 생성 40개·제외 61개다. 나머지 10개는 robots 차단 1개·크롤링됨/현재 미색인 9개로 이번 요청의 5개 사유 밖이다.

**sitemap은 Search Console에서 성공·처리 완료**, 마지막 읽기 2026-10-26, 제출 2026-10-20, 발견 23개·동영상 0개다. 이전 “가져올 수 없음”은 현재 재현되지 않으며 그 과거 원인을 확정할 로그는 없다. 재제출·검증 요청·운영 변경은 하지 않았다.

PR #905는 최신 main `c1c129b6f1dd5a397d7e1281a42bf771bf6b0b93` 기준의 독립 작업이다. #902 코드를 포함하지 않는다. 운영 DB·인증 권한·배포 변경 없음.

## 실제 오류와 정상 제외

| 사유 | GSC 수 | 현재 판정 |
|---|---:|---|
| 리디렉션 | 25 | 학원 서브도메인 301과 로그인 이동 유지. 과거 redirect였던 로그인 쿼리는 현재 200·루트 canonical. sitemap에 남은 301 alias만 제외 |
| 표준 없는 중복 | 9 | 회원가입 5개·TESTPREP 1개는 canonical 누락. 오래된 학원 3개는 API 404/HTML 200 불일치라 HTML도 404로 수정 |
| NOINDEX | 8 | Builder·NewStart 로그인·회원 자료, 정상 제외 유지 |
| 401 | 8 | 관리자·개인정보·인증 API, 정상 GET 차단 유지 |
| 404 | 1 | 실제 주소는 `https://feel2real.co.kr/*`, 404 유지 |

## robots·sitemap·모바일 검사

- robots.txt: HTTP 200, 68 bytes, `User-agent: *`, `Allow: /`, HTTPS sitemap 선언.
- sitemap.xml: HTTP 200, XML Content-Type, UTF-8 1,853 bytes, 표준 namespace 파싱 성공. 고유 URL 23개 중 16개 200·7개 301, 401/404/noindex 항목 없음.
- 모바일·Googlebot Smartphone UA에서도 robots/sitemap/홈/wiki는 200. sitemap 23개 파싱 성공. UA 검사만으로 Google IP 접근을 증명한 것은 아니지만, 별도로 GSC의 실제 sitemap 처리 성공도 확인했다.
- 학원 서브도메인 도착지 7개는 모바일·Googlebot UA에서 200. 해당 호스트 canonical과 별도 sitemap은 현 URL-prefix 속성 범위 밖이다. 무단 cross-site sitemap 제출이나 apex로 일괄 canonical 강제하지 않음.
- HTTP 루트는 HTTPS 301, www 루트는 미등록 학원 handle로 404. GSC 이번 51개에는 www가 없으므로 임의 복구하지 않았다.

## 실제 GSC URL 전체 목록

공통 prefix: `https://feel2real.co.kr`. **전 행 색인 상태는 GSC 제외**이다. 보고서 최종 업데이트 표시는 2026-10-19, 각 사유의 “처음 감지된 날짜”는 2026-10-20으로 표시됐다. 서로 다른 필드를 표시된 값 그대로 기록했다. 아래 날짜는 **URL 최종 크롤링일**이며 최초 발견일이 아니다. GSC 예시 표가 URL별 최초 발견일을 제공하지 않으므로 `url_first_discovered=null`로 기록했다. 사유 최초 감지일을 URL 발견일로 바꿔 적지 않았다.

| 사유 | 실제 URL 경로 | 최종 크롤링 | 현재 HTTP | sitemap | 판단 |
|---|---|---|---:|:---:|---|
| 리디렉션 | `/testprep/guide` | 2026-10-20 | 302 | 없음 | 정상 로그인 이동 또는 기존 noindex 유지 |
| 리디렉션 | `/?login=required&returnUrl=/problem-generator` | 2026-10-20 | 200 | 없음 | 현재 200·루트 canonical 확인; 과거 redirect 상태, 추가 수정 불필요 |
| 리디렉션 | `/?login=required&returnUrl=/choonggi/admin` | 2026-10-20 | 200 | 없음 | 현재 200·루트 canonical 확인; 과거 redirect 상태, 추가 수정 불필요 |
| 리디렉션 | `/choonggi/admin` | 2026-10-20 | 302 | 없음 | 정상 로그인 이동 또는 기존 noindex 유지 |
| 리디렉션 | `/my/profile` | 2026-10-20 | 200 | 없음 | 회원/관리 화면 틀 200; noindex 헤더 보완, API 인증 유지 |
| 리디렉션 | `/admin/students` | 2026-10-19 | 200 | 없음 | 회원/관리 화면 틀 200; noindex 헤더 보완, API 인증 유지 |
| 리디렉션 | `/admin/academies` | 2026-10-18 | 200 | 없음 | 회원/관리 화면 틀 200; noindex 헤더 보완, API 인증 유지 |
| 리디렉션 | `/?login=required&returnUrl=/builder` | 2026-10-16 | 200 | 없음 | 현재 200·루트 canonical 확인; 과거 redirect 상태, 추가 수정 불필요 |
| 리디렉션 | `/?login=required` | 2026-10-14 | 200 | 없음 | 현재 200·루트 canonical 확인; 과거 redirect 상태, 추가 수정 불필요 |
| 리디렉션 | `/problem-generator` | 2026-10-14 | 200 | 없음 | 정상 로그인 이동 또는 기존 noindex 유지 |
| 리디렉션 | `/stdt_dashboard` | 2026-10-13 | 200 | 없음 | 회원/관리 화면 틀 200; noindex 헤더 보완, API 인증 유지 |
| 리디렉션 | `/tutor/academy/lessons/editor` | 2026-10-10 | 302 | 없음 | 정상 로그인 이동 또는 기존 noindex 유지 |
| 리디렉션 | `/tutor/academy` | 2026-10-04 | 200 | 없음 | 회원/관리 화면 틀 200; noindex 헤더 보완, API 인증 유지 |
| 리디렉션 | `/newstart*` | 2026-10-01 | 404 | 없음 | 현재 문자 그대로 별표 경로 404; 복구하지 않음 |
| 리디렉션 | `/academies/모두영어` | 2026-09-06 | 301 | 포함 | 정상 학원 서브도메인 301 유지; 현 sitemap 포함 시 alias 제외 |
| 리디렉션 | `/academies/wendy-영어` | 2026-08-28 | 301 | 포함 | 정상 학원 서브도메인 301 유지; 현 sitemap 포함 시 alias 제외 |
| 리디렉션 | `/academies/edu-holic-academy` | 2026-08-09 | 301 | 없음 | 정상 학원 서브도메인 301 유지; 현 sitemap 포함 시 alias 제외 |
| 리디렉션 | `/academies/새-학원` | 2026-08-08 | 301 | 없음 | 정상 학원 서브도메인 301 유지; 현 sitemap 포함 시 alias 제외 |
| 리디렉션 | `/academies/에이닷-영어학원-의정부지점` | 2026-08-07 | 301 | 포함 | 정상 학원 서브도메인 301 유지; 현 sitemap 포함 시 alias 제외 |
| 리디렉션 | `/academies/압구정에듀홀릭` | 2026-08-05 | 301 | 포함 | 정상 학원 서브도메인 301 유지; 현 sitemap 포함 시 alias 제외 |
| 리디렉션 | `/academies/제목은-수정가능-한글-마지막-두번써지는것-확인인-수정사항항` | 2026-07-30 | 301 | 포함 | 정상 학원 서브도메인 301 유지; 현 sitemap 포함 시 alias 제외 |
| 리디렉션 | `/tutor/academy/videos` | 2026-07-28 | 200 | 없음 | 회원/관리 화면 틀 200; noindex 헤더 보완, API 인증 유지 |
| 리디렉션 | `/admin/analyzes` | 2026-07-28 | 200 | 없음 | 회원/관리 화면 틀 200; noindex 헤더 보완, API 인증 유지 |
| 리디렉션 | `/tutor/academy/posts` | 2026-07-11 | 200 | 없음 | 회원/관리 화면 틀 200; noindex 헤더 보완, API 인증 유지 |
| 리디렉션 | `/admin/lesson-communities` | 2026-07-03 | 200 | 없음 | 회원/관리 화면 틀 200; noindex 헤더 보완, API 인증 유지 |
| 표준 없는 중복 | `/testprep?login=required&returnUrl=/testprep/guide` | 2026-10-20 | 200 | 없음 | 수정: 쿼리 제외한 /register 또는 /testprep canonical |
| 표준 없는 중복 | `/register?returnUrl=/community` | 2026-10-13 | 200 | 없음 | 수정: 쿼리 제외한 /register 또는 /testprep canonical |
| 표준 없는 중복 | `/register` | 2026-10-09 | 200 | 없음 | 수정: 쿼리 제외한 /register 또는 /testprep canonical |
| 표준 없는 중복 | `/register?returnUrl=/home` | 2026-10-06 | 200 | 없음 | 수정: 쿼리 제외한 /register 또는 /testprep canonical |
| 표준 없는 중복 | `/register?returnUrl=/community/posts/1` | 2026-09-06 | 200 | 없음 | 수정: 쿼리 제외한 /register 또는 /testprep canonical |
| 표준 없는 중복 | `/academies/henry-고등부-내신영어` | 2026-08-04 | 200 | 없음 | 수정: 공개 API 404인데 HTML 200 빈 화면; 운영 base-domain 설정 시 HTML도 404 |
| 표준 없는 중복 | `/academies/테스트학원` | 2026-07-31 | 200 | 없음 | 수정: 공개 API 404인데 HTML 200 빈 화면; 운영 base-domain 설정 시 HTML도 404 |
| 표준 없는 중복 | `/academies/테스트-아카데미` | 2026-07-28 | 200 | 없음 | 수정: 공개 API 404인데 HTML 200 빈 화면; 운영 base-domain 설정 시 HTML도 404 |
| 표준 없는 중복 | `/register?returnUrl=/tutor/academy/posts/new` | 2026-07-17 | 200 | 없음 | 수정: 쿼리 제외한 /register 또는 /testprep canonical |
| NOINDEX | `/builder` | 2026-10-19 | 200 | 없음 | 정상 로그인/회원/제작 화면 제외; 기존 noindex 또는 로그인 이동 유지 |
| NOINDEX | `/newstart/login` | 2026-10-04 | 200 | 없음 | 정상 로그인/회원/제작 화면 제외; 기존 noindex 또는 로그인 이동 유지 |
| NOINDEX | `/newstart/login?next=/newstart/updates/59` | 2026-10-04 | 200 | 없음 | 정상 로그인/회원/제작 화면 제외; 기존 noindex 또는 로그인 이동 유지 |
| NOINDEX | `/newstart/updates/59` | 2026-10-04 | 302 | 없음 | 정상 로그인/회원/제작 화면 제외; 기존 noindex 또는 로그인 이동 유지 |
| NOINDEX | `/newstart/login?next=/newstart/updates/58` | 2026-10-04 | 200 | 없음 | 정상 로그인/회원/제작 화면 제외; 기존 noindex 또는 로그인 이동 유지 |
| NOINDEX | `/newstart/updates/58` | 2026-10-04 | 302 | 없음 | 정상 로그인/회원/제작 화면 제외; 기존 noindex 또는 로그인 이동 유지 |
| NOINDEX | `/newstart/login?next=/newstart/thanks-guide` | 2026-10-04 | 200 | 없음 | 정상 로그인/회원/제작 화면 제외; 기존 noindex 또는 로그인 이동 유지 |
| NOINDEX | `/newstart/thanks-guide` | 2026-10-04 | 302 | 없음 | 정상 로그인/회원/제작 화면 제외; 기존 noindex 또는 로그인 이동 유지 |
| 401 | `/api/admin/students` | 2026-10-20 | 401 | 없음 | 정상 인증 제외; GET 401 유지 |
| 401 | `/api/me/oauth` | 2026-08-03 | 401 | 없음 | 정상 인증 제외; GET 401 유지 |
| 401 | `/api/me` | 2026-08-03 | 401 | 없음 | 정상 인증 제외; GET 401 유지 |
| 401 | `/api/admin/academies` | 2026-07-10 | 401 | 없음 | 정상 인증 제외; GET 401 유지 |
| 401 | `/api/users/tutors` | 2026-07-02 | 401 | 없음 | 정상 인증 제외; GET 401 유지 |
| 401 | `/api/auth/login` | 2026-07-01 | 401 | 없음 | 정상 인증 제외; GET 401 유지 |
| 401 | `/api/users/students` | 2026-07-01 | 401 | 없음 | 정상 인증 제외; GET 401 유지 |
| 401 | `/api/auth/logout` | 2026-06-11 | 401 | 없음 | 정상 인증 제외; GET 401 유지 |
| 404 | `/*` | 2026-10-16 | 404 | 없음 | 문자 그대로 /* 주소; 404 유지 |

## 원인 분석 및 수정 전후

| 항목 | 현재 운영 | PR 적용 후 예상 |
|---|---|---|
| sitemap | 서브도메인으로 301 이동하는 학원 홈 7개 포함 | 기존 공개·게시 조건 유지, handle 있는 홈 alias만 제외. 동일 데이터라면 23→16개. handle 없는 legacy 홈은 보존 |
| register·testprep | 복귀 쿼리 포함 URL에 canonical 없음 | 서버 head에서 각각 HTTPS `/register`, `/testprep`로 통일. 복귀 기능 유지 |
| 오래된 학원 3개 | 공개 API 404, HTML은 같은 빈 셸 200 | 운영 base-domain이 설정된 경로 진입에서 존재·공개·게시 여부 확인 후 404. 존재하는 공개 학원 301 및 legacy 200 유지 |
| 공개 학원 목록·블로그·privacy | canonical 없음 | 고정 URL 및 인코딩한 MVC slug 기반 canonical. Host/forwarded host/쿼리 제외 |
| 관리자·강사·회원·Studio 화면 틀 | 일부 HTML 200, noindex 없음 | 한정 경로 X-Robots-Tag noindex,nofollow. 인증·401/403·로그인 이동은 불변 |
| NewStart 회원 자료·Builder | 기존 noindex 또는 로그인 이동 | 유지 |
| 문자 그대로 /*, newstart* 등 | 404 | 유지, 홈으로 강제 이동하지 않음 |

`/`와 `/home`은 서로 다른 서비스 템플릿이므로 이름만 보고 합치지 않았다. 새로 확인한 학원 3개에 대응할 공개 콘텐츠가 없어 임의 301을 만들지 않는다.

## 구현·보호 범위

- 기존 AcademyRepository의 PUBLISHED+visible query로 sitemap 작성, 운영 DB 읽기 조건만 재사용. schema/migration/write 없음.
- canonical은 서버 렌더 head에 한 개. 쿼리·외부 Host로 대표 URL이 바뀌지 않는다.
- HeaderWriter는 `/admin`, `/api/admin`, `/ai-agent-studio`, `/api/studio`, `/tutor`, `/my`와 하위 경로 및 정확한 `/dashboard`, `/studio-projects`, `/stdt_dashboard`, `/tutor_dashboard`에만 적용. 인증 허용 목록은 변경하지 않는다.
- 프리뷰·미공개 Studio 자료를 sitemap에 넣지 않으며 기존 401/403 경계 유지.

## 검증

- 1차 코드: JDK21 컴파일 및 테스트 **92건 전부 통과**(XML/sitemap·canonical 실제 렌더링·header·기존 404·Studio 권한).
- 최초 다운로드/Mockito 동적 attach 문제는 로컬 실행 환경 문제였다. 의존성 연결 설정과 명시적 Mockito javaagent로 해결했으며 검증을 건너뛰지 않았다. 로컬 환경용 설정은 저장소에 커밋하지 않았다.
- GSC 대조 후 최종 코드: **97건 전부 통과, 실패 0건**. 학원 301·한글 slug·legacy 200·누락/비공개 404, canonical 실제 렌더링, 헤더, Studio 80건 및 404 6건 포함.
- 1차 PR HEAD `1958c8c`의 Build Check #2217은 build·mysql-integration-test 모두 성공. 최종 추가 변경의 CI는 갱신 후 확인한다.
- CI Build Check는 PR 전용. main 병합은 운영 배포를 유발할 수 있으므로 별도 승인 전 병합하지 않는다.

## 자료

- [GSC 실제 51개 URL·최종 크롤링·HTTP·sitemap 판정 JSON](gsc-urls-20261026.json)
- [최초 사이트맵 23개·모바일·Googlebot UA 응답 JSON](production-http-20261026.json)
- Google 공식: https://developers.google.com/search/docs/crawling-indexing/sitemaps/build-sitemap
- Google 공식: https://developers.google.com/search/docs/crawling-indexing/consolidate-duplicate-urls

## 배포 후 확인(별도 승인 필요)

승인 배포 후 sitemap 16개 예상값(데이터 동일 시), 실제 301·404·canonical·private noindex·인증 회귀를 재검사한다. GSC의 현재 성공 상태를 실패로 오인해 불필요한 인증 해제나 전역 noindex 제거를 하지 않는다. Google의 재크롤링·대표 URL 선택·색인 반영 시점은 코드 수정 완료와 별개다.
