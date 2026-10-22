# r5 캐릭터 시안 검토

정식 원작 대조는 하지 못했습니다. r5는 제작용 개정본이며 원작 검수 완료본이 아닙니다.

## 결과: 영화적 시안 이미지는 생성하지 못했습니다

지시서가 요구한 실사풍 시안은 코드로 그린 도식으로는 만들 수 없고, 이 환경에는 이미지 모델이나 GPU가 없습니다. 연결된 외부 이미지 생성 도구가 있는 것으로 보이지만 비용이 어떻게 부과되는지 확인할 수 없어 승인 전에는 쓰지 않았습니다. 빈 파일이나 임의 이미지로 대체하지 않았습니다.

| 파일 | 상태 |
|---|---|
| characters/suni/front.png | 미생성 |
| characters/suni/side.png | 미생성 |
| characters/suni/three_quarter.png | 미생성 |
| characters/suni/full_body.png | 미생성 |
| characters/suni/costume_A.png | 미생성 |
| characters/suni/costume_B.png | 미생성 |
| characters/suni/costume_C.png | 미생성 |
| characters/suni/expressions.png(구조 외 추가 요청 항목) | 미생성 |
| characters/leader/front.png | 미생성 |
| characters/leader/side.png | 미생성 |
| characters/leader/three_quarter.png | 미생성 |
| characters/leader/cut38_cut65_comparison.png (왼쪽 칸) | 미생성 |
| characters/leader/cut38_cut65_comparison.png (오른쪽 칸) | 미생성 |

## 대신 준비한 것
- `comfyui/character_prompts.json`: 파일별 영어 프롬프트, negative, 한국어 메모(서수니 11종, 리더 6종)
- `character_cut_requirements.json`: 새 시안이 필요한 컷 55개 목록(수니·리더 등장 컷, 필요한 각도, 의상 블록)
- 기존 r3·r4 도식은 참고 자료로만 남김(인물 구분·의상 연속성)

## 서수니 요구사항 대응
| 요구 | 처리 |
|---|---|
| 한국인 여성, 자연스러운 얼굴 구조 | 프롬프트에 반영 |
| 33세·168cm | 정본으로 확정하지 않음. 프롬프트는 '30대'로만 표기 |
| 정면·측면·3/4·전신·상반신 | 프롬프트 작성, 이미지 미생성 |
| 표정 시트 | 프롬프트 작성(expressions), 이미지 미생성 |
| 의상 A/B/C | A 야간 비 / B 낮 복도 / C 점퍼 벗음, 변경 추가 없음 |

## 리더 요구사항 대응
| 요구 | 처리 |
|---|---|
| 컷 38·65 동일 인물 | 같은 얼굴 요소(각진 턱, 굵고 낮은 눈썹, 큰 귀, 얇은 입, 짧은 머리·올라간 이마 선)를 프롬프트에 고정 |
| 야간/주간 비교 | 컷 38 야간·용역 복장, 컷 65 주간·체포조 복장·바이저 올림 |
| 눈썹 흉터 | 추가하지 않음, negative에 포함 |
| 오른손 | 컷 43 응급 고정(붕대·간이 부목)과 컷 64 정식 부목(흰 부목+검은 밴드)을 구분 |

## 같은 인물 확인 방법 (이미지가 생기면)
정면·측면·3/4 각도와 컷 38·65 비교 이미지에서 눈썹 형태, 귀, 턱선, 입, 머리 형태가 같은지 사람이 나란히 비교한다. 합격 기준 값은 시험 설계 때 정한다.

## 세븐
이번 범위에서 제외. 컷 67 음성 없음. 별도 디자인이 필요하면 통합 설정 바이블 확보 후 진행.