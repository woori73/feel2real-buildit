#!/bin/bash
# EC2(f2r-app)에서 SSM으로 실행. ACTION=install|status|enable|disable. 비밀값은 읽거나 출력하지 않는다.
set -euo pipefail
ACTION="${ACTION:?}"
BASE=/opt/f2r-embed
APP_HEALTH=http://127.0.0.1:8080/actuator/health
ENV_FILE=/etc/f2r/env
HF_REPO="${HF_REPO:-onnx-community/embeddinggemma-300m-ONNX}"
MODEL_VERSION="${MODEL_VERSION:-embeddinggemma-300m-onnx-q8}"
SRC="${SRC_DIR:-/tmp/f2r-embed-src}"

avail_mb() { awk '/MemAvailable/{print int($2/1024)}' /proc/meminfo; }
app_ok() { curl -fsS --max-time 5 "$APP_HEALTH" 2>/dev/null | grep -q '"status":"UP"'; }
report() {
  echo "== memory =="; free -m
  echo "MemAvailable_MB=$(avail_mb)"
  echo "== services =="
  for s in f2r-app buildit-wiki f2r-embed; do echo "$s: $(systemctl is-active $s 2>/dev/null || true) enabled=$(systemctl is-enabled $s 2>/dev/null || true)"; done
  echo "MemTotal_MB=$(awk '/MemTotal/{print int($2/1024)}' /proc/meminfo)"
  echo "instance_type=$(curl -fsS -m 2 -H "X-aws-ec2-metadata-token: $(curl -fsS -m 2 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' 2>/dev/null)" http://169.254.169.254/latest/meta-data/instance-type 2>/dev/null || echo unknown)"
  echo "app_health_up=$(app_ok && echo yes || echo no)"
  echo "== top rss (MB) =="; ps -eo rss,comm --sort=-rss | head -6 | awk '{printf "%d %s\n",$1/1024,$2}'
  if systemctl is-active --quiet f2r-embed; then
    echo "embed MemoryCurrent_MB=$(( $(systemctl show f2r-embed -p MemoryCurrent --value) / 1048576 ))"
  fi
  echo "== load =="; uptime
  echo "== app embedding env (non-secret) =="; grep -E '^APP_ADMISSIONS_(EMBEDDING|PUBLIC_API)' "$ENV_FILE" 2>/dev/null || echo "(none)"
  echo "== guard log =="; journalctl -t f2r-embed-guard -n 5 --no-pager 2>/dev/null || true
}
set_env() { # key value
  grep -vE "^$1=" "$ENV_FILE" > "$ENV_FILE.tmp" || true
  printf '%s=%s\n' "$1" "$2" >> "$ENV_FILE.tmp"
  mv "$ENV_FILE.tmp" "$ENV_FILE"; chmod 600 "$ENV_FILE"
}
unset_env() { grep -vE "^$1=" "$ENV_FILE" > "$ENV_FILE.tmp" || true; mv "$ENV_FILE.tmp" "$ENV_FILE"; chmod 600 "$ENV_FILE"; }
restart_app() {
  systemctl restart f2r-app
  for _ in $(seq 1 30); do sleep 5; app_ok && return 0; done
  return 1
}
stop_embed() { systemctl stop f2r-embed 2>/dev/null || true; }

case "$ACTION" in
status) report ;;

install)
  TOTAL=$(awk '/MemTotal/{print int($2/1024)}' /proc/meminfo)
  [ "$TOTAL" -ge 7000 ] || { echo "ABORT: 총 메모리 ${TOTAL}MB — 4GB 환경 설치는 실측(OOM 2회)으로 금지. t3.large(8GB) 이상에서만 설치"; exit 3; }
  A=$(avail_mb); echo "MemTotal_MB=$TOTAL MemAvailable_MB(before)=$A"
  [ "$A" -ge 2500 ] || { echo "ABORT: 여유 메모리 부족(<2500MB) — 설치하지 않음"; exit 3; }
  app_ok || { echo "ABORT: 앱이 정상이 아님 — 설치하지 않음"; exit 3; }
  [ "$(df -m / | awk 'NR==2{print $4}')" -ge 2500 ] || { echo "ABORT: 디스크 여유 부족(<2.5GB)"; exit 3; }
  PY=$(command -v python3.11 || command -v python3.12 || command -v python3)
  id f2r-embed >/dev/null 2>&1 || useradd -r -s /sbin/nologin -d "$BASE" f2r-embed
  mkdir -p "$BASE/model"
  "$PY" -m venv "$BASE/venv" 2>/dev/null || { dnf install -y python3-pip >/dev/null; "$PY" -m venv "$BASE/venv"; }
  "$BASE/venv/bin/pip" install --no-cache-dir -q onnxruntime==1.20.1 tokenizers==0.21.0 numpy==1.26.4
  # 모델 파일 선택: 양자화(int8) 단일 파일 우선. 목록은 HF API로 확인한다.
  LIST=$(curl -fsSL --retry 3 "https://huggingface.co/api/models/$HF_REPO" | "$PY" -c 'import sys,json;print("\n".join(s["rfilename"] for s in json.load(sys.stdin)["siblings"]))')
  PICK=""; for f in onnx/model_quantized.onnx onnx/model_int8.onnx; do echo "$LIST" | grep -qx "$f" && PICK=$f && break; done
  [ -n "$PICK" ] || { echo "ABORT: 양자화 모델 파일 없음. 저장소 파일 목록:"; echo "$LIST" | head -40; exit 4; }
  echo "model file: $PICK"
  for f in "$PICK" "${PICK}_data" tokenizer.json; do
    if [ "$f" = "${PICK}_data" ] && ! echo "$LIST" | grep -qx "$f"; then continue; fi
    [ -s "$BASE/model/$(basename "$f")" ] || curl -fL --retry 3 -o "$BASE/model/$(basename "$f")" "https://huggingface.co/$HF_REPO/resolve/main/$f"
  done
  ( cd "$BASE/model" && ls -l && sha256sum "$(basename "$PICK")" tokenizer.json ) | tee "$BASE/MODEL_INFO"
  install -m 644 "$SRC/server.py" "$SRC/embed_core.py" "$SRC/calibrate.py" "$BASE/"
  echo "MIN_AVAIL_MB=600" > "$BASE/guard.conf"
  cat > "$BASE/guard.sh" <<'G'
#!/bin/bash
# 앱 보호: 여유 메모리가 임계값 미만이거나 앱이 죽어 있으면 임베딩부터 중지한다(재시작은 사람이 한다).
. /opt/f2r-embed/guard.conf
A=$(awk '/MemAvailable/{print int($2/1024)}' /proc/meminfo)
if systemctl is-active --quiet f2r-embed; then
  if [ "$A" -lt "$MIN_AVAIL_MB" ] || ! systemctl is-active --quiet f2r-app; then
    systemctl stop f2r-embed; logger -t f2r-embed-guard "stopped embedding: MemAvailable=${A}MB"
  fi
fi
G
  chmod 755 "$BASE/guard.sh"
  cat > /etc/systemd/system/f2r-embed.service <<U
[Unit]
Description=F2R admissions embedding server (EmbeddingGemma ONNX, CPU)
After=network.target
StartLimitIntervalSec=300
StartLimitBurst=3
[Service]
User=f2r-embed
WorkingDirectory=$BASE
Environment=EMBED_MODEL_PATH=$BASE/model/$(basename "$PICK")
Environment=EMBED_TOKENIZER_PATH=$BASE/model/tokenizer.json
Environment=EMBED_THREADS=2
Environment=EMBED_MAX_TOKENS=512
Environment=MALLOC_ARENA_MAX=2
ExecStart=$BASE/venv/bin/python $BASE/server.py
MemoryMax=2000M
MemorySwapMax=0
CPUQuota=150%
Nice=15
OOMScoreAdjust=900
TasksMax=64
Restart=on-failure
RestartSec=10
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
[Install]
WantedBy=multi-user.target
U
  cat > /etc/systemd/system/f2r-embed-guard.service <<U
[Service]
Type=oneshot
ExecStart=$BASE/guard.sh
U
  cat > /etc/systemd/system/f2r-embed-guard.timer <<U
[Timer]
OnBootSec=60
OnUnitActiveSec=30
[Install]
WantedBy=timers.target
U
  systemctl daemon-reload
  systemctl enable --now f2r-embed-guard.timer f2r-embed.service
  for _ in $(seq 1 40); do sleep 3; curl -fsS --max-time 3 http://127.0.0.1:8081/health >/dev/null 2>&1 && break; done
  if ! curl -fsS --max-time 3 http://127.0.0.1:8081/health; then
    echo; echo "FAIL: 임베딩 서버 기동 실패"; journalctl -u f2r-embed -n 30 --no-pager; stop_embed; exit 5
  fi
  echo; echo "== 보정(calibrate) =="; echo "embed MemoryCurrent_MB(before)=$(( $(systemctl show f2r-embed -p MemoryCurrent --value) / 1048576 ))"
  CAL=0; "$PY" "$BASE/calibrate.py" | tee "$BASE/CALIBRATION" || CAL=1
  sleep 5; report
  if [ "$(avail_mb)" -lt 1500 ] || ! app_ok || [ "$CAL" = 1 ]; then
    echo "FAIL: 설치 후 안전 기준 미달 — 임베딩 중지"; journalctl -u f2r-embed -n 25 --no-pager; stop_embed; systemctl disable f2r-embed >/dev/null 2>&1 || true; exit 6
  fi
  echo "INSTALL_OK" ;;

enable)
  systemctl is-active --quiet f2r-embed || { echo "ABORT: f2r-embed가 실행 중이 아님"; exit 3; }
  if [ -z "${MIN_SIMILARITY:-}" ] && [ -f "$BASE/CALIBRATION" ]; then
    S=$(sed -n 's/^SUGGESTED_FLOOR=//p' "$BASE/CALIBRATION")
    if [ -n "$S" ] && [ "$S" != None ]; then MIN_SIMILARITY=$S; else echo "WARN: 보정셋이 분리되지 않아 기본 하한(0.5) 유지 — 키워드 게이트가 1차 방어"; fi
  fi
  echo "min_similarity=${MIN_SIMILARITY:-default(0.5)}"
  cp -p "$ENV_FILE" "$ENV_FILE.bak-embed-$(date +%s)"
  set_env APP_ADMISSIONS_EMBEDDING_ENABLED true
  set_env APP_ADMISSIONS_EMBEDDING_SERVICE_URL http://127.0.0.1:8081
  set_env APP_ADMISSIONS_EMBEDDING_MODEL_VERSION "$MODEL_VERSION"
  [ -z "${MIN_SIMILARITY:-}" ] || set_env APP_ADMISSIONS_EMBEDDING_MIN_SIMILARITY "$MIN_SIMILARITY"
  if restart_app; then echo "ENABLE_OK"; report
  else
    echo "FAIL: 앱 health 실패 — env 복구 후 재시작"
    cp -p "$(ls -t "$ENV_FILE".bak-embed-* | head -1)" "$ENV_FILE"; restart_app || true; stop_embed; exit 7
  fi ;;

disable)
  for k in ENABLED SERVICE_URL MODEL_VERSION MIN_SIMILARITY; do unset_env "APP_ADMISSIONS_EMBEDDING_$k"; done
  set_env APP_ADMISSIONS_EMBEDDING_ENABLED false
  stop_embed
  restart_app && echo "DISABLE_OK" || { echo "FAIL: 앱 health 실패"; exit 8; }
  report ;;

*) echo "unknown ACTION"; exit 2 ;;
esac
