# Feel2Real 색인 제외 조사 · 2026-10-26

## 상태와 조사 범위

운영 변경·DB 변경·배포 없음. 최신 main `c1c129b6f1dd5a397d7e1281a42bf771bf6b0b93`에서 시작한 독립 PR이며 #902를 포함하지 않는다.

Search Console 속성 `https://feel2real.co.kr/`를 열었으나 Google 로그인 화면으로 연결됐다. 따라서 아래는 **실제 운영 HTTP 검사 목록**이며, Google 보고서의 실제 제외 URL 목록이 아니다. 사유별 보고서 건수, URL 발견 날짜, Google 색인 상태, Google 선택 canonical, 과거 sitemap 가져오기 오류 원인은 아직 미확인이다. 모든 URL의 발견 날짜·색인 상태는 미확인으로 기록한다. 사이트맵 포함 여부는 이번에 내려받은 XML 기준이다.

## 사이트맵·robots 검사

- robots.txt: HTTP 200, 68 bytes, `User-agent: *`, `Allow: /`, HTTPS sitemap 선언. 차단 없음.
- sitemap.xml: HTTP 200, `application/xml;charset=UTF-8`, UTF-8 1,853 bytes, 표준 sitemap namespace XML 파싱 성공, 고유 URL 23개. 로그인 이동 없음.
- 23개 중 16개 HTTP 200, 7개 HTTP 301. 401·404·noindex URL은 현재 sitemap 안에서 발견되지 않았다.
- 모바일 UA와 Googlebot Smartphone UA로 robots/sitemap/홈/wiki 응답 200, sitemap 23개 파싱 확인. **UA 검사는 실제 Googlebot IP에서의 접근 또는 Google의 가져오기 성공을 입증하지 않는다.**
- HTTP 루트는 HTTPS로 301. www 루트는 404(학원 서브도메인 필터에서 미등록 handle로 처리). GSC 실제 유입 URL 확인 전 임의 복구하지 않는다.

## URL별 운영 검사

공통 prefix: `https://feel2real.co.kr`. Google 발견 날짜·색인 상태는 전 행 미확인.

| URL 경로 | HTTP | sitemap | 판정·조치 |
|---|---:|:---:|---|
| `/` | 200 | 예 | 공개 200·canonical 있음; 유지 |
| `/home` | 200 | 예 | 공개 200·canonical 있음; 유지 |
| `/faq` | 200 | 예 | 공개 200·canonical 있음; 유지 |
| `/newstart` | 200 | 예 | 공개 200·canonical 있음; 유지 |
| `/exam` | 200 | 예 | 공개 200·canonical 있음; 유지 |
| `/wiki` | 200 | 예 | 공개 200·canonical 있음; 유지 |
| `/privacy` | 200 | 예 | 공개 200; canonical 추가 |
| `/academies` | 200 | 예 | 공개 200; canonical 추가 |
| `/academy-posts` | 200 | 예 | 공개 200; canonical 추가 |
| `/academies/새-학원-3` | 301 | 예 | 정상 301 유지; sitemap에서 이전 주소 제외. 대상: https://academy-2f7pul.feel2real.co.kr/ |
| `/academies/새-학원-3/posts` | 200 | 예 | 공개 200; canonical 추가 |
| `/academies/새-학원-2` | 301 | 예 | 정상 301 유지; sitemap에서 이전 주소 제외. 대상: https://academy-1u9rzi.feel2real.co.kr/ |
| `/academies/새-학원-2/posts` | 200 | 예 | 공개 200; canonical 추가 |
| `/academies/에이닷-영어학원-의정부지점` | 301 | 예 | 정상 301 유지; sitemap에서 이전 주소 제외. 대상: https://academy-4pxqk4.feel2real.co.kr/ |
| `/academies/에이닷-영어학원-의정부지점/posts` | 200 | 예 | 공개 200; canonical 추가 |
| `/academies/wendy-영어` | 301 | 예 | 정상 301 유지; sitemap에서 이전 주소 제외. 대상: https://academy-1mgqpa.feel2real.co.kr/ |
| `/academies/wendy-영어/posts` | 200 | 예 | 공개 200; canonical 추가 |
| `/academies/압구정에듀홀릭` | 301 | 예 | 정상 301 유지; sitemap에서 이전 주소 제외. 대상: https://eduholic.feel2real.co.kr/ |
| `/academies/압구정에듀홀릭/posts` | 200 | 예 | 공개 200; canonical 추가 |
| `/academies/제목은-수정가능-한글-마지막-두번써지는것-확인인-수정사항항` | 301 | 예 | 정상 301 유지; sitemap에서 이전 주소 제외. 대상: https://academy-2ir7a2.feel2real.co.kr/ |
| `/academies/제목은-수정가능-한글-마지막-두번써지는것-확인인-수정사항항/posts` | 200 | 예 | 공개 200; canonical 추가 |
| `/academies/모두영어` | 301 | 예 | 정상 301 유지; sitemap에서 이전 주소 제외. 대상: https://modooeng.feel2real.co.kr/ |
| `/academies/모두영어/posts` | 200 | 예 | 공개 200; canonical 추가 |
| `/login` | 200 | 아니오 | 추가 관찰; GSC 실제 제외 여부 미확인 |
| `/dashboard` | 302 | 아니오 | 회원 전용 로그인 이동 유지; noindex 헤더 보완 |
| `/ai-agent-studio` | 302 | 아니오 | 회원 전용 로그인 이동 유지; noindex 헤더 보완 |
| `/admin` | 404 | 아니오 | 404 유지; GSC·외부 링크 확인 전 임의 복구 안 함 |
| `/seo-audit-missing-20261026` | 404 | 아니오 | 404 유지; GSC·외부 링크 확인 전 임의 복구 안 함 |
| `http://feel2real.co.kr/` | 301 | 아니오 | HTTPS 영구 이동 유지 |
| `https://www.feel2real.co.kr/` | 404 | 아니오 | 404 유지; GSC·외부 링크 확인 전 임의 복구 안 함 |
| `/api/studio/catalog` | 401 | 아니오 | 정상 비공개 API 차단 유지 |
| `/admin/hub` | 200 | 아니오 | 관리자 화면 틀에 noindex 누락; 헤더 보완 |
| `/exam-analysis` | 200 | 아니오 | 기존 meta robots=noindex 유지 |
| `/academies?utm_source=audit` · `?utm_source=other` | 200 | 아니오 | canonical 없음; `/academies`로 통일 |

## 5가지 제외 사유의 분류

| GSC 사유 | 직접 검사에서 확인한 사례 | 판단 |
|---|---|---|
| 리디렉션 | sitemap 학원 URL 7개, 대시보드·Studio 로그인 이동, HTTP→HTTPS | 301/302 자체는 정상. sitemap에 301 alias가 실린 부분만 수정 |
| 표준 없는 중복 | 학원 목록·블로그 목록·privacy canonical 누락, 학원 목록 쿼리 변형 | 누락 확인. 실제 Google 중복 판정은 로그인 후 대조 필요 |
| NOINDEX | `/exam-analysis` meta noindex | 현 정책 유지. GSC 실제 URL이 공개 필수 페이지인지 추가 대조 필요 |
| 401 | `/api/studio/catalog` | 정상 인증 경계. 인증 해제 금지 |
| 404 | `/admin`, 임의 없는 경로, www 루트 | 없는 페이지 유지. Google 실제 404 목록 확보 후 필요한 주소만 별도 판단 |

## 원인과 수정 전후(코드 기준, 운영 적용 전)

| 항목 | 운영 현재 | PR 적용 후 예상 |
|---|---|---|
| apex sitemap | 23개 중 7개는 서브도메인으로 301 | 기존 게시·공개 조건 유지, handle 있는 301 홈 alias만 제외. 동일 데이터라면 16개 |
| 미이전 학원 홈 | 경로형 URL 제공 | handle 없거나 base-domain 미설정이면 경로형 포함 유지 |
| 공개 목록·privacy | canonical 없음 | 고정 HTTPS 대표 URL |
| 학원 블로그 목록·상세 | 서버 HTML에 canonical 없음 | MVC 경로 변수로 URL 인코딩, 쿼리·Host·forwarded host 제외, 서버 head에 한 개 출력 |
| 관리자/Studio/개인 대시보드 | 일부 200 화면 틀에 noindex 없음 | 한정된 경로에 X-Robots-Tag: noindex, nofollow. 인증·리디렉션·401/403 유지 |
| 404 | 정상 404 | 그대로 유지 |

`/`는 Feel2Real 랜딩, `/home`은 별도 학원 홈 템플릿이다. 이름만 보고 중복으로 합치지 않았다. 서브도메인 최종 학원 홈 7개는 모바일·Googlebot UA 모두 200이나 canonical이 없었다. 해당 호스트는 현재 URL-prefix 속성 범위 밖이므로 이번 PR에서 cross-site sitemap을 만들거나 일괄 apex canonical을 강제하지 않는다.

## 검증

- `git diff --check` 통과.
- 신규 테스트: XML 파싱·공개 필터·301 제외/legacy 보존, 템플릿 실제 렌더링·canonical 단일성/URL 인코딩/Host 오염 방지, 비공개 헤더·응답 코드 불변.
- 기존 Studio 보안 회귀 테스트에 비로그인 API 401/페이지 로그인 이동과 noindex 동시 검증 추가. 기존 404 회귀 테스트 실행 대상 포함.
- 로컬 JDK 21을 확보했으나 Gradle 플러그인 다운로드 단계에서 Spring Boot 4.0.6 플러그인을 해석하지 못해 테스트 본문은 실행되지 않음. 테스트 통과로 보고하지 않는다.
- 신규 테스트 3종을 기존 Build Check allowlist에 추가. PR CI 결과는 후속 갱신.

## 남은 확인

1. Google 인증 후 GSC 5개 사유별 URL·발견 날짜·색인 상태와 sitemap 처리 상태 확보.
2. GSC URL을 이 HTTP 목록과 대조. 보고서에 발견 날짜가 제공되지 않는 항목은 마지막 크롤링 날짜와 구분.
3. PR CI 통과·검토 후 별도 운영 배포 승인. main 병합은 자동 운영 배포를 유발하므로 이번 요청에서는 병합하지 않는다.
4. 승인 배포 후 운영 재검사, sitemap 재제출/검증 요청은 실제 오류를 대조한 뒤 진행.

## 근거

- 직접 검사 원본: [production-http-20261026.json](production-http-20261026.json)
- Google 공식 sitemap 가이드: https://developers.google.com/search/docs/crawling-indexing/sitemaps/build-sitemap
- Google 공식 canonical 가이드: https://developers.google.com/search/docs/crawling-indexing/consolidate-duplicate-urls
