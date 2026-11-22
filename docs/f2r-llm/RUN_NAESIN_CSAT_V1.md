# 내신·수능형 출제 모델 v1 — 데이터·평가 세트와 GPU 실행 안내

작성: 2026-11-22 · 요청: GPU 학습 강화 요청서(차원연산4, 2026-11-22) 2장 · 받는 곳: 차원연산4(GPU 실행)

이 문서대로 하면 저장소만으로 학습 데이터와 평가 세트를 다시 만들고, v0(학습 전) → QLoRA v1 → 평가까지 돌릴 수 있다.
학습용 JSONL에는 교과서·평가원·사설 모의고사 지문이 들어가므로 **저장소에 커밋하지 않는다**(`training/.gitignore`). GPU 서버에서 다시 만든다.

## 1. 데이터 출처와 이용 판단

| 트랙 | 출처(저장소 경로) | 문항 | 학습 | 평가 | 비고 |
|---|---|---|---|---|---|
| 내신 | 압구정중2 2학기 기말 예상문제 16회(V1116) | 730 | O | - | F2R 제작, 정답표·해설 대조 완료. 문항은 그림 → OCR |
| 내신 | 신구중2 2학기 기말 예상문제 11회(V1120·V1121) | 440 | O | - | F2R 제작, 다른 세션 검수 |
| 내신 | 압구정중1 2학기 기말 예상문제 7회(V1127·V1128) | 280 | - | O | **학교 통째로 평가용**(다른 교과서·다른 출제 교사) |
| 수능형 | 예비고1 2부 고1 실전 독해 변형 10회(`prehigh-part2`) | 280 | O | - | 사설 모의고사 18~45번을 F2R이 지문·선택지 변형, 해설 새로 씀(2차 감수). OCR |
| 수능형 | 평가원 2026학년도 수능(2025-11-13)·2027학년도 6월 모평(`fixtures/csat_day1`) | 38 | O | - | 평가원 원문. 생성물이 원문을 베끼는지 겹침 지표로 잰다 |
| 수능형 | 2027학년도 9월 모평(2026-09-03) | 19 | - | O | 날짜 기준 평가용 |
| 수능형 | 2027학년도 수능(2026-11-19, `static/img/csat/2027-en`) 18~45번 | 최대 28 | - | O | 평가원 가답안 + F2R 해설. OCR |
| 수능형 | 9월 전국연합 고1·2 변형(`backend/private-reference`) | 498 중 표본 | **X** | O(풀이만) | 출처 PDF(외부 자료로 보임)의 이용 권한 미확인 → 학습 금지 |
| 문법 | 예비고1 문법 20단원(`pre-high-english/grammar`) | 600 | O | - | F2R 제작 |
| SAT | TestPrep SAT 미니 세트(V1113·V1114) | 60 | O | - | F2R 제작(College Board 문항 아님) |
| iBT | `training/export/sources/ibt_f2r_original.json` | 34 | O | - | 이번에 F2R이 새로 씀. ETS 문항·지문 미사용, 공개된 2026 시험 구성만 참고 |

- iBT: ETS 사이트 공개 자료(Free Practice Test 등)는 ETS 이용약관상 개인·비상업용이고 수정도 금지되어 받지 않았다. 듣기·말하기는 음성이 필요해 이번에 넣지 않았다.
- 예비고1 어휘(단어 뜻 고르기)는 출제 방식 학습에 도움이 적어 넣지 않았다.
- 내신위키 Generated Question Store는 현재 비어 있다(압구정중1·2, 신구중2 2026 2학기 기말 조회 결과 0건). 문항이 쌓이면 `backend/dataset_export.py`로 내보내 합친다.

## 2. 나누기 규칙(누출 방지)

`--cutoff "csat=2026-09-01,naesin=group,grammar=group,sat=group,ibt=group"`

- 수능형: 2026-09-01 이전 시험만 학습, 이후(9월 모평·9월 전국연합·2027 수능)는 평가.
- 내신: 날짜가 모두 같은 2학기 기말이라 날짜로 나눌 수 없다. 학교로 나눈다(압구정중1 = 평가).
- 모든 트랙: `source_group`이 겹치면 중단, 평가 지문의 8-gram이 학습 지문과 30% 넘게 겹쳐도 중단(`training/common.check_split`).

## 3. 평가 세트

| 파일 | 내용 | 채점 |
|---|---|---|
| `eval_generate.jsonl` | 내신 압구정중1 생성 + 수능형(9월 모평·2027 수능) 생성 | 형식 준수율·근거 일치율·**원문 겹침**(발문·선택지가 원래 문항을 베꼈는지, 지문을 그대로 옮겼는지) 자동, 정답률은 교사 검수 후 |
| `eval_solve.jsonl` | 같은 문항 풀이 + 9월 전국연합 변형 표본(수능형 합계 200문항) | 정답률 자동(교사 확인 정답·평가원 정답·출처 정답) |

- 원문 겹침: `original_question_overlap`(새 발문·선택지 vs 원래 문항), `source_passage_overlap`(새 지문 vs 근거 지문). 8-gram 비율, 0.5 이상을 베낀 것으로 센다. 내신은 지문 재사용이 정상이라 `source_passage_overlap`이 높게 나오는 게 맞고, 수능 변형은 낮아야 한다.

## 4. 실행 순서(GPU 서버)

```bash
git clone https://github.com/woori73/feel2real-buildit && cd feel2real-buildit
sudo apt-get install -y tesseract-ocr tesseract-ocr-kor      # OCR (CPU, 2코어 약 20분)
python3 -m pip install -r training/requirements-eval.txt pillow
python3 -m training.export.ocr_images --root src/main/resources/static/naesin \
  --root src/main/resources/prehigh-part2 --root src/main/resources/static/img/csat/2027-en --workers 8
python3 -m training.export.build_dataset --out training/data   # train.jsonl / eval_*.jsonl / export_summary.json
python3 -m unittest discover -s training/tests

CUT="csat=2026-09-01,naesin=group,grammar=group,sat=group,ibt=group"
# 1) 시험 학습(200행): 실행 경로·loss 확인용
head -n 200 training/data/train.jsonl > training/data/train_pilot.jsonl
python3 -m training.qlora.train --train training/data/train_pilot.jsonl --eval training/data/eval.jsonl \
  --cutoff "$CUT" --revision "$F2R_BASE_REV" --output training/outputs/pilot --dry-run
python3 -m pip install -r training/requirements.txt
python3 -m training.qlora.train --train training/data/train_pilot.jsonl --eval training/data/eval.jsonl \
  --cutoff "$CUT" --revision "$F2R_BASE_REV" --output training/outputs/pilot --allow-download
# 2) v0(학습 전) 생성·평가: llama-server로 베이스 모델을 띄운 뒤
python3 -m training.v0.generate --input training/data/eval_generate.jsonl --output training/outputs/v0_gen.jsonl \
  --model Qwen/Qwen2.5-7B-Instruct --model-revision "$F2R_BASE_REV"
python3 -m training.v0.generate --input training/data/eval_solve.jsonl --output training/outputs/v0_solve.jsonl \
  --model Qwen/Qwen2.5-7B-Instruct --model-revision "$F2R_BASE_REV"
python3 -m training.eval.run --gold training/data/eval_generate.jsonl --predictions training/outputs/v0_gen.jsonl \
  --output training/eval/results/v0-naesin-csat-gen.json --model-version "$F2R_BASE_REV" --retrieval-mode hybrid
python3 -m training.eval.run --gold training/data/eval_solve.jsonl --predictions training/outputs/v0_solve.jsonl \
  --output training/eval/results/v0-naesin-csat-solve.json --model-version "$F2R_BASE_REV" --retrieval-mode hybrid
# 3) 본 학습(전체) → GGUF → v1 평가(2와 같은 명령, 모델만 바꿈)
python3 -m training.qlora.train --train training/data/train.jsonl --eval training/data/eval.jsonl \
  --cutoff "$CUT" --revision "$F2R_BASE_REV" --output training/outputs/v1
python3 -m training.gguf.export --model Qwen/Qwen2.5-7B-Instruct --revision "$F2R_BASE_REV" \
  --adapter training/outputs/v1/adapter --llama-cpp-dir /opt/llama.cpp --llama-cpp-revision "$F2R_LLAMA_REV" \
  --output training/outputs/v1-gguf
```

- 결과 JSON만 `training/eval/results/`에 커밋하고 [EVAL_RESULTS.md](EVAL_RESULTS.md)에 한 줄씩 적는다. 체크포인트·어댑터·GGUF는 매번 내려받아 보관하고, 끝나면 서버를 삭제한다.
- 상용 AI 점수 칸: 같은 `eval_*.jsonl`을 상용 AI로 생성한 예측 파일로 같은 명령을 돌려 채운다.

## 5. 예상 GPU 시간(L40S 48GB 1장 기준, 추정)

| 단계 | 시간 |
|---|---|
| 환경 설치·모델 받기 | 0.5시간 |
| 시험 학습(200행) | 0.2시간 |
| v0 생성(평가 약 600행, 행당 출력 약 600토큰) | 1.5~2시간 |
| 본 학습(약 2,000행, 1 epoch) | 0.5~1시간 |
| GGUF 변환 | 0.5시간 |
| v1 생성·평가 | 1.5~2시간 |
| 합계 | **약 5~6시간** |

대여 전에 콘솔의 시간당 요금 × 6시간을 한 줄로 대표께 알리고 시작한다(요청서 0장 5항). OCR은 GPU가 필요 없으므로 대여 전에 학원 PC나 CPU 서버에서 미리 해 두면 GPU 시간이 줄어든다.

## 6. 데이터 품질 한계(학습 전에 알아 둘 것)

- 내신·고1 변형 문항은 그림을 OCR로 읽어서 밑줄·네모·<보기> 상자 표시가 없어지고, 한글 선택지에 오타가 섞인다. 선택지 5개를 못 나눈 문항은 학습에서 뺐다(`export_summary.json`의 `선택지 인식 실패`). 글자 원본(HTML)을 찾으면 OCR 대신 그것으로 바꾸는 것이 가장 큰 개선이다.
- 내신 출제 근거인 학습지 필기본은 저장소에 없다. 어휘·발음 문항처럼 지문이 없는 문항은 근거 청크 없이 학습한다.
- 평가원 기출 글자본(fixtures)은 띄어쓰기 오류가 일부 있다.
