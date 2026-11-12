# cafe-audit-2026-10-03 — 네이버 카페 구조개편 사전조사 산출물

- 카페: 새출발기금 부실차주 라이브러리 `https://cafe.naver.com/newhopero` (CAFE ID 31312978)
- 실측일: 2026-10-03 (학원 PC) · **카페에 가한 실제 변경 0건**
- 상위 문서(`docs/newstart/`): `CAFE_CURRENT_STRUCTURE_2026-10-03.md`(구조 감사) · `CAFE_PHASE1_FINAL_PLAN.md`(실행 설계) · `CAFE_2026_10_VISION_MESSAGE.md`(운영 메시지) · `CAFE_HANDOFF_2026-10-03.md`(인계)

## 파일 목록

| 파일 | 내용 | 용도 |
|---|---|---|
| `boards_table.txt` | 15개 게시판 menuId·글 수·매니저 비율·문의 비율·무활동일·제목 키워드·Jaccard(총 5,323건) | 그룹 매핑표 근거 |
| `index_candidates.txt` | 증상별(연체·추심 제목 일치 327건 등 8개 그룹) 대표 게시글 articleId·조회·댓글 | [내 문제 찾기] 원고 근거 |
| `ref_report.txt` | 3287/4446/3198 inbound 링크 전수(각 0·0·10건), 카카오 방 코드 분포 | 3287 삭제 게이트 근거 |
| `notices3_report.txt` | 전체공지 3건(3287·4446·3198) 평문 추출 본문 | 역할 중복 판정 |
| `art3198_body.txt` | 3198 본문 + 스마트스토어 링크·이미지 5장·주의사항 전제 | 개편 원고 근거 |
| `ns_cta.txt` | NewStart 랜딩 CTA 앵커 실측(href·class) | CTA 문구 통일 근거 |
| `menu_data_scrubbed.json` | 게시글 5,323건 인덱스(id·제목 앞30자·writeDate·댓글·조회·mgr) — **작성자 nick 필드 제거** | 목록 재집계 |
| `raw_backup/notices_backup_full.json` | 전체공지 3건 **contentHtml 원문 전체** | 롤백(3198/3287/4446) |
| `raw_backup/art3198_full.json` | 3198 gw/v4 전체 응답 | 3198 원상복구 |
| `raw_backup/notices3.json` | 3건 원문(축소본) | 2중 백업 |
| `raw_backup/pc_gate_html_backup_20261003.txt` | PC 대문 HTML 원문 732자(img 2개·a 0개) | 대문 변경 롤백 |
| `shots/mobile_home_390.png` | 모바일 HOME 390px — **전체공지 0건 실증** | BEFORE |
| `shots/mobile_notice_tab_390.png` | 모바일 `?tab=notice` 3건 | 공지 슬롯 실측 |
| `shots/art3198_mobile_390_stitched.png` | 3198 모바일 본문(390×1659, 3프레임 합성) | 공지 개편 BEFORE |
| `shots/art3198_panel_top.png` | 3198 상단 패널 | 상동 |
| `shots/pc_menu_crop.png` | PC 전체 메뉴 15개(1200×1800 크롭) | 메뉴 BEFORE |
| `shots/newstart_home_390.png` | NewStart HOME 390px | 디자인 기준 |
| `shots/newstart_login_390.png` | NewStart 로그인 390px | 전환 동선 |
| `shots/newstart_mobile_metrics.json` | 390px computed style 실측 | 토큰 근거 |
| `shots/skin05_naver_preset_gate.png` | 네이버 프리셋 스킨 640×484 = 모바일 대문 배경 | 배너=클릭 불가 이미지 실증 |
| `scripts/analyze_*.py` | 집계 재현 스크립트 | 재생성용 |

## 의도적으로 제외한 것

- **게시글 원본 코퍼스(4,129건 본문 전체)** — Git에 복제하지 않음(로컬 보존). 개편 설계는 집계·제목·대표글 ID로 충분하며, 원문은 로컬 스크립트로 재집계 가능.
- **`menu_data.json` 원본의 `nick` 필드(작성자 닉네임 1,193건)** — 회원 식별 정보라 scrub본에서 제거. 원본은 학원 PC에만 있음.
- **브라우저 프로필(`.chrome-shot-profile2/`), 쿠키·세션·토큰·인증 헤더 일체** — 절대 커밋하지 않음.
- 대형 원본 캡처(`pc_cafe_menu.png` 1.2MB), 중간 프레임(`a3198_p1..p3.png`), 헤더리스 로그(`cafe_shot.log`), 스크래치 HTML(`ns_home.html`, `ns_login.html`) — 핵심만 선별.
