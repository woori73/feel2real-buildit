# T07 내보내기와 T08 평가의 파일 계약

이 문서는 T08~T12가 사용하는 `f2r-eval-v1` 파일 계약입니다. T07 내보내기는 클로드 담당이며 아직 연결되지 않았습니다. DB를 직접 읽지 않습니다. JSONL 원본은 `training/data/` 등 ignore 경로에 두고, 공개 저장소에는 집계 결과 JSON만 올립니다.

## 입력과 트레이닝

모든 행에 `id`(파일 안에서 유일한 문자열), `instruction`(출제 원칙), `input`(문자열 또는 객체), `source_chunks`(id·text 배열), `metadata`가 필요합니다. v0는 `instruction`, `input`, `source_chunks`만 프롬프트에 넣습니다. 정답·해설은 input이나 source_chunks에 섞지 말고 별도 output/gold에 둡니다.

`metadata` 필드:

| 키 | 의미 |
|---|---|
| domain | `education`만 허용 |
| deidentified | 내보내기 담당자가 식별정보 제거를 확인한 `true` |
| source_group | 같은 원본 지문·기출 계열을 묶는 ID. 변형문제끼리도 같은 값 |
| exam_date | 평가 대상 시험의 ISO 날짜 `YYYY-MM-DD` |
| teacher_verified | 학습·평가 정답에 대한 교사 확인이 끝났을 때만 `true` |
| training_allowed | 학습 이용 범위가 확인된 데이터만 `true` |

이 표시는 신뢰할 수 있는 내보내기 단계가 기록해야 합니다. 도구는 개인정보를 스스로 탐지·익명화하지 않으며, 표시만으로 이용 권한이나 비식별성이 증명되지 않습니다. 채무상담 데이터는 입력 대상이 아닙니다.

트레이닝 행은 추가로 `output`이 필요합니다. 값은 JSON 문자열 또는 다음 구조의 객체입니다.

```json
{"question": {"type": "mc", "questionType": "grammar", "sourceText": "A is larger than B.", "stem": "Choose A.", "options": ["A", "B"], "answer": [1], "multi": false, "score": 1, "conditions": [], "answerFormat": "", "acceptedAnswers": [], "sampleAnswer": "", "gradingRule": ""}, "evidence_refs": [{"id": "s1", "quote": "larger"}]}
```

학습은 교사 확인·학습 동의를 모두 요구합니다. cutoff보다 이른 시험만 트레이닝에, cutoff 이후(당일 포함)만 평가에 둡니다. id와 source_group이 겹치면 중단합니다. 같은 원문의 서로 다른 ID로 중복이 들어오지 않도록 T07에서 그룹을 지정해야 하며, 숨겨진 텍스트 중복을 이 도구가 자동으로 모두 찾는 것은 아닙니다. 근거 청크·태그 통계도 해당 시험 전에 이용 가능했던 자료만 내보내야 합니다.

## 평가 정답

평가 행은 `teacher_verified=true`와 `gold`를 요구합니다. 객관식은 `{"answer": [1]}`, 서답형은 `{"acceptedAnswers": ["Hello world", "허용한 다른 답"]}`입니다. 서답형은 유니코드 NFKC와 공백만 정규화하며 대소문자·구두점을 임의로 제거하지 않습니다. 허용 변형은 교사가 acceptedAnswers에 명시합니다.

500문항 holdout은 Trainer에 전달하지 않습니다. train.py는 날짜·그룹 누출 검사에만 사용합니다. 하이퍼파라미터 조정에는 별도의 개발 세트를 두고, 최종 테스트 세트는 튜닝에 반복 사용하지 않습니다.

## 생성과 외부 검증 결과

v0 결과 행은 id, question, evidence_refs, generator_model(정규 모델 ID), model_revision, usage(prompt_tokens·completion_tokens), latency_seconds를 기록합니다. source_chunks가 없으면 API를 호출하지 않고 abstained=true를 남깁니다. 모델 중단·비정상 JSON은 성공으로 위장하지 않고 명령이 실패합니다. 부분 파일은 별도 경로로 다시 생성하고, 평가에서는 누락 ID도 실패 분모에 포함합니다.

T05 검증 뒤 아래 필드를 추가할 수 있습니다.

```json
{"verification": {"model": "생성 모델과 다른 정규 모델 ID", "answer": [1]}, "auto_pass": true, "audit": {"selected": true, "correct": false}}
```

- verification.answer는 객관식 정수 배열 또는 서답형 답 문자열입니다. model은 버전/별칭이 아닌 정규 모델 ID여야 합니다. 같은 모델을 이름만 바꿔 독립 검증으로 표시하지 않습니다. 판정·모델 호출은 T05 담당입니다.
- auto_pass/audit는 모델 출력에서 가져오지 않고 검증기·교사가 기록합니다. 오딧 대기 상태는 correct를 비우고, 완료된 결과만 true/false로 기록합니다.
- 샘플 오딧 오답률의 분모는 **검수 완료된 자동 통과 샘플**입니다. 전체 자동 통과 수, 미검수 수를 별도 기록합니다. 샘플 비율·선정 방식과 신뢰 구간은 실증 보고서에 남기며, 작은 샘플에서 오답 0개여도 무오류를 주장하지 않습니다.
- 다른 모델의 재풀이 기록이 없으면 독립 풀이 지표는 null입니다. 토큰·지연 기록이 없을 때도 null이며 추정치로 채우지 않습니다.
- 근거 일치율은 id와 인용문이 자료에 실제 존재하는지 확인하는 **문자열 출처 검사**입니다. 의미적 타당성·정답의 유일성 증명은 별도 검수 대상입니다.
- 형식 준수는 기존 question_contract.validate_transform을 호출합니다. 의미·난이도·교육 효과는 이 계약 통과만으로 판정하지 않습니다.

## 비교 조건

모델과 검색 모드를 비교할 때 평가 파일 SHA256, 모델/가중치 버전, 프롬프트, 출력 한도, 컨텍스트 토큰 예산, 장비·동시성 조건을 함께 기록합니다. D·bm25·embedding·hybrid는 같은 조건에서 비교하며, 다른 gold 해시의 점수를 직접 비교하지 않습니다. 집계 JSON은 수동 검토 자료이며 자동 운영 전환·배포 게이트가 아닙니다.
