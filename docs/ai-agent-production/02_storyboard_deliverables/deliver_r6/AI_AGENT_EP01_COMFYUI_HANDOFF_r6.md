# 《AI AGENT》 EP01 ComfyUI/Wan 인계 r6

대상: 11월 GPU 환경. 이 문서는 **준비 자료**입니다. 모델·워크플로·해상도는 그 환경에서 확인한 뒤 확정하며, 지금까지 이미지·영상은 하나도 생성하지 않았습니다. 유료 이미지·영상 생성은 별도 승인 전까지 하지 않습니다.

## 1. 캐릭터 참조 등록
| 항목 | 값 |
|---|---|
| 캐릭터 ID | `CH_SUNI` |
| 승인 이미지 | `references/suni/SUNI_APPROVED_SHEET_original.png` |
| SHA-256 | `461926ffdb3a391c6b31d55aa759a4716674264209947d9cf55c7732e7f23be6` |
| 승인 상태 | APPROVED — CHARACTER VISUAL REFERENCE |
| 정면 참조 | `references/suni/suni_front_face_crop.png` |
| 3/4 참조 | `references/suni/suni_three_quarter_face_crop.png` |
| 우측면 참조 | `references/suni/suni_right_side_face_crop.png` |
| 좌측면·전신 | **없음(후보 제작 필요, 미확정)** |
| 표정 참조 | `references/suni/suni_expr_01~06_*_crop.png` (저해상도 썸네일, 식별용) |
| 의상 A / B / C | `references/suni/suni_costume_A|B|C_crop.png` + 구간 조건(아래 3절) |
- 이 파일들은 작업 폴더 상대 경로입니다. GPU 환경에 옮길 때 SHA-256으로 원본 일치를 확인하세요.
- 생성에 넣는 얼굴 참조는 **정면·3/4·우측 얼굴 크롭**(고해상)을 우선하고, 표정·의상 크롭은 보조로만 쓰세요.
- 리더 `CH_LEADER`는 아직 승인 이미지가 없습니다. `LEADER_DESIGN_r6.md`의 L1을 만들어 사용자 승인 후 같은 방식으로 등록합니다.

## 2. 컷별 참조 연결
정본은 `AI_AGENT_EP01_CHARACTER_CUT_MAP_r6.json`입니다(68컷 전체 항목, 서수니 51컷, 리더 13컷).
| 구분 | 컷 |
|---|---|
| 의상 A · 얼굴 필수 | 5, 6, 9, 11, 13, 16, 17, 21, 22, 24, 25, 28, 30, 34, 37, 38, 39, 40, 42, 44, 45, 47 |
| 의상 A · 실루엣/전신 위주 | 7, 8, 12, 14, 19, 20, 23, 26, 27, 29, 33, 35, 36, 41 |
| 의상 B · 얼굴 필수 | 50, 54, 58, 59, 60 |
| 의상 B · 실루엣/전신 위주 | 49, 51, 53, 55, 56, 61 |
| 의상 C · 얼굴 필수 | 65, 68 |
| 의상 C · 실루엣/전신 위주 | 62, 66 |
| 리더 | 10, 27, 34, 35, 36, 37, 38, 39, 43, 44, 63, 64, 65 (얼굴 필수: 34, 37, 38, 39, 43, 44, 65) |
- 컷 24·58은 수니 시점(POV)이라 화면에 수니 얼굴이 나오지 않을 수 있습니다. 컷 구성 확인 후 참조 적용 여부를 정하세요.
- 컷 10은 "용역 6명(리더 포함 해석)"이며 리더 얼굴 비중은 낮습니다.
- 이 매핑은 기존 러프 패널 68장을 **다시 만들라는 뜻이 아닙니다.** 러프 패널은 `panels_preserved_68/`에 그대로 있고, 새 이미지는 후보로만 만듭니다.

## 3. 서수니 생성 조건
### 3.1 긍정 프롬프트 기본 블록 (`SUNI_FACE`)
승인 시트 관찰을 문장으로 옮긴 것이며, 얼굴 참조 이미지를 넣었다면 문구는 최소화하세요.
```
photorealistic cinematic photograph of a Korean woman in her 30s, calm slightly long dark brown eyes with faint shadow under the eyes, straight nose, thin lips, natural skin texture with visible pores and rain moisture, dark brown hair pulled back into a low ponytail with loose strands falling around the face, no scars, no piercings, no glasses
```
### 3.2 구간별 추가 블록 (의상 세부는 사용자 확인 전 임시)
- A: `wearing a black hooded waterproof jacket with harness straps, wet from rain, night rain lighting with cool teal key light`
- B: `wearing a navy jacket over a light top, bright daytime corridor, soft cool white light`
- C: `wearing a cream long-sleeve top with a shoulder strap, no jacket`
- 위 의상 문구는 **시트 관찰 기반 임시 문구**입니다. 주황 클립·권총집·경찰 점퍼 등 r5 소품을 쓸지는 사용자 결정(APPROVALS 항목 3) 후 추가합니다.
### 3.3 네거티브
```
text, watermark, logo, caption, scar on face, eyebrow scar, glasses, heavy makeup, extra fingers, deformed hands, cartoon, illustration, 3d render look, plastic skin, beauty filter, different person, older face, younger face, changed hairstyle
```

## 4. 외형 변경 금지 조건
1. 승인 얼굴의 눈매·코·입술·얼굴형·귀·피부 질감을 바꾸지 않는다
2. 헤어: 낮은 포니테일+잔머리 구조 유지. 단발·풀어 내린 머리·앞머리 변경 금지(컷에서 헤어가 흐트러지는 것은 비·움직임 표현 수준만)
3. 나이 33세·키 168cm에 맞춰 얼굴 인상을 조정하지 않는다
4. 얼굴에 흉터·상처·점을 새로 넣지 않는다. 부상은 컷 연속성표(옆구리)에 따른 의상 표현만
5. 뷰티 필터·과한 보정·만화풍·3D 렌더 질감 금지
6. 의상 A/B/C 구간 경계를 넘지 않는다(컷 49 이전 B 금지, 컷 62 이후 점퍼 금지 등)
7. 세븐 음성(컷 67) 추가, 컷 14·15 사격 인원(3명), 컷 42·45 대사 보류, 컷 52 시각, 컷 59 연출은 r5 그대로

## 5. 생성 전후 동일 인물 검증 기준
**생성 전**: 참조 파일 SHA-256 일치, 컷 맵의 의상 구간·부상 상태 확인
**생성 후 (사용자 육안 검수가 최종, 자동 점수는 보조)**
| 번호 | 기준 | 판정 |
|---|---|---|
| V-S1 | 정면·3/4 얼굴이 승인 시트와 같은 사람 | 육안 |
| V-S2 | 헤어 구조(낮은 포니테일+잔머리) 일치 | 육안 |
| V-S3 | 얼굴 흉터·피어싱·안경 없음 | 육안 |
| V-S4 | 의상 구간 일치 | 컷 맵 대조 |
| V-S5 | 부상·붕대 상태가 컷 연속성표와 일치 | 컷 맵 대조 |
| V-S6 | 같은 컷의 인접 컷끼리 얼굴 불일치 없음(대표 컷 5·22·40·50·60·68 순서 비교) | 육안 |
| V-L1 | 컷 38·65 리더 같은 사람(턱·눈썹·귀·눈코입) | 육안 |
| V-L2 | 응급 고정(43)과 정식 부목(64) 구분, 모두 오른손 | 육안 |
- 얼굴 임베딩 유사도 같은 자동 지표를 쓸 경우 **임계값은 11월 환경에서 대표 컷 샘플로 정한 뒤 사용자와 합의**합니다(지금은 수치를 정하지 않음).
- 자동 지표가 통과해도 사용자 검수 전에는 확정하지 않습니다. 정식 원본 대조가 끝나지 않은 컷은 PASS 처리하지 않습니다.

## 6. 인물 일관성 검증 대표 컷
| 구분 | 컷 | 이유 |
|---|---|---|
| 서수니 | 5 | A 야간 얼굴 기준 클로즈업 |
| 서수니 | 22 | A 부상 직후 |
| 서수니 | 40 | A 붕대 이후(수첩) |
| 서수니 | 50 | B 낮 복도 클로즈업 |
| 서수니 | 60 | B 낮 클로즈업 |
| 서수니 | 68 | C 마지막 클로즈업 |
| 리더 | 38 | 야간 얼굴 기준 |
| 리더 | 65 | 체포조 복장, 컷 38 얼굴 플래시 |
| 리더 | 43, 64 | 응급 고정 / 정식 부목 오른손 |
먼저 대표 컷 10개로 얼굴 유지가 되는지 확인한 뒤 나머지 컷으로 확장하는 순서를 권장합니다.

## 7. 11월 환경에서 확인할 것 (현재 미정)
- 사용 모델·체크포인트, 얼굴 참조 방식, Wan 영상 모델 버전
- 해상도·프레임 수·VRAM 한도
- 이전 인계 파일: `deliver_r5/comfyui/ep1_r5_manifest.json`, `continuity_anchors.json`은 컷 앵커용으로 계속 사용(수니 프롬프트 부분만 이 문서가 대체)
- Kling AI는 핵심 장면 보완용으로만, 사용 전 별도 승인
