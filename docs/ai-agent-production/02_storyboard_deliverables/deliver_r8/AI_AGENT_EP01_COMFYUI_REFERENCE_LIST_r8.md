# ComfyUI/Wan 참조 목록 r8

r7 rev1 인계(`deliver_r7_rev1/AI_AGENT_EP01_COMFYUI_HANDOFF_r7_rev1.md`)에 S1·S2 승인 참조를 더한 문서입니다. 이전 내용(변경 금지 조건, 검증 기준, 대표 컷, 11월 확인 사항)은 그대로이며, 11월 GPU 환경에서 실제 모델·워크플로·해상도를 확인하기 전까지 준비 자료입니다. Claude가 생성한 이미지·영상은 없고 유료 생성은 별도 승인 전까지 하지 않습니다.

## 1. `CH_SUNI` 참조 등록
| 구분 | 파일 | 상태 | SHA-256(시트) |
|---|---|---|---|
| 기준 얼굴 시트 | `references/suni/SUNI_APPROVED_SHEET_original.png` | 승인(기준) | `461926ff…3be6` |
| 얼굴 정면·3/4·우측면 | 기존 크롭(`deliver_r7/references/suni/`) | 승인(기준) | 위와 동일 |
| **S1 좌측면 얼굴** | `references/suni/suni_S1_left_profile_crop.png` | **승인(r8)** | `a35ceeed…1e9f` (S1·S2 시트) |
| **S2 의상 A 정면** | `references/suni/suni_S2_A_front_crop.png` | **승인(r8)** | 동일 |
| **S2 의상 A 측면** | `references/suni/suni_S2_A_side_crop.png` | **승인(r8)** | 동일 |
| **S2 의상 A 후면** | `references/suni/suni_S2_A_back_crop.png` | **승인(r8)** | 동일 |
| S3 의상 B 전신 | — | 후보, 이미지 미접수 | — |
| S4 의상 C 전신 | — | 후보, 이미지 미접수 | — |
| S5·S6 | — | 후보, 미제작 | — |
- 전체 SHA-256은 `AI_AGENT_EP01_r8_FILE_LIST.md`. GPU 환경으로 옮긴 뒤 일치 여부 확인
- 얼굴 참조는 **기존 승인 시트의 얼굴 크롭을 우선**하고, S1은 좌측면 컷, S2는 의상 A 전신 컷에 사용

## 2. 컷별 연결 (정본: `AI_AGENT_EP01_CHARACTER_CUT_MAP_r8.json`)
- 의상 A 서수니 컷(≤48)마다 `costume_fullbody_A`에 S2 정면·측면·후면 연결, 얼굴 필수 컷에는 S1 좌측면 연결
- 컷 22 이후 상처 상태 참조(S5·S6)는 미제작이라 `미제작`으로 표시
- 컷 24·58(POV)은 얼굴이 실제로 보일 때만 얼굴 참조

## 3. 의상 A 프롬프트 블록 (시트 관찰 기반)
```
wearing a black waterproof hooded jacket with a center zipper, chest buckle strap and shoulder straps crossing on the back, waist buckle belt with small pouches, black fingerless gloves, black cargo pants with thigh straps, black chunky lace-up combat boots, wet from rain, night rain lighting with cool blue-white light
```
- 컷 1~21: 재킷 온전. 컷 22~42: `a shallow knife graze and torn jacket on her own left flank`(S5 후 확정). 컷 43~48: `white hemostatic bandage on her own left flank`(S6 후 확정)
- 다리 파우치: 사용자 확인 전에는 프롬프트에 넣지 않거나 `small thigh pouch`로만 표기
- 네거티브 추가: `orange clips, orange straps, orange gloves, wound on right side`

## 4. 검증 기준 추가
| 번호 | 기준 |
|---|---|
| V-S9 | 의상 A 구성(후디 재킷·하네스·손가락 없는 장갑·카고 바지·전투화)이 S2와 일치, 주황 요소 없음 |
| V-S10 | 얼굴이 S1·S2가 아니라 **기존 승인 시트**와 같은 사람으로 보임 |
V-S1~S8, V-L1~L4는 이전과 동일. 자동 지표 임계값은 11월 환경에서 합의, 사용자 검수가 최종.

## 5. 남은 준비
S3·S4 접수·검수 → S5·S6 제작 → 다리 파우치 확인 → 리더 손 좌우·장비 검수 → 컷 62 이후 붕대 결정 → 11월 GPU 환경 확인 → 대표 컷 10개 테스트 → 정식 원본 확보 후 원작 대조(현재 0/68컷)
