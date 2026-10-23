# r9 컷별 캐릭터 참조 매핑

> 이미지·영상 생성 0건(이번 단계와 전체 프로젝트에서 Claude가 생성한 것 없음). 모든 컷 pass_confirmed=false, 정식 원작 대조 0/68. 새 사건·대사 없음. [제안]은 승인 전 미확정.

경로는 `deliver_r9/` 기준. 모든 파일은 승인 원본과 SHA-256 일치(`references/REFERENCE_SHA256.md`). 후보(미승인) 이미지는 연결하지 않았습니다.

| 컷 | 얼굴 | 표정(참고) | 의상 | 부상 | 리더 얼굴 | 리더 손 | 리더 의상 | 조명 비교 |
|---|---|---|---|---|---|---|---|---|
| 5 | suni_front_face_crop.png<br>suni_three_quarter_face_crop.png<br>suni_right_side_face_crop.png | suni_expr_02_determined_crop.png | suni_S2_A_front_crop.png<br>suni_S2_A_side_crop.png<br>suni_S2_A_back_crop.png | — | — | — | — | — |
| 22 | — | — | suni_S2_A_front_crop.png<br>suni_S2_A_side_crop.png<br>suni_S2_A_back_crop.png | suni_S5_wound_front_crop.png<br>suni_S5_wound_side_crop.png<br>suni_S5_wound_back_crop.png<br>suni_S5_closeup_front_crop.png<br>suni_S5_closeup_jacket_crop.png<br>suni_S5_closeup_position_crop.png | — | — | — | — |
| 38 | suni_front_face_crop.png<br>suni_three_quarter_face_crop.png<br>suni_right_side_face_crop.png | — | suni_S2_A_front_crop.png<br>suni_S2_A_side_crop.png<br>suni_S2_A_back_crop.png | suni_S5_wound_front_crop.png<br>suni_S5_wound_side_crop.png<br>suni_S5_wound_back_crop.png<br>suni_S5_closeup_front_crop.png<br>suni_S5_closeup_jacket_crop.png<br>suni_S5_closeup_position_crop.png | leader_v2_front_crop.png<br>leader_v2_three_quarter_crop.png<br>leader_v2_right_side_crop.png | — | leader_v2_fullbody_night_crop.png | leader_v2_cut38_lighting_crop.png |
| 40 | — | — | suni_S2_A_front_crop.png<br>suni_S2_A_side_crop.png<br>suni_S2_A_back_crop.png | — | — | — | — | — |
| 43 | — | — | — | — | leader_v2_front_crop.png<br>leader_v2_three_quarter_crop.png<br>leader_v2_right_side_crop.png | leader_v2_hand_emergency_cut43_crop.png | leader_v2_fullbody_night_crop.png | — |
| 50 | suni_front_face_crop.png<br>suni_three_quarter_face_crop.png<br>suni_right_side_face_crop.png | suni_expr_01_neutral_crop.png<br>suni_expr_02_determined_crop.png | suni_S3_B_front_crop.png<br>suni_S3_B_side_crop.png<br>suni_S3_B_back_crop.png | — | — | — | — | — |
| 60 | — | — | suni_S3_B_front_crop.png<br>suni_S3_B_side_crop.png<br>suni_S3_B_back_crop.png | — | — | — | — | — |
| 64 | — | — | — | — | — | leader_v2_hand_splint_cut64_crop.png | leader_v2_fullbody_arrest_team_crop.png | — |
| 65 | suni_front_face_crop.png<br>suni_three_quarter_face_crop.png<br>suni_right_side_face_crop.png | suni_expr_04_surprised_crop.png | suni_S4_C_front_crop.png<br>suni_S4_C_side_crop.png<br>suni_S4_C_back_crop.png | — | leader_v2_front_crop.png<br>leader_v2_three_quarter_crop.png<br>leader_v2_right_side_crop.png | — | leader_v2_fullbody_arrest_team_crop.png | leader_v2_cut38_lighting_crop.png<br>leader_v2_cut65_lighting_crop.png |
| 68 | suni_front_face_crop.png<br>suni_three_quarter_face_crop.png<br>suni_right_side_face_crop.png | suni_expr_04_surprised_crop.png<br>suni_expr_03_tense_crop.png | suni_S4_C_front_crop.png<br>suni_S4_C_side_crop.png<br>suni_S4_C_back_crop.png | — | — | — | — | — |

## 참조가 없는 인물
- 정진모(컷 43), 용역 6명(컷 43): 승인 디자인 없음 → 승인 필요
- 컷 38 서수니·리더 동시 프레임: 두 인물 참조를 한 번에 넣는 방식은 11월 환경에서 검증
- 컷 22·40·60: 얼굴 노출 없음 → 얼굴 참조 미연결(손·옆구리·소품 연속성만 확인)
- 컷 5: 눈만 보여 얼굴 전체 동일성 검증 제한

## 규칙
1. 좌우 반전 금지. 부상은 서수니의 왼쪽 옆구리, 리더 부목은 오른손
2. 서수니 얼굴 기준은 항상 원본 승인 시트(S1~S6 크롭은 의상·부상 위치 참조 전용)
3. S5·S6 시트는 재킷이 열린 상태 → 개폐는 컷 규칙이 우선
