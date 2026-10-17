# ADMIN 메뉴 개편 · Analytics 세분화 설계

## 1. 메뉴 대응표
| 구분 | 이전 | 변경 |
|---|---|---|
| 서비스 바로가기 | `회계 Easy`(/easy) 1개 | **EASY 장부** `/easy`(ADMIN), **위메이드** `/wemade`(공개), **충기** `/choonggi/admin`(ADMIN·CREATOR), **홈페이지 빌더** `/builder`(TUTOR·ADMIN), **내신위키** `/problem-generator`(로그인 시 `/problem-generator/wiki/` 로 이동), **TESTPREP** `/testprep` |
| AcademyCraft | — | **연결하지 않음**: 단독 진입 경로가 없다(공개 소개는 "곧 준비중", LMS는 `/lms/{handle}` 학원별 경로). 가짜 링크를 만들지 않는다 |
| 불필요 메뉴 3개 제거 | — | **보류**: 첨부 스크린샷(빨간 표시)을 확인할 수 없어 대상 확정 전. 후보는 아래 §2 |
| 레이아웃 | 가로 스크롤(스크롤바 숨김)이라 뒤쪽 메뉴가 가려짐 | 데스크톱(≥769px)은 줄바꿈, 모바일은 기존 가로 스크롤 |

기능·데이터·라우트는 삭제하지 않는다(메뉴 노출만 조정). ADMIN 전용 기능 보존.

## 2. 제거 후보 (현재 상단 메뉴 전체)
통합 대시보드(/admin/hub)·학생 목록·회원 전체·통합 Analytics·Academy 노출 관리·BUILD IT CHART·시험 분석 자료 관리(/admin/analyzes)·모의고사 원본(/admin/wiki-originals)·분석 자료(/admin/wiki-analysis)·부실차주 Wiki·지피지기 Agent·커뮤니티 검색·랜딩 페이지 관리·새출발·새출발 업데이트. 상단 "로그인/회원가입" 버튼은 메뉴가 아니라 계정 영역이며 로그인 상태에서는 숨겨진다. → 제거할 3개 이름을 알려 주면 한 줄씩 지운다.

## 3. 기존 수집 방식 (재사용)
- `SiteVisitFilter`가 HTML 200 응답마다 `site_page_visits` 1행(경로·역할·visitor_key·traffic_kind·날짜)을 저장. **경로가 저장돼 있어 서비스·페이지 분류는 과거 데이터까지 소급** 가능 → DB 변경 없음.
- 기존 `service_area` 분류(HOME/CHART/WEB/ACADEMY/WIKI/NEWSTART/OTHER)는 그대로 둔다("서비스 영역별(기존 분류)"). 새 분류와 섞어 합산하지 않는다.

## 4. 집계 정의
- **PV**: HTML 페이지 200 응답 1회. **UV**: 기간 내 `visitor_key` distinct(회원 U:ID, 비회원 V:쿠키UUID 90일, 과거 S:세션). IP·지문 미사용.
- **Bot**: UA에 bot·crawler·spider·Googlebot·Bingbot·GPTBot 등 → `traffic_kind=BOT`, 모든 UV/PV에서 제외, "제외된 Bot 요청"으로만 집계.
- **기간**: 오늘·7일·30일(오늘 포함, Asia/Seoul)·누적(수집 시작일부터). 관리자 화면(/admin/**)은 서비스 방문에서 제외.
- **합산 금지**: 서비스별·페이지별 UV 합 ≠ 전체 UV(한 방문자가 여러 곳 방문). 화면에 명시.
- **서비스 분류**(경로 접두): 메인(/·/home), BuildIt(/builder·/tutor/academy·/academies·/academy-posts·/lessons·/subdomain-home), EASY(/easy·/ledger·/tutor/easy), 위메이드(/wemade), 충기(/choonggi), 내신위키(/problem-generator), TESTPREP(/testprep), AcademyCraft(/lms), 기타 등록 서비스(/designcraft·/exam-analysis·/chart·/wiki·/newstart·/admissions), 미분류.
- **페이지 그룹**: 메인 홈페이지 / 서비스 소개·첫 화면(서비스 루트 경로) / 시험 제작(/problem-generator·/exam-analysis) / 상담신청(/admissions·/newstart/consult*) / 개별 서비스 페이지 / 기타. **가격·상품 안내는 전용 페이지가 없어** 집계하지 않고 화면에 명시(홈 '내신 예상문제 20,000원' 영역은 섹션 지표 대상).

## 5. API
`GET /api/admin/analytics/breakdown[?service=EASY]` (ADMIN 전용): 서비스·페이지 그룹 표(오늘/7일/30일/누적 UV·PV), 많이 본 페이지 Top15(30일), 30일 일별 추세(서비스 선택), 전환 지표(실제 수집된 것만, 미수집은 `collected=false`·숫자 없음), 집계 기준 문구. 기존 `/summary`는 변경 없음.

## 6. 이벤트 수집 (V1087 `site_events`, 구현됨 — 운영 적용은 별도 승인)
섹션 노출·버튼 클릭·서비스 이동·상담/제작 시작은 서버가 볼 수 없는 브라우저 행동이라 `site_events`(신규 테이블)로 따로 수집한다. **PV와 다른 정의의 별도 지표이며 합산하지 않는다.**
- 테이블·컬럼·보관·개인정보 원칙: `docs/DOMAIN.md` §4.48-1. API: `docs/API.md`(`POST /api/analytics/events`, `GET /api/admin/analytics/events`).
- 노출 기준: IntersectionObserver로 **50% 이상(또는 화면 높이 50% 이상)이 1초 이상** 보일 때 페이지 방문당 1번. 스쳐 지나간 영역·탭이 숨겨진 시간은 세지 않는다.
- 중복 방지: 방문마다 새 `view_id`(무작위), 서버 유니크 키 + `INSERT IGNORE`. 같은 방문자의 새 방문은 노출 횟수 +1, UV는 그대로.
- 방문자 식별은 기존 PV와 동일(`AnalyticsVisitorIdentity`). Bot은 BOT, ADMIN은 internal로 저장하고 집계에서 제외.
- 서버는 허용 목록(`AnalyticsEventCatalog`)의 이벤트·페이지·대상만 받고 URL·사용자 식별값은 신뢰하지 않는다. 속도 제한·20개/요청·4KB 본문 제한.
- 보관 395일, 매일 새벽 자동 삭제. 상담신청 완료는 `academy_inquiries` 건수로 센다.
- 모바일 드로어·상단 메뉴 링크(내신위키 포함)는 추적하지 않는다(서비스 카드·본문 버튼만).

## 7. 뮤즈와의 분리
뮤즈: `/dashboard`·`/admin/hub`·Canva·EASY/상담 위젯. 이 작업: `_admin_topnav`(서비스 링크 추가)·`admin_topnav.css`(줄바꿈 1블록)·`/admin/analytics`·`analytics` 패키지. 공통 nav/CSS 변경은 최신 main 기준으로 병합했다.
