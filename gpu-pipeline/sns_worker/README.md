# 인스타 자동 피드 GPU 작업자

runyour.ai GPU(RTX A5000 24GB)에서 ComfyUI로 피드 이미지를 만든다. 운영 서버·DB와 분리: GPU에는 작업자 토큰 하나만 두고, 작업을 HTTP로 가져가 결과 JPEG만 올린다.

## 1. ComfyUI와 모델

```bash
git clone https://github.com/comfyanonymous/ComfyUI && cd ComfyUI
pip install -r requirements.txt
# 얼굴 맞추기(IPAdapter) 노드
git clone https://github.com/cubiq/ComfyUI_IPAdapter_plus custom_nodes/ComfyUI_IPAdapter_plus
```

| 파일 | 넣을 곳 | 출처 | 라이선스 |
|---|---|---|---|
| `sd_xl_base_1.0.safetensors` | `models/checkpoints/` | stabilityai/stable-diffusion-xl-base-1.0 | CreativeML Open RAIL++-M(상업 이용 가능, 사용 제한 조항 준수) |
| `ip-adapter-plus-face_sdxl_vit-h.safetensors` | `models/ipadapter/` | h94/IP-Adapter (sdxl_models) | Apache-2.0 |
| `CLIP-ViT-H-14-laion2B-s32B-b79K.safetensors` | `models/clip_vision/` | h94/IP-Adapter (models/image_encoder) | MIT |

- 얼굴 인식 모델(insightface)을 쓰는 FaceID·InstantID·PuLID는 그 모델이 **비상업 연구용**이라 쓰지 않는다.
- 사진풍 품질이 부족하면 상업 이용 가능한 SDXL 파생 체크포인트로 바꾸고 `SNS_CKPT`만 바꾼다(라이선스 먼저 확인).
- 얼굴이 충분히 같지 않으면 다음 단계로 캐릭터 LoRA 학습을 검토한다(승인된 이미지로만).

ComfyUI 실행: `python main.py --listen 127.0.0.1 --port 8188`(외부에 열지 않는다).

## 2. 작업자 실행

```bash
export F2R_BASE_URL=https://feel2real.co.kr
export SNS_WORKER_TOKEN=...            # 운영 서버 SNS_WORKER_TOKEN과 같은 값
export COMFYUI_URL=http://127.0.0.1:8188
python3 gpu-pipeline/sns_worker/worker.py --exit-when-idle 30
```

- 30초마다 작업을 확인한다. `--exit-when-idle 30`이면 30분 동안 작업이 없을 때 끝난다 → **GPU 인스턴스는 콘솔에서 직접 중지**(시간 요금).
- 매일 19:00에 작업이 생기므로, GPU를 19:00 전후로 켜 두면 바로 처리된다. 꺼져 있던 동안의 작업은 켜면 처리된다.
- 실패하면 서버에 알려 1회 다시 시도하고, 두 번째 실패는 실패 탭에 사유가 남는다.
- 작업자는 비율(1:1·4:5·16:9)에 맞춰 1024×1024 · 896×1120 · 1344×768로 만든다.

## 3. 테스트

```bash
python3 -m unittest discover -s gpu-pipeline/tests
```

가짜 F2R·ComfyUI 서버로 작업 가져가기 → 참고 이미지 → 생성 → JPEG 업로드 / 실패 보고 흐름을 확인한다. 실제 GPU·ComfyUI 품질(얼굴 유지)은 시험 생성으로 사람이 확인한다.
