# 홈 밝은 개편 화면 확인 (2026-11-01)

승인 시안(`Main.dc.html` 데스크톱, `Mobile.dc.html` 390px, `Menu.dc.html` 전체 메뉴)을 `templates/public/landing.html` + `static/css/home_light.css`로 옮긴 뒤 찍은 화면이다.

## 찍은 방법

- 실제 Thymeleaf 템플릿을 `HomeExamOffer.CURRENT`, 계좌 공개(`HomeBankTransfer.CURRENT`), 기본 홍보 영상·트레일러·게임 경로로 렌더했다.
- 정적 파일은 저장소의 `src/main/resources/static`을 그대로 쓰고, Pretendard v1.3.9를 적용했다. 외부 요청은 막았다.
- 빌드잇 차트 TOP5는 화면 확인용 예시 3건(`예시 선생님 A~C`)이다. 실제 운영 데이터가 아니다.
- 배너 자동 넘김은 멈춘 상태(첫 장)에서 찍었다.

## 파일

| 폭 × 높이 | 첫 화면 | 전체 메뉴 열림 |
|---|---|---|
| 360 × 800 | `360-top.jpg` | `360-menu.jpg` |
| 390 × 844 | `390-top.jpg` | `390-menu.jpg` |
| 430 × 932 | `430-top.jpg` | `430-menu.jpg` |
| 768 × 1024 | `768-top.jpg` | `768-menu.jpg` |
| 1100 × 900 | `1100-top.jpg` | `1100-menu.jpg` |
| 1440 × 1050 | `1440-top.jpg` | `1440-menu.jpg` |

전체 페이지(축소): `390-full.jpg`, `1440-full.jpg`.

## 확인한 것

- 6개 폭 모두 가로 넘침 0, 상단 로고와 버튼이 겹치지 않음, 첫 화면 안에 「맞춤문제 1장 무료 신청」 버튼이 보임.
- 배너 4장 높이가 같아 넘길 때 아래 내용이 움직이지 않음.
- 전체 메뉴: 열면 닫기 버튼으로 포커스, Esc·닫기·바깥 누르면 닫히고 메뉴 버튼으로 포커스 복귀, 열린 동안 Tab이 메뉴 안에서만 돎.
