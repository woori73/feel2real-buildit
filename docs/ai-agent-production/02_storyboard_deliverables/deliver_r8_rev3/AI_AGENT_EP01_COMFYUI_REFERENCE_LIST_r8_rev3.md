# ComfyUI/Wan 참조 목록 r8 rev3

r8 rev2 참조 목록의 개정본입니다. 이전 내용(변경 금지, 검증 기준, 대표 컷, 11월 확인 사항)은 그대로이며 준비 자료입니다. 생성한 이미지·영상은 없고 유료 생성은 별도 승인 전까지 하지 않습니다.

## `CH_SUNI` 참조 현황
| 구분 | 상태 | 위치 |
|---|---|---|
| 기준 얼굴 시트(`461926ff…3be6`) | 승인 | `deliver_r8/references/suni/` |
| S1·S2(`a35ceeed…1e9f`) | 승인 | `deliver_r8/references/suni/` |
| S3·S4(`89cc41fa…dd0b`) | 승인 | `deliver_r8_rev2/references/suni/` |
| **S5·S6(`ccde0596…356e`)** | **후보, 생성 참조로 사용 금지** | `references/suni_candidates/` |

## 소품 규칙 (확정)
- 의상 A: 오른쪽 허벅지 `small thigh equipment pouch` 사용 가능, `holster` 금지
- 컷 49: `only the end of a pocket watch chain showing outside the chest pocket`
- 컷 50: `her hand resting on a pocket watch inside the chest pocket, the watch itself not visible`
- 네거티브(49·50): `pocket watch worn as a necklace, visible watch face`
- 의상 B 권총집은 49·53·61 전신에서만 `small holster on her right hip`, 상반신은 `holster` 제외

## 부상 프롬프트 (S5·S6 승인 후 확정)
- 컷 22~42: `a shallow knife graze and torn jacket on her own left flank` (정면에서 화면 오른쪽)
- 컷 43~48: `white hemostatic bandage on her own left flank`
- 후보 시트의 수위는 콘티보다 강하므로 그대로 쓰지 않음
- 네거티브: `deep open wound, excessive blood, wound on right side, symmetrical wounds, facial wound`

## 검증 기준
이전 V-S1~V-S12, V-L1~L4 유지. 추가: **V-S13** 상처가 서수니 왼쪽 옆구리(정면 화면 오른쪽, 후면 화면 왼쪽)인지, 수위가 콘티(얕은 스친 자국) 수준인지.

## 남은 준비
S5·S6 승인 여부 → S4 어깨 끈 확인 → 리더 손 좌우·장비 검수 → 컷 62 이후 붕대 결정 → 11월 GPU 환경 확인 → 대표 컷 10개 테스트 → 정식 원본 확보 후 원작 대조(0/68컷)
