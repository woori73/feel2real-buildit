# 홈 내신위키 링크 복구 검증 (로컬 부팅 + 실제 브라우저)

- 환경: 로컬 부팅(local profile, 일회용 MySQL 8.0), Chromium 1280 / 880~1024 / 390(모바일).
- 결과: PC 상단 메뉴·모바일 메뉴·서비스 칩·`/home` 상단 메뉴에 `내신위키 → /problem-generator`, `모의고사위키 → /wiki`가 함께 보임. 비회원 클릭 → 공개 소개(200, 네이버 로그인 링크), STUDENT/TUTOR/ADMIN → 기존 내신위키(`/problem-generator/wiki/index.html`)로 302. 다른 서비스 경로(/wiki, /exam-analysis, /chart, /testprep, /builder, /ledger, /designcraft, /admissions/guide, /home) 200 유지.
- 파일: `pc-1280-nav.png`, `mobile-390-menu.png`, `pc-anon-intro.png`, `home-legacy-nav.png`
