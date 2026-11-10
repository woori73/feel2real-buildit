# training/ — 자체 LLM 데이터·학습·평가·변환

계획: [docs/f2r-llm/PLAN.md](../docs/f2r-llm/PLAN.md) 4-6, 4-7 · 담당: [docs/f2r-llm/TASKS.md](../docs/f2r-llm/TASKS.md)

이 폴더는 **저장소 안에만 두고 서버에 배포하지 않는다.** 실행은 런유어AI GPU(L40S·H100 1장) 또는 학원 PC에서 한다.

## 폴더 구성

| 경로 | 내용 | 담당 |
|---|---|---|
| `export/` | 검증 통과 문항 → JSONL 내보내기, 평가 세트 날짜 분리 | 클로드(T07) |
| `v0/` | 학습 없는 v0 생성(베이스 모델 + 근거·출제 원칙) | GPT(T09) |
| `qlora/` | QLoRA 학습 스크립트·설정 | GPT(T10) |
| `gguf/` | LoRA 병합 → GGUF 변환 → 4비트 양자화 | GPT(T11) |
| `eval/` | 평가 하니스 실행, `eval/results/`에 결과 JSON | GPT(T08) |

T08~T12의 코드와 테스트를 추가했습니다. T07 내보내기·T05 검증은 클로드 담당으로 아직 연결되지 않았습니다. 입력 파일 계약은 [DATA_CONTRACT.md](DATA_CONTRACT.md)입니다. 운영 API·DB·GPU 작업자는 이 코드에서 호출하지 않습니다.

## 런유어AI 실행 순서
1. 대표님이 콘솔에서 GPU를 빌린다. **빌리기 전에 예상 시간·포인트를 확인받는다.**
2. 웹 터미널 또는 연결된 PC에서 접속한다(클라우드 코드 세션은 SSH가 막혀 있다).
3. 저장소를 받아 `training/` 스크립트를 실행한다.
4. 반납하면 환경이 초기화된다. LoRA 가중치·GGUF는 저장소 밖(런유어AI 저장소 또는 학원 PC)에 보관하고, 평가 결과 JSON만 저장소에 남긴 뒤 [EVAL_RESULTS.md](../docs/f2r-llm/EVAL_RESULTS.md)에 요약한다.

## 지킬 것
- 학생 이름 등 식별 정보는 내보내기 단계에서 지운다. 채무상담 데이터는 내보내지 않는다.
- 베이스 모델은 상업 이용 가능한 라이선스만 쓴다. 후보와 라이선스는 이 문서에 표로 남긴다(T12).
- 모델 가중치, 체크포인트, GGUF, 학습용 JSONL 원본은 커밋하지 않는다(`training/.gitignore`).

## 베이스 모델 후보와 라이선스 확인 T12

2026-11-08에 공식 모델 카드와 LICENSE를 확인했습니다. 채택 모델과 가중치 commit은 아직 확정하지 않았으며 한국어·영어 내신 성능은 측정 전입니다. 표는 공개 라이선스 확인이며 서비스·배포 적합성의 성능 보장이 아닙니다.

| 모델 | 크기 | 라이선스 | 상업 이용 | 한국어·영어 | 비고 |
|---|---|---|---|---|---|
| [Qwen2.5-7B-Instruct](https://huggingface.co/Qwen/Qwen2.5-7B-Instruct) | 약 7B | [Apache-2.0 원문](https://huggingface.co/Qwen/Qwen2.5-7B-Instruct/blob/main/LICENSE) | 조건 준수 시 가능 | F2R 이밸류에이션 필요 | 초기 학습 설정 후보. 40자리 모델 commit 고정 필요 |
| [Qwen3-8B](https://huggingface.co/Qwen/Qwen3-8B) | 약 8B | [Apache-2.0 원문](https://huggingface.co/Qwen/Qwen3-8B/blob/main/LICENSE) | 조건 준수 시 가능 | F2R 이밸류에이션 필요 | v0 비교 후보. thinking 모드·응답 마스크 확인 전 QLoRA 허용 목록에 추가하지 않음 |

배포 시 라이선스 사본·관련 고지를 보존하고 수정 사실을 표시하며, 원본에 NOTICE가 있으면 해당 고지도 반영합니다. 가중치·어댑터·토크나이저를 함께 배포할 때 각각의 출처와 고정 revision을 기록합니다. 교사 모델의 생성 결과를 트레이닝에 사용할 수 있는지는 해당 공급자의 약관을 별도로 확인합니다. 이름이 비슷한 다른 크기·배포본의 라이선스를 이 표로 대신하지 않습니다.

## 검증과 실행

저장소 루트에서 Python 3.10+로 실행합니다. 이 PR의 로컬 검증은 Python 3.12.14 / pydantic 2.13.4에서 GPU 없이 수행했습니다. 아래 GPU 의존성 범위는 아직 실장비 통합 검증 전이며, 실제 설치 버전은 매 실험 manifest의 packages에 남깁니다. 설치는 GPU 대여 시간·비용 승인을 받은 환경에서 하십시오.

```bash
python3 -m pip install -r training/requirements-eval.txt
python3 -m unittest discover -s training/tests -v
```

v0는 승인된 로컬 llama-server가 별도로 실행되어 있어야 합니다. 도구가 서버를 설치하거나 띄우지 않습니다. model-revision은 실제 사용하는 GGUF의 SHA256 또는 모델 commit으로 지정합니다. endpoint 기본값은 127.0.0.1이며 원격 주소는 --allow-remote를 명시해야 합니다. API 키가 필요한 경우 F2R_MODEL_API_KEY 환경변수만 사용하고 URL·명령행에 넣지 않습니다.

```bash
python3 -m training.v0.generate --input training/data/eval.jsonl --output training/outputs/v0.jsonl --model Qwen/Qwen2.5-7B-Instruct --model-revision "$F2R_BASE_REV"
python3 -m training.eval.run --gold training/data/eval.jsonl --predictions training/outputs/v0.jsonl --output training/eval/results/v0-summary.json --model-version "$F2R_BASE_REV" --retrieval-mode hybrid
```

v0는 학습하지 않은 베이스 모델의 근거 기반 생성입니다. 실제 호출이 실패하면 중단하고, 모의 문항·모의 레이턴시를 반환하지 않습니다. 기본 응답 한도는 2048토큰이며, 잘려 끝난 출력은 성공으로 취급하지 않습니다. 평가는 기존 문항 계약을 호출하고 집계 JSON만 출력합니다. `EVAL_RESULTS.md`는 사람이 이 JSON을 검토한 뒤 갱신합니다.

QLoRA는 CUDA 단일 GPU용입니다. NF4·이중 퀀타이제이션·LoRA all-linear를 사용하며 assistant 응답 토큰에만 loss를 적용합니다. 채팅 템플릿 prefix가 맞지 않으면 중단하고, 2048토큰을 넘는 행은 정답을 자르지 않고 제외하여 개수를 기록합니다. 작은 샘플에서는 실행 경로만 확인하고 1,500쌍 학습 실적이라고 기록하지 않습니다.

```bash
python3 -m training.qlora.train --train training/data/train.jsonl --eval training/data/eval.jsonl --cutoff 2026-12-01 --revision "$F2R_BASE_REV" --output training/outputs/v1 --dry-run
# 아래 명령은 GPU·포인트 승인 후 실행합니다.
python3 -m pip install -r training/requirements.txt
python3 -m training.qlora.train --train training/data/train.jsonl --eval training/data/eval.jsonl --cutoff 2026-12-01 --revision "$F2R_BASE_REV" --output training/outputs/v1
```

기본값은 이미 캐시된 고정 revision만 읽습니다. 필요한 파일을 미리 다운로드하여 보관하고, 승인한 환경에서 직접 다운로드할 경우에만 --allow-download를 추가합니다. 새 output 경로만 허용하므로 재실행 때는 다른 경로를 지정하십시오. 트레이닝 중 checkpoints와 최종 adapter를 영구 저장소로 별도 복사해야 하며, 이 스크립트는 비용·시간 상한이나 클라우드 반납·백업을 자동화하지 않습니다.

GGUF 변환은 [공식 llama.cpp](https://github.com/ggml-org/llama.cpp) checkout의 convert_hf_to_gguf.py와 build/bin/llama-quantize를 사용합니다. 사용자가 고정 commit으로 checkout·의존성 설치·빌드를 먼저 마쳐야 합니다. 실제 실행은 origin과 HEAD, tracked source 변경 여부를 확인합니다. 병합은 4비트 모델이 아닌 같은 revision의 원래 베이스 가중치에 LoRA를 합쳐 수행하므로, 8GB VRAM 목표와 별개로 충분한 CPU RAM·디스크가 필요합니다.

```bash
python3 -m training.gguf.export --model Qwen/Qwen2.5-7B-Instruct --revision "$F2R_BASE_REV" --adapter training/outputs/v1/adapter --llama-cpp-dir /opt/llama.cpp --llama-cpp-revision "$F2R_LLAMA_REV" --output training/outputs/v1-gguf --dry-run
# 같은 명령에서 --dry-run을 제거하면 병합(bf16)·BF16 GGUF 변환·Q4_K_M 퀀타이제이션을 실행합니다.
```

dry-run은 데이터를 검사하거나 명령을 출력할 뿐 GPU·병합·변환 성공을 뜻하지 않습니다. 변환 결과는 model-Q4_K_M.gguf와 해시·크기·llama.cpp commit이 담긴 export_manifest.json입니다. evaluation_passed는 false로 두며, 변환된 GGUF를 별도로 실행·재평가한 뒤에만 서비스 전환을 검토합니다. 가중치가 약 5GB라는 것만으로 8GB 장비에서 임의의 긴 컨텍스트·동시 요청이 보장되지는 않습니다. 생성과 독립 검증 모델의 순차 실행/적재 조건을 실측해야 합니다.

## 현재 검증 범위와 후속 작업

- 합성 자료로 평가 분모·누락 응답·근거 오인용·동일 모델 재풀이 제외·오딧 분모·날짜/그룹 누출·응답 loss 마스크를 검사합니다.
- 로컬 테스트 HTTP 서버로 v0 요청·응답 프로토콜을 검증합니다. 이 서버의 결과를 실제 모델 점수로 보고하지 않습니다.
- PyTorch·Transformers·PEFT·bitsandbytes와 CUDA 학습, 실제 모델 v0 생성, PEFT 병합, llama.cpp 변환·8GB 구동은 아직 실행하지 않았습니다.
- 앱 부팅·Gradle 빌드는 실행하지 않았습니다. 이 변경에는 Java·DB·운영 배포 경로 변경이 없습니다.
- T07 내보내기에서 DATA_CONTRACT.md의 메타데이터·출력 형태를 맞추고, T05가 독립 재풀이·오딧 결과를 추가해야 현장 평가가 가능합니다.
- PR #1007 승인·병합 후 이 후속 PR의 base를 main으로 바꾸고, 클로드 교차 검토와 대표님 승인 후 병합합니다.
