# BuildIt 홈페이지 v3 — 디자인 시안 보존

> **이 폴더는 참조용 문서·시안이다. 운영 코드가 아니다.** `src/main/resources`로 그대로 복사해 쓰지 않는다.
> 구현은 Muse가 `HANDOFF.md`를 기준으로 한다. Claude는 운영 홈페이지 코드를 수정하지 않는다.

## 상태

- 디자인 방향: **v3 채택(사용자 확정)**
- 핸드오프: **확정 v1.0** — [HANDOFF.md](HANDOFF.md)
- 실제 홈페이지 구현: **미착수** (사용자 지시 후 Muse)
- 선행 PR: #651(샘플 카드 제거) — CI 통과·병합 준비, **미병합**

## 구성

| 경로 | 내용 |
|---|---|
| [HANDOFF.md](HANDOFF.md) | 구현 핸드오프(토큰·컴포넌트·레이아웃·유지 동작·테스트 차이·검증 항목·확정 결정) |
| `source/index.html`, `tokens.css`, `home.css`, `home.js` | **HTML v3 시안 원본** (정적, Pretendard CDN 사용 — 운영 반영 전 자체 호스팅 검토, HANDOFF §13) |
| `source/verify.playwright.js` | 시안 렌더·가로넘침·axe 검증 스크립트(참조용) |
| `screens/` | PC 1440 / 모바일 390 캡처, 1차 시안 대비 비교 이미지 (WebP) |

## Figma (편집 가능 시안)

- 파일: <https://www.figma.com/design/vfoT1AqngzCNxxyFCyspG2>
- PC 1440: `?node-id=5-2` · Mobile 390: `?node-id=9-20`
- 서비스 상태 보드: PC `?node-id=7-287` · Mobile `?node-id=10-303`
- Button 컴포넌트 `?node-id=4-12` · Badge 컴포넌트 `?node-id=4-22`
- 변수 컬렉션 `BuildIt/Color`(23), 텍스트 스타일 `BuildIt/*`(15)
- 제약: Figma 무료(Starter) 플랜 MCP 호출 한도에 도달해 **추가 수정·구조 재점검 불가**. 파일 열람·편집은 Figma 좌석 권한에 따른다.
- **동작·반응형 기준은 HTML v3**이다. Figma와의 차이는 HANDOFF §8.

## 검증 현황 요약 (상세: HANDOFF §2)

| 항목 | 결과 |
|---|---|
| HTML v3 390/1440 렌더·가로넘침·axe | 통과 (정적 파일 기준) |
| Lighthouse(정적, 샌드박스) | 모바일 성능 100·접근성 100 / 데스크톱 성능 88·접근성 100 |
| **모바일 Chart·콘텐츠(Wiki/Report/Shorts)·FAQ** | **검수 미완료** — Muse 브라우저 검증 필수(HANDOFF §9-1) |
| 360·430·768·1024 | **미검증** — Muse 작업 범위(HANDOFF §14) |
| Spring 통합·로그인·권한·실데이터 | **미연결** |
| 시안의 링크 오류 | `/report-demo`·`/shorts-demo`(존재하지 않음), `/login`(리다이렉트), 히어로 `form action` 등 — **구현 전 정정 필수**(HANDOFF §3) |

## 확정된 결정 (HANDOFF §10)

1. 노출 순서: AI 홈페이지 제작 → Chart → Easy → 콘텐츠 도구 → 준비 중 서비스 (계약 테스트·철학 문서와의 차이는 HANDOFF §6-3에 기록, 테스트 변경은 별도 검토)
2. 상단 내비: v3 디자인 + 기존 모의고사·F2R·CHART·WIKI 경로 유지
3. 소개영상·슬로건 유지·재배치
4. Easy: 관리자 전용 베타, `/easy` 직접 연결 금지, 도입 문의는 기존 문의 모달(`openContactModal()`) 연결(불가 시 비활성)
5. Pretendard 우선 + 대체 폰트 + 자체 호스팅 검토(가능, OFL-1.1)
6. 인증 로직(`landing_buildit.js`, 로그인 모달) 유지
7. Chart 실제 공개 TOP5 유지, Easy 실제 회계 데이터 노출 금지
8. 360·390·430·768·1024·1440 반응형 검증
