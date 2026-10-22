# 《AI AGENT》 EP01 r9 ComfyUI/Wan 제작 프롬프트

> 이미지·영상 생성 0건(이번 단계와 전체 프로젝트에서 Claude가 생성한 것 없음). 모든 컷 pass_confirmed=false, 정식 원작 대조 0/68. 새 사건·대사 없음. [제안]은 승인 전 미확정.

대상: 11월 GPU 환경. 모델·워크플로·해상도·fps·VRAM은 **미정**이라 값을 적지 않았습니다. 프롬프트는 모델 비의존 영어 문장이며, 얼굴 참조 이미지를 넣으면 얼굴 문구는 줄이세요. 유료 영상 생성 별도 승인 전 사용 금지.

## 공통 규칙
- 16:9, 컷 길이는 콘티 초수
- 자막·숫자·글자 생성 금지(합성 항목은 후반 작업)
- 좌우 반전 금지, 생성 후 왼쪽 옆구리/오른손 위치 확인
- 얼굴 변경 금지, 눈썹 흉터 금지
- 대사·효과음은 음성/후반 작업. 영상에 새 사건 추가 금지
- 컷 67의 세븐 음성 없음 유지

## 컷 5 (2.0초)
**참조 입력**
- face: references/suni/base/suni_front_face_crop.png
- face: references/suni/base/suni_three_quarter_face_crop.png
- face: references/suni/base/suni_right_side_face_crop.png
- expression: references/suni/base/suni_expr_02_determined_crop.png
- costume: references/suni/S1_S2/suni_S2_A_front_crop.png
- costume: references/suni/S1_S2/suni_S2_A_side_crop.png
- costume: references/suni/S1_S2/suni_S2_A_back_crop.png

**Positive**
```
photorealistic cinematic photograph of a Korean woman in her 30s, calm slightly long dark brown eyes with faint shadow under the eyes, straight nose, thin lips, natural skin texture with visible pores and rain moisture, dark brown hair pulled back into a low ponytail with loose strands falling around her face, no scars, no piercings, no glasses. Close-up in a helicopter cabin, her determined unblinking eyes in the upper frame, in the lower frame a gloved hand in a black fingerless glove snapping a harness buckle across her chest. She wears a black waterproof hooded jacket with a center zipper, chest buckle strap and shoulder straps crossing on the back, waist buckle belt with small pouches, black fingerless gloves, black cargo pants with thigh straps, black chunky lace-up combat boots, wet from rain. Dim red auxiliary cabin light, open door behind her showing rain and darkness, dark green-grey and blue-black palette, 16:9 cinematic, shallow depth of field, fine film grain.
```
**Negative**
```
text, subtitles, watermark, logo, caption, cartoon, illustration, 3d render look, plastic skin, beauty filter, extra fingers, deformed hands, different person, mirrored image, flipped image, eyebrow scar, scar on face, orange straps, orange clips, wound, torn jacket, gun visible
```
**움직임(Wan 영상용)**: 카메라 고정에서 시작해 하네스 걸쇠 쪽으로 짧게 푸시인. 눈은 거의 움직이지 않음. 걸쇠가 채워질 때 손만 움직임(효과음 '철컥'은 별도)

**합성 금지/별도**: 없음

**생성 후 확인**: 손 좌우 콘티 미지정. 총기는 오른손 권총(컷 5 프레임에는 총 비노출)

## 컷 22 (2.0초)
**참조 입력**
- costume: references/suni/S1_S2/suni_S2_A_front_crop.png
- costume: references/suni/S1_S2/suni_S2_A_side_crop.png
- costume: references/suni/S1_S2/suni_S2_A_back_crop.png
- wound: references/suni/S5_S6/suni_S5_wound_front_crop.png
- wound: references/suni/S5_S6/suni_S5_wound_side_crop.png
- wound: references/suni/S5_S6/suni_S5_wound_back_crop.png
- wound: references/suni/S5_S6/suni_S5_closeup_front_crop.png
- wound: references/suni/S5_S6/suni_S5_closeup_jacket_crop.png
- wound: references/suni/S5_S6/suni_S5_closeup_position_crop.png

**Positive**
```
Extreme close-up of a woman's left flank seen from her left side. Her black waterproof hooded jacket with a center zipper, chest buckle strap and shoulder straps crossing on the back, waist buckle belt with small pouches, black fingerless gloves, black cargo pants with thigh straps, black chunky lace-up combat boots, wet from rain; the black jacket is zipped closed with a clean-cut tear in the fabric at the left flank revealing skin, and a thin shallow red knife-graze line on the skin with minimal blood. Total darkness with thin white rain streaks, only a faint cold edge light on the torn fabric and the red line. Black palette, 16:9 cinematic, shallow depth of field, film grain.
```
**Negative**
```
text, subtitles, watermark, logo, caption, cartoon, illustration, 3d render look, plastic skin, beauty filter, extra fingers, deformed hands, different person, mirrored image, flipped image, eyebrow scar, scar on face, deep open wound, gaping skin, excessive blood, blood on chest, wound on right side, symmetrical wounds, open jacket
```
**움직임(Wan 영상용)**: 거의 정지. 짧은 숨에 맞춘 옆구리의 미세한 움직임. 암전에서 가는 빛이 찢김과 붉은 선에 닿음

**합성 금지/별도**: 없음

**생성 후 확인**: 부상은 서수니의 왼쪽 옆구리. 이 컷에서 좌우 반전 금지(반전 시 오른쪽 옆구리가 됨)

## 컷 38 (3.0초)
**참조 입력**
- face: references/suni/base/suni_front_face_crop.png
- face: references/suni/base/suni_three_quarter_face_crop.png
- face: references/suni/base/suni_right_side_face_crop.png
- costume: references/suni/S1_S2/suni_S2_A_front_crop.png
- costume: references/suni/S1_S2/suni_S2_A_side_crop.png
- costume: references/suni/S1_S2/suni_S2_A_back_crop.png
- wound: references/suni/S5_S6/suni_S5_wound_front_crop.png
- wound: references/suni/S5_S6/suni_S5_wound_side_crop.png
- wound: references/suni/S5_S6/suni_S5_wound_back_crop.png
- wound: references/suni/S5_S6/suni_S5_closeup_front_crop.png
- wound: references/suni/S5_S6/suni_S5_closeup_jacket_crop.png
- wound: references/suni/S5_S6/suni_S5_closeup_position_crop.png
- leader_face: references/leader_v2/leader_v2_front_crop.png
- leader_face: references/leader_v2/leader_v2_three_quarter_crop.png
- leader_face: references/leader_v2/leader_v2_right_side_crop.png
- leader_costume: references/leader_v2/leader_v2_fullbody_night_crop.png
- leader_lighting: references/leader_v2/leader_v2_cut38_lighting_crop.png

**Positive**
```
Medium two-shot in rain between a forklift and shipping containers. Left: photorealistic cinematic photograph of a Korean woman in her 30s, calm slightly long dark brown eyes with faint shadow under the eyes, straight nose, thin lips, natural skin texture with visible pores and rain moisture, dark brown hair pulled back into a low ponytail with loose strands falling around her face, no scars, no piercings, no glasses, wearing a black waterproof hooded jacket with a center zipper, chest buckle strap and shoulder straps crossing on the back, waist buckle belt with small pouches, black fingerless gloves, black cargo pants with thigh straps, black chunky lace-up combat boots, wet from rain, jacket zipped closed with a visible tear at her left flank. Right: Korean man in his late 30s to 40s, square angular jaw, thick low nearly straight eyebrows, small heavy-lidded dark brown eyes, large prominent ears, short cropped black hair, rough natural skin with pores and rain moisture, no scars, no facial marks, wearing a dark olive tactical work jacket wet from rain, right hand visibly swollen at the fingers, face turned toward the camera and clearly visible. They face each other. Forklift headlights and rain reflections, blue-black palette with yellow forklift light, 16:9 cinematic, film grain.
```
**Negative**
```
text, subtitles, watermark, logo, caption, cartoon, illustration, 3d render look, plastic skin, beauty filter, extra fingers, deformed hands, different person, mirrored image, flipped image, eyebrow scar, scar on face, two women, two men, merged faces, swapped faces, eyebrow scar
```
**움직임(Wan 영상용)**: 거의 고정 투샷. 빗줄기와 헤드라이트 반사만 움직임. 리더는 말하지 않음, 서수니의 대사 '이름.'은 음성 별도

**합성 금지/별도**: 없음

**생성 후 확인**: 서수니 부상=왼쪽 옆구리. 리더 부은 손=오른손

## 컷 40 (3.0초)
**참조 입력**
- costume: references/suni/S1_S2/suni_S2_A_front_crop.png
- costume: references/suni/S1_S2/suni_S2_A_side_crop.png
- costume: references/suni/S1_S2/suni_S2_A_back_crop.png

**Positive**
```
Close-up of a hand in a black fingerless glove writing with a pen in a small notebook, the page blank with only faint ruled lines, wet black jacket sleeve with a harness strap at the wrist. Flickering blue siren light entering through gaps between shipping containers, blue-black palette, 16:9 cinematic, shallow depth of field, film grain.
```
**Negative**
```
text, subtitles, watermark, logo, caption, cartoon, illustration, 3d render look, plastic skin, beauty filter, extra fingers, deformed hands, different person, mirrored image, flipped image, eyebrow scar, scar on face, handwriting, readable letters, numbers, digits, timestamp
```
**움직임(Wan 영상용)**: 펜이 줄 위를 천천히 움직임. 사이렌 청색 불빛이 컨테이너 틈으로 깜빡임. 글자는 생성하지 않음

**합성 금지/별도**: 수첩 'HNL…', 시각 '00:25'

**생성 후 확인**: 쓰는 손 콘티 미지정 [제안: 오른손]

## 컷 43 (4.0초)
**참조 입력**
- leader_face: references/leader_v2/leader_v2_front_crop.png
- leader_face: references/leader_v2/leader_v2_three_quarter_crop.png
- leader_face: references/leader_v2/leader_v2_right_side_crop.png
- leader_hand: references/leader_v2/leader_v2_hand_emergency_cut43_crop.png
- leader_costume: references/leader_v2/leader_v2_fullbody_night_crop.png

**Positive**
```
Low-angle medium shot in rain at night with flickering blue-red siren light. A man in a long black coat walks into frame through puddles and stops, his gaze moving to Korean man in his late 30s to 40s, square angular jaw, thick low nearly straight eyebrows, small heavy-lidded dark brown eyes, large prominent ears, short cropped black hair, rough natural skin with pores and rain moisture, no scars, no facial marks, who wears a dark olive tactical work jacket wet from rain; the leader's right hand has the fingers bound with gauze and an improvised splint, a tactical terminal beside it. Six men in dark tactical gear stand behind him, all six clearly countable. Blue-black palette with flickering blue-red light, 16:9 cinematic, film grain.
```
**Negative**
```
text, subtitles, watermark, logo, caption, cartoon, illustration, 3d render look, plastic skin, beauty filter, extra fingers, deformed hands, different person, mirrored image, flipped image, eyebrow scar, scar on face, five men, seven men, fewer than six men, formal white splint, eyebrow scar, subtitle, timestamp
```
**움직임(Wan 영상용)**: 정진모 등장 후 시선을 따라 리더의 손과 단말기로 푸시인. 사이렌 점멸, 빗줄기, 물웅덩이 구둣발

**합성 금지/별도**: 자막 '00:55'

**생성 후 확인**: 리더 응급 고정=오른손

## 컷 50 (2.5초)
**참조 입력**
- face: references/suni/base/suni_front_face_crop.png
- face: references/suni/base/suni_three_quarter_face_crop.png
- face: references/suni/base/suni_right_side_face_crop.png
- expression: references/suni/base/suni_expr_01_neutral_crop.png
- expression: references/suni/base/suni_expr_02_determined_crop.png
- costume: references/suni/S3_S4/suni_S3_B_front_crop.png
- costume: references/suni/S3_S4/suni_S3_B_side_crop.png
- costume: references/suni/S3_S4/suni_S3_B_back_crop.png

**Positive**
```
photorealistic cinematic photograph of a Korean woman in her 30s, calm slightly long dark brown eyes with faint shadow under the eyes, straight nose, thin lips, natural skin texture with visible pores and rain moisture, dark brown hair pulled back into a low ponytail with loose strands falling around her face, no scars, no piercings, no glasses. Fixed close-up, chest-up, wearing a short navy zip jacket open over a light grey top, black cargo pants, black lace-up combat boots. Her eyes show very slight relief, no smile. Her right hand rests briefly on her left chest pocket of the jacket, over a pocket watch that stays inside the pocket and is not visible. No holster visible. Bright cold blue-white daytime corridor light, 16:9 cinematic, shallow depth of field, fine film grain.
```
**Negative**
```
text, subtitles, watermark, logo, caption, cartoon, illustration, 3d render look, plastic skin, beauty filter, extra fingers, deformed hands, different person, mirrored image, flipped image, eyebrow scar, scar on face, smile, pocket watch visible, pocket watch worn as a necklace, chain visible, holster, gun, bandage visible, wound
```
**움직임(Wan 영상용)**: 카메라 고정. 손이 주머니 위에 잠깐 놓였다 내려감. 표정은 눈빛만 아주 약하게 변함

**합성 금지/별도**: 없음

**생성 후 확인**: 붕대=왼쪽 옆구리(비노출). 권총집=오른쪽 허리(상반신에서 비노출). 손의 좌우는 제안

## 컷 60 (5.0초)
**참조 입력**
- costume: references/suni/S3_S4/suni_S3_B_front_crop.png
- costume: references/suni/S3_S4/suni_S3_B_side_crop.png
- costume: references/suni/S3_S4/suni_S3_B_back_crop.png

**Positive**
```
Close-up on a desk in a cold blue-grey office: a police badge being set down, then a handgun, then a bare right hand in a navy jacket cuff releasing it. No holster visible. Cold blue-grey palette, daytime office light, 16:9 cinematic, shallow depth of field, film grain.
```
**Negative**
```
text, subtitles, watermark, logo, caption, cartoon, illustration, 3d render look, plastic skin, beauty filter, extra fingers, deformed hands, different person, mirrored image, flipped image, eyebrow scar, scar on face, holster, finger on trigger, pointing the gun, readable badge text, readable numbers
```
**움직임(Wan 영상용)**: 배지가 책상에 닿는 순간을 강조(소리 '딸깍'은 별도), 이어서 권총을 내려놓는 손. 새 사건 없음, 이후 수니 비무장

**합성 금지/별도**: 없음

**생성 후 확인**: 손 좌우 콘티 미지정 [제안: 오른손, 총기는 오른손 권총]. 권총집은 프레임 밖(비노출)

## 컷 64 (2.0초)
**참조 입력**
- leader_hand: references/leader_v2/leader_v2_hand_splint_cut64_crop.png
- leader_costume: references/leader_v2/leader_v2_fullbody_arrest_team_crop.png

**Positive**
```
Short tilt-up in a dark apartment entrance seen with a slight door-monitor fisheye feel: starting on a man's right hand wrapped in a clean white rigid splint with a black velcro band, tilting up along black police special-support tactical gear and vest to the chest, face out of frame. Darkness with a cold blue-white glow, 16:9 cinematic, film grain.
```
**Negative**
```
text, subtitles, watermark, logo, caption, cartoon, illustration, 3d render look, plastic skin, beauty filter, extra fingers, deformed hands, different person, mirrored image, flipped image, eyebrow scar, scar on face, left hand splint, bandage and improvised stick splint, face visible, helmet visor face visible
```
**움직임(Wan 영상용)**: 손에서 몸으로 짧게 틸트업(약 2초). 얼굴은 프레임 위로 나가기 전에 멈춤

**합성 금지/별도**: 없음

**생성 후 확인**: 정식 부목=오른손(응급 고정과 구분). 반전 금지

## 컷 65 (2.5초)
**참조 입력**
- face: references/suni/base/suni_front_face_crop.png
- face: references/suni/base/suni_three_quarter_face_crop.png
- face: references/suni/base/suni_right_side_face_crop.png
- expression: references/suni/base/suni_expr_04_surprised_crop.png
- costume: references/suni/S3_S4/suni_S4_C_front_crop.png
- costume: references/suni/S3_S4/suni_S4_C_side_crop.png
- costume: references/suni/S3_S4/suni_S4_C_back_crop.png
- leader_face: references/leader_v2/leader_v2_front_crop.png
- leader_face: references/leader_v2/leader_v2_three_quarter_crop.png
- leader_face: references/leader_v2/leader_v2_right_side_crop.png
- leader_costume: references/leader_v2/leader_v2_fullbody_arrest_team_crop.png
- leader_lighting: references/leader_v2/leader_v2_cut38_lighting_crop.png
- leader_lighting: references/leader_v2/leader_v2_cut65_lighting_crop.png

**Positive**
```
(Beat 1) photorealistic cinematic photograph of a Korean woman in her 30s, calm slightly long dark brown eyes with faint shadow under the eyes, straight nose, thin lips, natural skin texture with visible pores and rain moisture, dark brown hair pulled back into a low ponytail with loose strands falling around her face, no scars, no piercings, no glasses, close-up of her eyes widening in a dark entryway lit only by a cold blue-white phone glow, wearing a cream long-sleeve top with two black shoulder straps over the shoulders, black cargo pants, no jacket. (Beat 2, under one second) flash of Korean man in his late 30s to 40s, square angular jaw, thick low nearly straight eyebrows, small heavy-lidded dark brown eyes, large prominent ears, short cropped black hair, rough natural skin with pores and rain moisture, no scars, no facial marks in rain with forklift headlight glow. (Beat 3) the same man's face seen on a door intercom monitor in fisheye, in black police special-support gear and helmet with goggles raised on top, ears partly hidden by the helmet. Darkness with cold blue-white light, 16:9 cinematic, film grain.
```
**Negative**
```
text, subtitles, watermark, logo, caption, cartoon, illustration, 3d render look, plastic skin, beauty filter, extra fingers, deformed hands, different person, mirrored image, flipped image, eyebrow scar, scar on face, different man in the flash and on the monitor, eyebrow scar, face turned away
```
**움직임(Wan 영상용)**: 비트 ①약 1.0초 ②1초 미만(약 0.5~0.8초) ③약 1.0초. 비트별로 따로 생성해 이어 붙이는 방식 제안(11월 환경에서 검증)

**합성 금지/별도**: 없음

**생성 후 확인**: 서수니 상처는 비노출. 리더 얼굴은 반전 금지

## 컷 68 (3.0초)
**참조 입력**
- face: references/suni/base/suni_front_face_crop.png
- face: references/suni/base/suni_three_quarter_face_crop.png
- face: references/suni/base/suni_right_side_face_crop.png
- expression: references/suni/base/suni_expr_04_surprised_crop.png
- expression: references/suni/base/suni_expr_03_tense_crop.png
- costume: references/suni/S3_S4/suni_S4_C_front_crop.png
- costume: references/suni/S3_S4/suni_S4_C_side_crop.png
- costume: references/suni/S3_S4/suni_S4_C_back_crop.png

**Positive**
```
photorealistic cinematic photograph of a Korean woman in her 30s, calm slightly long dark brown eyes with faint shadow under the eyes, straight nose, thin lips, natural skin texture with visible pores and rain moisture, dark brown hair pulled back into a low ponytail with loose strands falling around her face, no scars, no piercings, no glasses. Fixed close-up in a dark entryway, face lit only by a cold blue-white phone glow from below frame, wearing a cream long-sleeve top with two black shoulder straps over the shoulders, black cargo pants, no jacket. Her expression is startled and wary, eyes slightly wide, lips just parted as she speaks. 16:9 cinematic, shallow depth of field, fine film grain.
```
**Negative**
```
text, subtitles, watermark, logo, caption, cartoon, illustration, 3d render look, plastic skin, beauty filter, extra fingers, deformed hands, different person, mirrored image, flipped image, eyebrow scar, scar on face, smile, jacket, holster, phone screen text, voice from the phone
```
**움직임(Wan 영상용)**: 카메라 고정. 놀람이 경계로 굳어짐. 대사 '전원 껐는데.'는 음성 별도(입 모양은 11월에 맞춤). 대사 후 정적

**합성 금지/별도**: 없음

**생성 후 확인**: 부상 비노출. 얼굴 반전 금지

