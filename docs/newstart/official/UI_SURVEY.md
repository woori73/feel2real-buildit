# 공식 UI 실측 + NewStart 이식 기록 (2026-09-20)

## OFFICIAL UI INSPECTED

- https://www.newstartfund.or.kr/Index.do — DOM(html) + main.css + mq.css + 실렌더 스크린샷(.playwright-mcp/page-2026-09-20T07-59-40-686Z.png)
- 구조: sec01(대제목+설명) → sec02(대형 카드 3 + 공지 스와이퍼) → sec03(퀵메뉴 7) → sec04(콜센터)

## CARD STRUCTURE (sec02)

- 3열 float, 높이 250px(desktop), 카드1 18.4% / 카드2 18.4% / 카드3(newleap) 18.4% / 공지 38.8%
- 카드1 navy #001f3b "새출발기금/신청하기", 카드2 red #ac0001 "진행상태/확인하기", 카드3 white "나에게 알맞은 프로그램/확인하기"
- 제목 h3 3rem 굵게 + 부제 p 1.9rem, 화살표 a::after 좌하단(swiper 제외), hover 시 화살표 이동(transition .3s)
- radius: 신규 카드 23px 인라인, 구 카드 직각
- mobile(≤449px): 100% 세로 스택, 화살표 우측 중앙

## QUICK MENU STRUCTURE (sec03)

- 7칸 float 각 12.55%, 높이 100px, 흰 배경, 제목 h3 1.8rem 좌측 세로중앙(table-cell)
- 우측 화살표 스프라이트(quickBtn.png, 위치별 background-position), 첫 칸 red #ac0001 흰 글씨
- hover: box-shadow 3px 3px 5px 1px rgb(0 0 0/10%)
- mobile: 2열 49% 그리드

## ICONS USED / OFFICIAL ASSETS FOUND

- fa-arrow-right-long (FontAwesome), bannerBtn.png / bannerBtnNewleap.png / quickBtn.png (스프라이트)
- 배경: mainBg_wide.jpg, object_people.png, kin_logo.png, notice_banner*.gif/jpg/png, pop_logo.png, vc_btn.png
- 로고: /resource/img/common/logo.png (캠코·새출발기금)

## ASSET REUSE METHOD

- CSS-only 재현. 공식 이미지·로고·스프라이트 hotlink/복사 없음 (라이선스·브랜드 오인 방지).
- 화살표는 텍스트 → (이모지 아님, FontAwesome 대체)로 ::after 구현.
- 색상 역할만 참조: navy #001f3b / red #ac0001 / 흰 카드 / 첫 퀵메뉴 강조.
- NewStart 기존 임의 emoji/icon 없음(grep 확인) — 교체 대상 없음.

## NEWSTART TITLE/CARD/QUICK MAPPING

- [새출발기금/신청하기] → [나도 대상일까?/지원대상 확인] → #ns-official (내부)
- [진행상태/확인하기] → [내 상황 물어보기/NewStart AI] → #ns-ai (기존 AI)
- [나에게 알맞은 프로그램/확인하기] → [공식 신청·조회/새출발기금 바로가기] → 공식 Index (외부)
- 퀵 7: 대상 안내·감면·상환·신청 절차·필요 서류(내부 #ns-official) + FAQ·상담센터·협약 금융기관(공식 외부)
- hero: "내 새출발, 어디부터 확인할까" + eyebrow "공식 사이트가 아닌 정보·AI 지원 서비스" (브랜드 오인 방지)
- AI h1 → h2 (단일 h1 유지), AI form/thread/입장·상태·로그인·메뉴 전부 유지

## 검증 한계

- 로컬 Spring 기동 불가(Docker daemon 없음)로 실렌더 비교는 공식 스크린샷 vs 정적 검증으로 대체.
- 정적 검증: 태그 균형·h1 1개·CSS brace·클래스 커버리지·공식 URL 14/14·fragment/JS id 전수 유지 — 통과.
