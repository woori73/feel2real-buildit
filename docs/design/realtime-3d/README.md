# Feel2Real 실시간 3D 공간 시범 구현

2026-10-25 / ChatGPT Work 직접 제작. 승인 전 병합·운영 배포 금지.

## 변경과 재사용

- 기준 main: `745b5cf127f316120785c581b7c7a1b5a18d27a7`(#900).
- #893·#894·#899의 비서 선택·영상·고정 인사 음성·대화 입력·초안·승인·서비스 연결 코드를 그대로 재사용한다. 해당 JS 파일을 수정하지 않는다.
- #850은 미병합 Draft이며 기존 화면과 충돌한다. 전체를 합치지 않고 가시성 기반 지연 시작, 모션 감소·저사양·데이터 절약 시 2D 유지 원칙을 이어받았다.
- 홈/대시보드 템플릿에는 공통 CSS·지연 로더 두 줄씩만 추가한다. Java·API·DB·배포 설정을 바꾸지 않는다.

## 실제 3D

Three.js 0.180.0, PerspectiveCamera, HemisphereLight·PointLight, GLTFLoader, 승인 영상의 VideoTexture를 사용한다. 얼굴·음성 파일은 재생성하거나 편집하지 않으며 별도 디코더도 만들지 않는다. 대시보드의 두 영상 교체와 인사 오디오를 기존 코드가 계속 소유한다.

| GLB | 내용 | 크기 |
|---|---|---:|
| 01_central_stage.glb | 중앙 투사대·바닥 눈금·원형 조명 | 134,008 B |
| 02_hologram_rings.glb | 서로 다른 속도로 회전하는 3개 링 | 89,600 B |
| 03_research_facility.glb | 연구시설 포털·바닥 격자·콘솔 | 60,892 B |
| 04_service_panels.glb | 독립 선택 가능한 6개 패널 | 43,452 B |
| f2r-environment.glb | 공통 기하 버퍼를 재사용한 통합 환경 | 326,144 B |

3D 모델은 비용 없이 직접 만든 원본이다. 세븐 얼굴 메시가 없다. 텍스처 0개이므로 텍스처 압축 전송도 필요 없다. 정적 구조를 재질별로 병합하고 링·패널만 독립 메시로 유지한다.

버튼은 기존 DOM 노드를 3D 패널의 투영 좌표에 배치한다. 기존 이벤트·키보드 포커스·접근 가능한 이름·서비스 연결을 보존한다. 배경 패널 메시에는 Raycaster 클릭 판정도 제공한다. 마우스 이동·터치 드래그·패널 선택에 카메라가 부드럽게 반응한다. 2D 복귀 시 원래 부모와 순서로 버튼을 돌려놓는다.

## 모드와 성능 정책

- WebGL2: 실제 메시·PBR 조명·영상 텍스처, 최대 30fps, DPR PC 1.25 / 작은 무대 1.
- WebGL2를 만들 수 없으면 즉시 기존 2D 화면으로 복귀한다. 자동 재시도하지 않는다. `data-space-preview="svg"`를 명시한 격리 검수 화면에서만 SVG 호환 렌더를 허용한다(최대 8fps). 이 모드의 영상은 DOM video이며 PBR·VideoTexture 검증이 아니다.
- 메모리 ≤2GB, CPU ≤2코어, Save-Data/2G, 모션 감소, 사용자가 저장한 2D 선택: 엔진을 자동으로 다운로드하지 않고 기존 2D 화면을 유지한다. 수동 3D 전환은 가능하다.
- 무대가 화면 밖에 있거나 탭이 숨겨지면 RAF와 재생 중 영상을 멈춘다. 복귀 시 직접 일시정지한 영상만 같은 소스인지 확인하고 재개한다. 해제 시 텍스처·지오메트리·재질·리스너·렌더러를 정리한다.
- 3D 엔진 번들 594,849 B / gzip 154,290 B. 초기 로더 약 4KB. 모델도 보일 때만 다운로드한다.

## 빌드

```sh
npm ci --prefix scripts/spatial-ui
node scripts/spatial-ui/build.mjs
node --test src/test/js/f2r_spatial.test.js src/test/js/f2r_home_agent.test.js src/test/js/f2r_agent_dashboard.test.js src/test/js/f2r_agent_flows.test.js src/test/js/home_agent_exam_contract.test.js
```

빌드 결과와 MIT 라이선스를 함께 커밋했다. 런타임 CDN 의존성 없이 같은 출처에서 제공한다. `node_modules`는 커밋하지 않는다.

## 검수 범위와 남은 항목

- Node: 기존 비서·안내 계약과 새 정책·실제 GLB 구조/크기 35/35 통과. CI 선택 목록 25개 파일 전체도 205/205 통과했다.
- 격리 브라우저 시범: CSS viewport 1440×960 / 390×844. 대화·초안 API는 로컬 목 서버이며 운영 DB와 연결하지 않는다.
- 홈 서비스 버튼 → 기존 안내, 대시보드 학생 등록 → 이름 입력 → 학년 선택, 2D 복귀 후 원래 버튼 6개와 영상 유지 확인.
- 홈·대시보드 인사 버튼: 기존 speak.mp4를 muted=false·loop=false로 재생, 종료 후 idle.mp4·muted·loop 복귀. 일반 응답에 인사 립싱크를 붙이지 않는다.
- 서수니 선택 기능 유지. 격리 자료에는 공개 승인 세븐 자료만 있어서 서수니 선택 시 원래 SUNI 표식으로 표시한다. 운영 비공개 서수니 영상을 새로 만들어 대체하지 않는다.
- 원격 검수 브라우저는 WebGL이 Disabled 상태다. 스크린샷은 SVG 호환 렌더다. 원격 호환 렌더의 관측 평균은 약 1.1fps, JS render 호출 비용 약 16~18ms였다. 실제 GPU 프레임 속도나 영상+WebGL 동시 재생 성능이 통과했다고 주장하지 않는다.
- 운영 로그인·실제 업무 저장·Android/iOS 터치 실기기·키보드·WebGL context-loss 복구·실 GPU 시각 검수는 미완료다. 실제 기기 검수와 감독 승인 전 운영 배포하지 않는다.
- Studio 원본 묶음 `AGENT_MEDIA_SEVEN_T2f_v2_APPROVED.zip`(SHA-256 `65fe99b7578ce51a08a156752a1ba72bebf4fac21533abbaefde4d2dc1be4776`)을 다운로드해 시범의 idle·speak·poster 3개 모두 바이트 일치를 확인했다.
- Studio에 `F2R-REALTIME-3D-MODELS`, `F2R-REALTIME-3D-DEMO`, `F2R-REALTIME-3D-REVIEW` 3건을 `AI_PROPOSAL / REVIEW / work-v1-20261025`로 등록하고 재조회했다. 모델 ZIP·실행 시범 ZIP·독립 비교 HTML을 연결했다. 기존 승인 포인터와 원본은 변경하지 않았다.
- 운영 대시보드도 읽기 전용으로 확인했다. 세븐은 정본이며 서수니는 기존부터 자료 미연결 상태다. 새 얼굴·영상으로 대체하거나 운영 자료를 설치하지 않았다.

## 무료 혜택 확인

사용자가 제공한 Hyper3D Rodin 화면에 5크레딧이 표시된다. Chroma 공식 Hyper3D 페이지에는 Creator 1개월($30 가치) 혜택이 표시된다. 계정에서 혜택 적용/만료 여부는 인증 전이라 확인하지 못했다. 이번 작업은 Rodin 크레딧·유료 구독·추가 결제를 사용하지 않았다.

공식 근거: https://chromaawards.com/prizes/creative-tools-hyper3d-awards / https://hyper3d.ai/pricing?lang=en / https://threejs.org/docs/pages/GLTFExporter.html

## 2차 검수 및 공통 디자인

2차 검수에서도 실제 GPU 브라우저에 접근하지 못했다. 실 WebGL2·VideoTexture·PBR·실기기 FPS·GPU 사용률은 미검증이며 Draft를 유지한다. 움직이는 호환 시범과 실제 GPU 검증을 구분한다. [공통 AI Agent 공간 디자인](./AI_AGENT_DESIGN.md)을 따른다.

[2차 검수 결과·영상 위치](./SECOND_REVIEW.md)
