# 《AI AGENT》 EP01 ComfyUI/Wan 인계 r7

r6 인계(`deliver_r6/AI_AGENT_EP01_COMFYUI_HANDOFF_r6.md`)에 리더 등록을 더한 문서입니다. 11월 GPU 환경에서 모델·워크플로·해상도를 확인하기 전까지 모두 준비 자료이며, 지금까지 Claude가 생성한 이미지·영상은 없습니다. 유료 생성은 별도 승인 전까지 하지 않습니다.

## 1. 캐릭터 등록
| ID | 승인 이미지 | SHA-256 | 상태 |
|---|---|---|---|
| `CH_SUNI` | `references/suni/SUNI_APPROVED_SHEET_original.png` | `461926ffdb3a391c6b31d55aa759a4716674264209947d9cf55c7732e7f23be6` | APPROVED — VISUAL REFERENCE |
| `CH_LEADER` | `references/leader/LEADER_APPROVED_SHEET_v2_original.png` | `7cb7de4a7bb480e6f5f96b77936ccf8529874c740a16595ca08f8de5a137db59` | APPROVED — VISUAL REFERENCE |
- 크롭 목록과 SHA-256은 `AI_AGENT_EP01_r7_FILE_LIST.md`. GPU 환경으로 옮긴 뒤 SHA-256으로 원본 일치를 확인
- 얼굴 참조는 정면·3/4·측면 얼굴 크롭을 우선(고해상). 표정·의상·손 크롭은 보조
- 서수니 좌측면·전신 후보(S1~S6)는 미생성 → `SUNI_SIDE_FULLBODY_SPEC_r7.md`

## 2. 컷별 참조 연결
정본: `AI_AGENT_EP01_CHARACTER_CUT_MAP_r7.json` (서수니 51컷, 리더 13컷, 대표 검증 컷 10)
| 구분 | 컷 |
|---|---|
| 리더 얼굴 필수 | 34, 37, 38, 39, 43, 44, 65 |
| 리더 전신·실루엣 | 10, 27, 35, 36, 63, 64 |
| 리더 야간 복장 → 체포조 복장 | 10~44 야간 / 63~65 체포조 |
| 오른손 응급 고정 / 정식 부목 | 43·44·45 / 64·65 |
| 서수니 POV 규칙 | 컷 24·58은 화면에 얼굴이 보일 때만 참조 |

## 3. 리더 생성 조건
**긍정 기본 블록** (참조 이미지를 넣으면 문구는 최소화)
```
photorealistic cinematic photograph of a Korean man, square angular jaw, thick low nearly straight eyebrows, small heavy-lidded dark brown eyes, large prominent ears, short cropped black hair, rough natural skin with pores and rain moisture, no scars, no facial marks
```
- 야간: `dark olive tactical work jacket wet from rain, right hand bandaged` (컷 37~39는 부음, 43~45는 붕대+금속 막대 간이 부목)
- 체포조: `black tactical gear and helmet with goggles raised on the helmet, right hand in a white rigid splint with black velcro band`
**네거티브**
```
eyebrow scar, scar on face, facial marks, tattoo, glasses, beard, text, watermark, logo, cartoon, illustration, 3d render look, plastic skin, beauty filter, extra fingers, deformed hands, different person, left hand splint
```

## 4. 외형 변경 금지 조건
서수니: r6 인계 4절과 동일(얼굴·헤어·질감·인상 고정, 나이·키에 맞춘 조정 금지, 흉터 추가 금지).
리더:
1. 얼굴형·눈썹·눈매·턱선은 v2 기준으로 고정(사용자 승인)
2. 컷 65 귀 일부 가림은 허용, 큰 귀 특징은 보이는 범위에서 유지
3. 눈썹 흉터·얼굴 표식·문신 추가 금지
4. 응급 고정(43)과 정식 부목(64)을 섞지 않음, 둘 다 오른손
5. 부목은 왼손에 나오면 재생성
공통: 컷 14·15 사격 3명, 42·45 대사 보류, 52 시각 없음, 59 잠정+V1, 67 세븐 음성 없음은 r5 그대로.

## 5. 생성 전후 검증 기준
| 번호 | 기준 | 방법 |
|---|---|---|
| V-S1~S6 | 서수니 r6 기준(얼굴·헤어·흉터·의상 구간·부상·대표 컷 비교) | 육안 |
| V-L1 | 컷 38·65 같은 사람(눈썹·눈매·턱선 고정, 귀는 가림 허용) | 육안, `LEADER_38_65_COMPARISON_r7.md` 표 |
| V-L2 | 응급 고정 ≠ 정식 부목, 모두 오른손 | 육안 |
| V-L3 | **손 좌우 방향** | 육안, 제작 전 추가 검수 |
| V-L4 | **장비 세부**(패치·견장·벨트)가 컷마다 달라지지 않음 | 육안, 제작 전 추가 검수 |
- 자동 유사도 지표를 쓸 경우 임계값은 11월 환경에서 대표 컷으로 정해 합의. 자동 통과가 사용자 검수를 대체하지 않음
- 원본 대조가 끝나지 않은 컷은 PASS 처리하지 않음

## 6. 대표 컷
서수니 5·22·40·50·60·68, 리더 38·43·64·65. 이 10컷에서 얼굴 유지가 확인되면 나머지로 확장.

## 7. 남은 영상 제작 준비
1. 11월 GPU 환경에서 모델·얼굴 참조 방식·Wan 버전·해상도·VRAM 확인
2. 서수니 의상 소품 확정 후 S1~S4(좌측면·전신) 제작·승인
3. 부상 쪽(좌측 여부) 확정 후 S5·S6
4. 리더 손 좌우·장비 세부 검수 기준 확정
5. 대표 컷 10개로 얼굴 유지 테스트 → 확장
6. 컷 62 이후 붕대 노출, 컷별 지시 확정
7. 정식 원본 확보 후 원작 대조(현재 0/68컷)
