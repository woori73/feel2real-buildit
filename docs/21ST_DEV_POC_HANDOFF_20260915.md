# 21ST.DEV POC HANDOFF (2026-09-15)

## 목적

21st.dev를 BuildIt/홈페이지 디자인에 적용하기 위한 PoC. A/B/C 격리 실험 구조로 production 영향 없이 비교한다.

## 어제까지 완료된 것 (2026-09-14, commit 92084f15)

- 브랜치 `experiment/design-lab-21st-react`, 30 files +2673, production 파일 untouched.
- TYPE A: Vanilla HTML/CSS/JS (`templates/design-lab/home-a.html` 231줄, `home-a.css` 714줄).
- TYPE B: React 18 + Vite 5 + Tailwind 3.4 + shadcn/ui 패턴 (`frontend/design-lab/` + 빌드 산출물 `static/design-lab-b/`).
- TYPE C: 하이브리드 결정용 placeholder (`home-c.html`, `home-c.css` 228줄).
- Design Lab index + 컨트롤러 (`DesignLabController`, routes `/design-lab`, `/design-lab/home-a|b|c`).

## 현재 변경 파일 (commit 92084f15 내역)

- `frontend/design-lab/`: package.json(@buildit/design-lab-b), vite/postcss/tailwind/tsconfig, index.html, src/main.tsx·App.tsx·index.css·lib/utils.ts, components/Header·VideoHero·SchoolCard·CTASection, components/ui/button·card·badge (shadcn 패턴)
- `src/main/java/.../web/designlab/DesignLabController.java` (39줄)
- `src/main/resources/templates/design-lab/`: index·home-a·home-b·home-c.html
- `src/main/resources/static/css/design-lab/`: index·home-a·home-c.css
- `src/main/resources/static/design-lab-b/`: 빌드 산출물 (index.html, assets js/css, manifest)

## 21st.dev 적용 상태

- 21st CLI: NOT VERIFIED (CLI 사용 로그·설정 없음)
- 21st MCP: NOT VERIFIED (repo에 MCP 서버 설정 없음, 21st 항목 없음. index 카드 문구에만 "MCP/Registry 연동" 계획 언급)
- Component 가져오기: 21st.dev registry 직접 import 흔적 없음. ui/ 3종은 shadcn/ui 패턴 수작업 + radix/clsx/tailwind-merge 의존. TYPE B 라벨에 "21st.dev" 명칭만 사용.
- REACT: PoC 완료 (18.3.1, 빌드 산출물 포함)
- TAILWIND: PoC 완료 (3.4.14, Preflight 격리 설계)
- SHADCN: 패턴 완료 (button/card/badge + cn utils)
- HOMEPAGE POC: 실제 홈페이지 미적용 (`/design-lab/*` 격리, production untouched)

## 현재 중단 지점

- TYPE C 미정 (A/B 비교 후 하이브리드 결정이 다음 판단).
- 메인 작업트리 `frontend/design-lab/`는 스켈레톤 잔재 (빈 public/·src/hooks/, package-lock, node_modules). 실제 코드는 본 브랜치 커밋에 보존됨. node_modules·lock·빌드잔재는 커밋 대상 아님.

## 다음 작업

1. 본 브랜치 체크아웃 후 `frontend/design-lab`에서 `npm install` → `vite dev`로 TYPE B 동작 확인.
2. `/design-lab`에서 A/B 렌더 비교 후 TYPE C 방향 결정.
3. (그 이후 별도 승인 하에) 홈페이지 반영 여부 판단. 현 상태 production 배포 금지.

## 금지

현재 미완성 상태에서 production 배포하지 않는다. main merge 금지.
