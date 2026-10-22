# ComfyUI/Wan 참조 목록 r8 rev5

r8 rev3 참조 목록의 개정본입니다. 이전 내용(변경 금지, 검증 기준, 대표 컷, 11월 확인 사항)은 그대로이며 준비 자료입니다. 생성한 이미지·영상은 없고 유료 생성은 별도 승인 전까지 하지 않습니다.

## `CH_SUNI` 참조 등록 (모두 승인)
| 구분 | 위치 | 시트 SHA-256 |
|---|---|---|
| 기준 얼굴 | `deliver_r8/references/suni/SUNI_APPROVED_SHEET_original.png` | `461926ff…3be6` |
| S1·S2 | `deliver_r8/references/suni/` | `a35ceeed…1e9f` |
| S3·S4 | `deliver_r8_rev2/references/suni/` | `89cc41fa…dd0b` |
| **S5·S6** | `references/suni/suni_S5_*`, `suni_S6_*` | `37e8ab8c…57e5` |
| S5·S6 v1(사용 금지) | `deliver_r8_rev3/references/suni_candidates/` | `ccde0596…356e1` |

## 컷별 연결 (정본 `AI_AGENT_EP01_CHARACTER_CUT_MAP_r8_rev5.json`)
컷 22~42 상처 참조 = S5, 컷 43~48 = S6. 의상 A·B·C는 S2·S3·S4. 컷 1~21과 49 이후는 상처 참조 없음.

## 부상·재킷 프롬프트
- 컷 22~42: `a shallow knife graze on her own left flank, a thin red line with minimal blood, her black hooded jacket kept zipped with a clearly visible cut in the fabric at the left flank` (정면에서 화면 오른쪽)
- 컷 43~48: `her black jacket opened so a white hemostatic bandage on her own left flank is visible, same torn spot, minimal blood`
- 네거티브: `deep open wound, gaping skin, excessive blood, blood on chest, wound on right side, symmetrical wounds, facial wound`
- S5·S6 시트의 열린 재킷은 위치·수위 참조용이며 컷 22~42 생성에서 재킷을 열지 않음
- 클로즈업의 소량 혈흔은 참조 이미지에서만 허용, 컷에서는 최소화

## 검증 기준
V-S13(상처 위치·수위)에 더해 **V-S14**: 컷 22~42에서 재킷이 닫혀 있고 찢김이 식별되는가, 컷 43~48에서 재킷이 열려 붕대가 보이는가. 나머지 V-S1~S12, V-L1~L4는 유지. 자동 지표 임계값은 11월 환경에서 합의, 사용자 검수가 최종.

## 남은 준비
S4 어깨 끈 확인 → 컷 62 이후 붕대 노출 결정 → 리더 손 좌우·장비 검수 → 11월 GPU 환경 확인(모델·워크플로·해상도) → 대표 컷 10개(서수니 5·22·40·50·60·68, 리더 38·43·64·65) 테스트 → 정식 원본 확보 후 원작 대조(0/68컷)
