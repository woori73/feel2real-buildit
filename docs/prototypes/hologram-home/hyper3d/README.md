# Hyper3D 연결을 위한 무료 로컬 GLB 시안

[검증 보고서와 실제 캡처](../evidence/hyper3d/report.html) · [테스트 원본 JSON](../evidence/hyper3d/results.json) · [자산 조사](../evidence/hyper3d/asset-inventory.json)

PR #913의 `e458864e`를 기반으로 한 별도 브랜치입니다. 운영 main `df425588`, 기존 PR, 서버 코드, DB, 기존 캐릭터 파일을 수정하지 않습니다. Hyper3D 호출/생성/결제는 없습니다. 실제 세븐·서수니 GLB는 확보하지 못했으며, 포함된 19,584바이트 GLB는 직접 작성한 기하학 진단 모형입니다. 캐릭터 대체물 또는 Hyper3D 결과물이 아닙니다.

## 실행

이 디렉터리의 상위 `docs/prototypes/hologram-home`에서 실행합니다.

```sh
npm ci
npm run build:3d
python serve.py --port 8765
```

- 기존 시안: `http://127.0.0.1:8765/`
- 3D 검증: `http://127.0.0.1:8765/?preview3d=1` → **테스트 GLB**
- 보고서: `http://127.0.0.1:8765/evidence/hyper3d/report.html`
- 운영 캐릭터를 확인한 후에만 `--media-dir /verified/read-only/agent-media`로 기존 영상 연결. 기본값은 빈 미디어 목록입니다. 서버는 GET 읽기만 제공하고 외부 프록시/DB 연결은 하지 않습니다.
- Playwright는 `/usr/bin/chromium`을 사용합니다. 다른 경로는 `CHROMIUM_PATH`로 지정합니다.

```sh
npm run test:legacy
npm run test:restoration
npm run test:3d
node tests/hyper3d-validation.cjs
```

원래 main 검사 20개는 저장소 루트에서 각각 `node src/test/js/f2r_home_agent.test.js`, `node src/test/js/home_agent_exam_contract.test.js`, `node src/test/js/home_hologram_intake.test.js`로 실행했습니다. DB E2E는 실행하지 않았습니다.

## 구조와 계약

- `adapter.js`: 명시적인 검증 URL에서만 UI를 추가. main의 `data-state`와 `aria-pressed`를 관찰하고 안내 로직을 호출하거나 다시 구현하지 않습니다. 일반 URL에서는 WebGL 번들·canvas가 없습니다.
- `viewer.js` → `viewer.bundle.js`: 고정 버전 Three.js 0.186.1 / esbuild 0.28.2. 로컬 GLB의 SHA-256, 크기, 외부 URI, 필수 확장, 클립, 삼각형, 텍스처 예산을 검사합니다. 번들은 저장소에 포함되어 CDN 없이 작동합니다.
- `models.json`: 세븐·서수니 모두 `null`. `approved:true`만으로 자동 승인되는 운영 기능이 아니며, 검토자가 명시적인 승인 기록·원본 해시·모델 해시를 먼저 마련해야 합니다. 운영 배포에 연결되어 있지 않습니다.
- `diagnostic.glb`: 얼굴·의상 없는 584삼각형, draw call 2개, 애니메이션 4개. `npm run fixture:3d`로 무료 재현 가능합니다. `fixture.json`은 진단용 전용 경로이며 승인 모델 목록에 합치지 않습니다.
- 실제 모델을 향후 연결할 때의 항목: `url`(동일 origin의 로컬 GLB), `sha256`, `sourceSha256`(승인 원본), `approved`, `approvalReference`, `clips:{idle,greeting,thinking,responding}`. sourceSha256는 감사 추적용이며 현재 원본 파일 동일성을 자동 판정하는 기능은 없습니다.
- main `idle/listening`→idle, `greeting`→greeting, `thinking`→thinking, `speaking`→responding. 기존 오디오·인사 종료 시점은 main 영상 코드가 계속 관리합니다. 립싱크는 구현하지 않았습니다.
- 모델을 변형하거나 재질을 재생성하지 않고 카메라 구도에 맞는 외부 그룹 스케일/위치만 적용합니다. 실제 얼굴 동일성은 이 동작만으로 보증되지 않습니다.
- 최대 8MiB, 100k triangles, 8 textures, 4096px은 **검증용 상한**입니다. 모바일 권장 목표는 ≤4MiB, ≤30k triangles, 1K~2K texture로 제안하며 실제 승인 모델로 재측정해야 합니다.
- 모바일 DPR≤1.5 / 목표30fps, PC DPR≤2 / 목표60fps. 화면 밖·숨긴 탭에서 렌더 중단, reduced-motion 정지 자세, 캐릭터 교체 시 이전 fetch 취소와 GPU 자원 해제.
- 로딩8초 초과/오류/불완전 클립/미승인/해시 불일치/WebGL 상실 시 기존 영상·포스터 표시가 다시 노출됩니다. 실제 운영 미디어가 없으면 기존 미디어 미확인 안내가 남습니다.

## 알려진 한계와 다음 통합

1. 현재 캐릭터 이미지·영상의 원본 바이트, Hyper3D 계정 내 작업·모델 목록, 실제 인물 모델·리깅·표정 품질은 미검증입니다. 역사적 영화용 서수니 승인 시트를 운영 홈페이지 기준으로 승격하지 않았습니다.
2. GLB 내부 애니메이션이 있어야 4상태가 작동합니다. Hyper3D의 일반 자동 리깅만으로 원하는 인사·생각·응답·립싱크가 완성된다고 보장할 수 없습니다.
3. 기존 인사와 즉시 복귀를 보존하기 위해 3D 표시 중에도 main 영상 파이프라인은 유지합니다. 실제 모델에서 영상 디코딩+WebGL의 배터리 비용을 측정한 후 idle 영상 일시정지 계약을 별도로 설계해야 합니다.
4. Draco/KTX2, 압축/LOD, 실제 모바일 Safari, 저사양 Android, 느린망, 메모리/발열은 미검증입니다. 필수 GLB 확장은 현재 거절하고 영상으로 복귀합니다.
5. #913 병합 후 그 확정 SHA로 재기준화하고 원본 JS 동일성 및 301개 검사를 재실행합니다. 실제 파일·승인 기록·성능 검증을 마치기 전에는 기본 영상 방식을 유지합니다. main 병합/운영 배포/유료 생성은 별도 승인 사항입니다.

Three.js MIT 라이선스: [THREE-LICENSE.txt](THREE-LICENSE.txt). 서비스 문서·비용·승인 전 작업 제안은 검증 보고서에 출처와 함께 기록했습니다.
