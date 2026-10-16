# #717 홈페이지 개편 운영 배포(#990) 사후 점검 증거 (2026-10-16)

보존 목적: 임시 브랜치 `ci/flyway-history-readonly` 삭제 전에 캡처·결과·실행 기록을 남긴다. main 코드는 바꾸지 않는다.

## 배포
- run #990: https://github.com/woori73/feel2real-buildit/actions/runs/38851617533 — SHA `abc7bebcdb2b167cca0fc9666c75cd6993e27301`, 04:37:57~04:43:38 UTC, `[DEPLOY] SUCCESS`, 롤백 없음
- 배포 후 JAR SHA-256 `44d7f311c9dc065419ffb5e8fc5bf4af2685de99369d1dc1329d705252aaec42` (백업 `app.jar.previous` = `35953b7e…eceb`)
- 중단 시간 약 62초 (2초 폴링: 04:41:23 502 시작 → 04:42:25 200 복구)

## 점검 실행 기록 (모두 GET/클릭만, 쓰기 없음)
| 점검 | run |
|---|---|
| 배포 전 기준선(공개 URL + 호스트 읽기 전용) | https://github.com/woori73/feel2real-buildit/actions/runs/38851576404 |
| 배포 중 health 폴링 | https://github.com/woori73/feel2real-buildit/actions/runs/38851638683 |
| 배포 후 공개 URL + 호스트 읽기 전용 | https://github.com/woori73/feel2real-buildit/actions/runs/38852137098 |
| 배포 후 렌더링 지표(PC/모바일) | https://github.com/woori73/feel2real-buildit/actions/runs/38852789270 |
| 로그인 모달 1차(셀렉터 `#login-modal` 오류, 무효) | https://github.com/woori73/feel2real-buildit/actions/runs/38864727613 |
| 로그인 모달 2차(`[data-login-modal]`, 유효) | https://github.com/woori73/feel2real-buildit/actions/runs/38864883761 |

## 로그인 모달 결과 (운영, 비로그인, 로그인 버튼 클릭만)
- PC 1440 / 모바일 390 모두: 클릭 전 `display:none`·`hidden`, 클릭 후 0.5/1.5/3초 모두 `display:flex`, `visibility:visible`, `opacity:1`, `aria-hidden=false`, 화면 전체 덮음, 포커스 입력창 이동
- 콘솔 오류: `/api/auth/me` 401 1건(비로그인 정상), JavaScript 예외 0건
- 1차 측정이 `#login-modal`(없는 id)을 조회해 "닫힘"처럼 보였던 것은 측정 오류였고 2차에서 정정함(`result.json`은 1차, `result-v2.json`이 유효)
- 캡처의 일부 한글이 □로 보이는 것은 점검용 러너에 한글 폰트가 없어서이며 사이트 문제가 아님

## 파일
`*-v2.png`(유효 캡처), `result-v2.json`(유효 결과). 접미사 없는 파일은 1차(무효) 기록.

## 미검증
- 배포 직후 서버 오류 로그(AWS OIDC 역할 가정이 `main` 시작 실행에서 거부되어 조회 못 함, 별도 작업으로 분리)
- 실제 로그인, 제작 요청, 네이버 로그인 왕복, TUTOR 계정 `/builder`·`/ledger`
