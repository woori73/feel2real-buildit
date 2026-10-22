# ComfyUI/Wan 참조 목록 r8 rev2

r8 참조 목록(`deliver_r8/AI_AGENT_EP01_COMFYUI_REFERENCE_LIST_r8.md`)에 S3·S4를 더한 개정본입니다. 이전 내용(변경 금지, 검증 기준, 대표 컷, 11월 확인 사항)은 그대로이며 준비 자료입니다. Claude가 생성한 이미지·영상은 없고 유료 생성은 별도 승인 전까지 하지 않습니다.

## `CH_SUNI` 참조 등록
| 구분 | 파일 | 상태 | 시트 SHA-256 |
|---|---|---|---|
| 기준 얼굴 시트 | `deliver_r8/references/suni/SUNI_APPROVED_SHEET_original.png` | 승인(기준) | `461926ff…3be6` |
| S1 좌측면, S2 의상 A 전신 | `deliver_r8/references/suni/suni_S1_*`, `suni_S2_*` | 승인(r8) | `a35ceeed…1e9f` |
| **S3 의상 B 정면·측면·후면** | `references/suni/suni_S3_B_{front,side,back}_crop.png` | **승인(rev2)** | `89cc41fa…dd0b` |
| **S4 의상 C 정면·측면·후면** | `references/suni/suni_S4_C_{front,side,back}_crop.png` | **승인(rev2)** | 동일 |
| S5·S6 | — | 후보, 미제작 | — |
- 전체 SHA-256은 `AI_AGENT_EP01_r8_rev2_FILE_LIST.md`
- 얼굴 참조는 기존 승인 시트 얼굴 크롭 우선

## 컷별 연결 (정본 `AI_AGENT_EP01_CHARACTER_CUT_MAP_r8_rev2.json`)
의상 A 컷은 S2, **의상 B 컷(49~61)은 S3, 의상 C 컷(62~68)은 S4**를 `costume_fullbody_*`에 연결. 컷 22 이후 상처 참조(S5·S6)는 미제작.

## 프롬프트 블록
- B: `wearing a short navy zip jacket open over a light grey top, black cargo pants, black lace-up combat boots, bright daytime corridor, soft cool white light` (전신·와이드 컷에서만 `small holster on her right hip`, 상반신은 제외)
- B 컷 49·50 시계: `only the end of a pocket watch chain showing outside the chest pocket` (시계 앞면·문양 금지)
- C: `wearing a cream long-sleeve top with two black shoulder straps, black cargo pants, black lace-up combat boots, no jacket`
- 네거티브(B 상반신): `holster, gun on hip, pocket watch worn as a necklace` / (C): `jacket, holster, bandage visible`(붕대 노출 결정 전)
- 의상 A 다리 파우치: 확인 전에는 넣지 않음

## 검증 기준 추가
| 번호 | 기준 |
|---|---|
| V-S11 | 의상 B·C 구성이 S3·S4와 일치하고 구간(49 이전 B 금지, 62 이후 B 재킷 금지)을 지킴 |
| V-S12 | 컷 49·50 회중시계가 사슬 끝만 보이고 앞면·문양이 보이지 않음 |
이전 V-S1~S10, V-L1~L4는 동일. 자동 지표 임계값은 11월 환경에서 합의, 사용자 검수가 최종.

## 남은 준비
S5·S6 제작·검수 → 다리 파우치·시계·S4 어깨 끈 확인 → 리더 손 좌우·장비 검수 → 컷 62 이후 붕대 결정 → 11월 GPU 환경 확인 → 대표 컷 10개 테스트 → 정식 원본 확보 후 원작 대조(0/68컷)
