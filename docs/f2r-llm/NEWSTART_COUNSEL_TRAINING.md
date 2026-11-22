# 지피지기 상담 모델 GPU 사용법

작업 브랜치는 `feat/newstart-counsel-eval`이다. 입시상담과 같은 Qwen2.5-7B-Instruct 고정 리비전, QLoRA 학습기와 생성기를 사용한다. 저장소 루트에서 실행한다. 개인 상담 자료는 검수 ZIP을 GPU 서버의 비공개 경로에 풀어 사용한다.

## 준비와 대표 검수

평가 150행과 시험 학습 400행(실사례 100건)을 먼저 점검한다. 다음 명령은 GPU와 승인 표시 없이 실행할 수 있다.

```bash
export NEWSTART_EVAL=/private/newstart/eval.jsonl
export NEWSTART_APPROVED=/private/newstart/train_review.jsonl
python3 -m training.newstart.build_dataset --mode trial --eval "$NEWSTART_EVAL" --train "$NEWSTART_APPROVED"
python3 -m unittest discover -s training/tests
```

대표가 평가 30행과 학습 40행을 표본 검수하고 답변 길이를 결정한 뒤 확정 파일을 사용한다. 답변 길이 변경이 있으면 모범 답변에도 반영한다. `NEWSTART_REVIEW_REFERENCE`에는 두 파일에 적용되는 실제 승인 기록을 넣는다. 개별 행에 이미 검수·학습 승인이 기록돼 있으면 이 변수는 생략할 수 있다. 표본 검수는 전 행의 개별 검수로 기록되지 않는다.

## GPU 실행

GPU 서버에 입시상담과 같은 torch, transformers, peft, accelerate, bitsandbytes, huggingface_hub 환경이 필요하다. 시험에서 최초 한 번 모델을 캐시에 받는다.

```bash
bash training/newstart/run_gpu.sh trial
# 본 학습용 800쌍 이상, 서로 다른 실제 원문 200건 이상의 확정본으로 전환
export NEWSTART_APPROVED=/private/newstart/train_main.jsonl
# 본 학습 파일에 적용되는 승인 기록으로 갱신한 뒤 실행
bash training/newstart/run_gpu.sh main
```

시험은 400행 전체를 사용한다. 본 학습에 앞서 같은 평가 파일에서 베이스와 시험 어댑터 결과를 비교한다. 생성 배치는 4이고 토큰 상한은 `NEWSTART_MAX_NEW_TOKENS`로 조절한다. 기본 768토큰은 생성 중단 상한이며 상담 답변의 권장 글자 수는 대표 검수에서 정한다.

결과는 `training/outputs/newstart/` 아래에 저장한다. 마지막 출력 경로가 해당 단계의 점수표이며, 같은 폴더에 예측 JSONL과 어댑터가 있다. 동일 입력으로 같은 명령을 다시 실행하면 완료된 모델과 예측을 재사용한다. 중단된 단계는 새 임시 경로에서 재시도한다. 입력 데이터·설정·생성 상한이 바뀌면 관련 결과 경로가 달라진다.

`counsel_eval.py`는 모든 문항의 출처 집합, 문구 기반 사실·자료 부족·보장 거절·위험 대응, 근거에 없는 수치, 평균 답변 길이를 집계한다. 자동 검사는 의역이나 문장 의미를 완전히 판정할 수 없으므로 대표 표본 검수와 함께 해석한다.

```bash
python3 -m training.newstart.counsel_eval --gold "$NEWSTART_EVAL" --predictions /private/predictions.jsonl --model-version trial --output /private/trial.score.json
```

## 운영 진단 2026-11-22

|질문|관찰한 답변|근거 또는 답변 문제|
|---|---|---|
|보증 대위변제 전후 확인처|은행·보증기관 순서 제시|본문 없는 색인, 중복 도메인 FAQ|
|주택구입용 주담대와 사업자 신용대출 구분|계약서 용도와 담보 확인 안내|직접 맞는 사례 대신 차량담보 사례가 길게 제시됨|
|신청 전 감면율 확정|보장 거절, 당시 사례 수치 명시|관련성이 낮은 다른 제도 자료 포함|
|개인 약정일|확정 불가, 접수 경과 확인 안내|불필요한 추심 대응 안내도 생성|
|월세 차감 소명|확정 서류 목록 미확인, 증빙 정리 안내|본문 없는 색인 노출|

본문 미확보 색인은 5/5, 중복 도메인 링크는 4/5. 이 브랜치에서 상담용 근거 조립 단계의 미확보 색인을 제외하고 출력 FAQ URL의 동일 도메인 중복을 교정한다. PR 승인·배포 후 동일 질문으로 재측정한다.
