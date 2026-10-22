# 《AI AGENT》 EP01 용역 리더 — 영화적 디자인 명세 r6

> **상태: 이미지 미생성.** 이 세션에는 실사 이미지를 생성할 도구를 쓰지 않았습니다(외부 이미지 도구는 비용 구조를 모르는 상태라 사용자 승인 없이 쓰지 않음). 이 문서는 제작용 명세와 프롬프트이며, 정면·측면·3/4·전신·컷 38/65 조명 비교 시안은 **아직 존재하지 않습니다.**
> 아래 외형은 r4·r5 도식 설명과 컷 38·65 지시에서 가져온 **제작 가정**이며 정본이 아닙니다. 사용자 검수 전에는 확정하지 않습니다.

## 1. 고정 조건 (지시서 기준)
- 한국인 남성, 각진 턱, 굵고 낮은 눈썹, 짧은 머리, 큰 귀, 동일한 눈·코·입 구조
- 컷 38과 컷 65에서 같은 얼굴
- 야간 복장과 체포조 복장 구분
- 컷 43 오른손 응급 고정, 컷 64 정식 부목
- **NO eyebrow scar.** 눈썹 흉터는 미확정이므로 넣지 않음. 얼굴의 흉터·점·문신도 넣지 않음

## 2. 얼굴 명세
| 항목 | 명세 |
|---|---|
| 연령 인상 | 30대 후반~40대(제작 가정) |
| 얼굴형 | 정면에서 각진 턱, 광대보다 아래턱이 넓은 사각형 |
| 눈썹 | 굵고 낮고 거의 일자. 눈과 간격이 좁음. 흉터·끊김 없음 |
| 눈 | 작은 편, 무거운 윗눈꺼풀, 짙은 갈색 |
| 코 | 콧대 중간 굵기, 코끝 둥근 편 |
| 입 | 얇고 일자, 입꼬리 약간 내려감 |
| 귀 | 큰 귀, 머리 옆으로 눈에 띄게 나옴 |
| 머리 | 짧게 친 검은 머리, 관자놀이 쪽 선이 올라감 |
| 피부 | 자연 질감, 거칠고 비에 젖음 |
| 식별 특징 | 눈썹 흉터 없이 **큰 귀 + 각진 턱 + 낮은 굵은 눈썹**의 조합으로 컷 38·65를 식별 |

## 3. 복장·손 상태
| 구분 | 컷 | 복장 | 오른손 |
|---|---|---|---|
| 야간 | 10, 27, 34~39, 43~44 | 짙은 올리브 전술 작업 재킷, 젖음 | 37 손가락 골절 → 38·39 부음 → 43·44 붕대+간이 부목(응급 고정) |
| 체포조 | 63~65 | 검은 전술 장비, 헬멧(바이저 올림), 경찰 특공 지원팀 복장 | 64·65 흰 부목+검정 밴드(정식 부목) |
- 컷 63은 선두 얼굴이 가려진 상태, 컷 64는 손과 선두 남자 위주, 컷 65에서 컷 38 얼굴이 플래시컷으로 겹침
- 컷 43의 응급 고정(붕대+나무/금속 막대 느낌의 간이 부목)과 컷 64의 정식 부목(흰 규격 부목+검정 밴드)은 **다른 물건**이어야 하며 같은 손가락(오른손)

## 4. 제작 시안 목록 (전부 미생성)
| 번호 | 시안 | 조건 | 상태 |
|---|---|---|---|
| L1 | 정면 흉상 | 야간 복장, 중립 표정 | 미생성 |
| L2 | 3/4 흉상 | L1과 같은 얼굴 | 미생성 |
| L3 | 측면 흉상(귀·턱 선 확인) | L1과 같은 얼굴 | 미생성 |
| L4 | 전신 야간 | 오른손 부음(컷 38~39 상태) | 미생성 |
| L5 | 전신 체포조 | 헬멧 바이저 올림, 정식 부목 | 미생성 |
| L6 | 오른손 인서트 응급 고정 | 컷 43 | 미생성 |
| L7 | 오른손 인서트 정식 부목 | 컷 64 | 미생성 |
| L8 | 컷 38 조명 비교용 | 지게차 헤드라이트·빗물 반사, 청흑+황색 | 미생성 |
| L9 | 컷 65 조명 비교용 | 현관 모니터 어안→암전, 폰 청백광, 체포조 복장 | 미생성 |
L8·L9는 **같은 얼굴 구조**를 다른 조명에서 비교하는 용도입니다. L1을 기준 참조로 먼저 확정(사용자 검수)한 뒤 L2~L9를 L1 참조로 만듭니다.

## 5. 생성용 프롬프트 (영어, 모델 비의존. 실제 모델은 11월 환경에서 확인)
공통 얼굴 블록 `LEADER_FACE`:
```
photorealistic cinematic photograph of a Korean man in his late 30s to 40s, square angular jaw, thick low nearly straight eyebrows close above small heavy-lidded dark brown eyes, rounded nose tip, thin straight lips with slightly down-turned corners, large prominent ears, short cropped black hair with raised temple lines, rough natural skin texture with visible pores and rain moisture, no scars, no facial marks, no tattoos
```
공통 네거티브 `LEADER_NEG`:
```
eyebrow scar, scar on face, facial marks, tattoo, glasses, beard, text, watermark, logo, caption, cartoon, illustration, 3d render look, plastic skin, beauty filter, extra fingers, deformed hands, different person
```
| 시안 | 추가 문구 |
|---|---|
| L1 | `front view head and shoulders, looking at camera, neutral stern expression, wearing a dark olive tactical work jacket wet from rain, night rain lighting with cool teal key light, 85mm lens, shallow depth of field, fine film grain, 16:9` |
| L2 | L1과 동일 + `three-quarter view` (L1 이미지를 얼굴 참조로 첨부) |
| L3 | L1과 동일 + `pure side profile, ear and jawline clearly visible` (L1 참조) |
| L4 | `full body, standing, dark olive tactical work jacket, right hand visibly swollen at the fingers, wet pavement, night rain, forklift headlights and container background` |
| L5 | `full body, black tactical gear, helmet with visor raised, police special-support team gear, right hand in a clean white rigid splint with a black band, dim corridor` |
| L6 | `close-up of a man's right hand, fingers bound with gauze bandage and an improvised makeshift splint, rainy night, emergency blue-red siren flicker` |
| L7 | `close-up of a man's right hand in a clean white commercial finger splint with a black elastic band, dark corridor, cold blue-white phone glow` |
| L8 | L1 얼굴 + `medium shot, forklift headlight glare, rain streaks and water reflections, blue-black with yellow forklift light, face clearly visible` |
| L9 | L1 얼굴 + `close-up in a dark door-camera fisheye feel, cold blue-white phone glow, police special-support gear, helmet visor raised` |
- 이미지 참조(IP-Adapter/얼굴 참조류)로 L1을 모든 후속 시안에 넣고, 시드를 고정해 같은 얼굴을 유지하는 방식을 11월 GPU 환경에서 검증합니다(방식·모델은 미확정).

## 6. 동일 인물 검수 기준 (사용자 육안 검수 + 체크리스트)
- 컷 38 시안(L8)과 컷 65 시안(L9)을 나란히 두고 턱 각, 눈썹 굵기·높이, 귀 크기·위치, 눈·코·입 간격이 같은지 확인
- 조명이 달라도 두 이미지가 같은 사람으로 보일 것
- 눈썹 흉터·얼굴 표식이 없을 것
- 오른손 상태: 43 응급 고정 ≠ 64 정식 부목, 두 경우 모두 오른손
- 위 항목 중 하나라도 어긋나면 재생성. 수치형 임계값은 11월 환경에서 합의

## 7. 사용자 확인 필요
1. 위 얼굴 명세(제작 가정)를 시안 제작 기준으로 써도 되는지
2. 리더의 식별 특징을 눈썹 흉터 없이 "큰 귀·각진 턱·낮은 눈썹"으로 가는 데 동의하는지(흉터는 계속 보류)
3. 이미지 생성 경로 선택: ① 외부 도구(비용 확인 후) ② 11월 GPU ComfyUI ③ 일러스트레이터 의뢰
