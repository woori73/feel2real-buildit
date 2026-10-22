# r6 → r7 변경 사항

| 구분 | r6 | r7 |
|---|---|---|
| 리더 | 명세·프롬프트만, 이미지 없음 | v2 시트 승인 등록(`CH_LEADER`), 크롭 9종, 38·65 동일 인물 비교표 |
| 서수니 | 승인 시트 등록, 좌측면·전신 없음 | 얼굴 변경 없음. 좌측면·전신·부상 상태 후보 명세 완성(미생성) |
| 컷 맵 | 서수니 51 / 리더 13(명세) | 리더 13컷에 승인 참조 연결, POV 규칙(24·58) |
| 연속성 | 33항목(r5 21 + r6 12) 통과 | 43항목(+r7 10) 통과, 모두 내부 정합 기준 |
| 핸드오프 | 서수니 | 서수니+리더, 리더 검증 기준(V-L1~L4) |

## 신규 파일 (deliver_r7)
LEADER_APPROVED_REFERENCE, LEADER_38_65_COMPARISON_r7, SUNI_SIDE_FULLBODY_SPEC_r7, CHARACTER_BIBLE_r7, CHARACTER_CUT_MAP_r7.json, CONTINUITY_r7.json, COMFYUI_HANDOFF_r7, r7_APPROVALS, r7_FILE_LIST, 본 문서, `references/`(서수니 시트·크롭 12, 리더 v2 시트·크롭 9·비교 파생 1, 대체 v1), `preserved/`

## 변경하지 않은 것
r14 원본, deliver~deliver_r6 전체와 러프 패널 68장(전후 MD5는 `preserved/preservation_check_r7.md`), 컷 14·15·42·45·52·59·67 결정, 서수니 승인 얼굴, 모든 컷 `pass_confirmed=false`.

## 이미지 생성 여부
Claude가 만든 이미지는 없습니다. 모든 크롭은 사용자가 승인한 시트에서 잘라낸 것이고 비교 이미지는 크롭을 나란히 놓은 파생본입니다.
