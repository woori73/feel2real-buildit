#!/usr/bin/env bash
# 신규 AWS 계정 EC2(AL2023) 1회 프로비저닝. 구 호스트 읽기 전용 인벤토리(2026-10-18) 기준.
# 비밀값은 다루지 않는다 — /etc/f2r/env 는 SSM Parameter Store(/f2r/app/*)에서 render_env 로 만든다.
# 사용: sudo bash provision-new-host.sh            (전체 실행)
#       source provision-new-host.sh               (함수만 로드 — 테스트용)
set -euo pipefail

HEAP_MB_DEFAULT=2048   # 신규 t3.medium(4GB) 기준. 구 호스트(2GB)는 1g 였다.

# f2r-app systemd 유닛 텍스트를 stdout 으로 출력한다.
render_app_unit() {
  local heap_mb="${1:-$HEAP_MB_DEFAULT}"
  cat <<UNIT
[Unit]
Description=f2r BuildIt Spring Boot app
After=network-online.target
Wants=network-online.target

[Service]
User=ec2-user
EnvironmentFile=/etc/f2r/env
ExecStart=/usr/bin/java -Xmx${heap_mb}m -jar /home/ec2-user/app.jar
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
UNIT
}

# SSM Parameter Store 의 name=value 목록(stdin, "NAME<TAB>VALUE")을 EnvironmentFile 형식으로 바꾼다.
# 이름은 /f2r/app/ 접두어를 제거하고, 값은 줄바꿈이 있으면 거부한다(주입 방지).
render_env() {
  local name value
  while IFS=$'\t' read -r name value; do
    [ -n "$name" ] || continue
    case "$value" in *$'\n'*) echo "invalid value for $name" >&2; return 1;; esac
    printf '%s=%s\n' "${name##*/}" "$value"
  done
}

install_packages() {
  dnf install -y java-21-amazon-corretto-headless python3.11 python3.11-pip fontconfig tar xz git
  # 한글 폰트: 패키지명이 배포판 버전에 따라 달라 실패해도 중단하지 않고 아래에서 검증한다.
  dnf install -y google-noto-sans-cjk-ttc-fonts || dnf install -y google-noto-sans-cjk-fonts || true
  fc-cache -f
  fc-list :lang=ko | grep -q . || echo "WARN: 한글 폰트 미설치 — /opt/artcraft/fonts 이전 필요"
}

install_ffmpeg_static() {
  [ -x /usr/local/bin/ffmpeg ] && return 0
  local arch=amd64
  [ "$(uname -m)" = aarch64 ] && arch=arm64
  curl -fsSL "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-${arch}-static.tar.xz" -o /tmp/ffmpeg.tar.xz
  tar -xJf /tmp/ffmpeg.tar.xz -C /tmp
  install -m 755 /tmp/ffmpeg-*-static/ffmpeg /usr/local/bin/ffmpeg
  install -m 755 /tmp/ffmpeg-*-static/ffprobe /usr/local/bin/ffprobe
}

# 파라미터(/f2r/app/*)로 /etc/f2r/env 를 만든다. 값은 로그에 출력하지 않는다.
fetch_env_from_ssm() {
  local region="${AWS_REGION:-ap-northeast-2}"
  install -d -m 755 /etc/f2r
  aws ssm get-parameters-by-path --region "$region" --path /f2r/app/ --with-decryption \
    --query 'Parameters[].[Name,Value]' --output text | render_env > /etc/f2r/env.new
  chmod 600 /etc/f2r/env.new && mv /etc/f2r/env.new /etc/f2r/env
  [ -s /etc/f2r/env ] || echo "WARN: /f2r/app/* 파라미터 없음 — 비밀값 이전 후 다시 실행"
}

# 앱이 기동 시 존재를 요구하는 디렉터리(application-prd.yaml: 영상 검증·최적화 임시 경로, 부모 /var/tmp/f2r 필수).
required_dirs() {
  printf '%s\n' /opt/designcraft /opt/artcraft/fonts /opt/buildit-wiki \
    /var/tmp/f2r /var/tmp/f2r/academy-video-validation /var/tmp/f2r/academy-video-optimization
}

main() {
  install_packages
  install_ffmpeg_static
  required_dirs | xargs install -d -o ec2-user -g ec2-user
  fc-cache -f
  fetch_env_from_ssm || echo "WARN: env 생성 건너뜀"
  render_app_unit "${HEAP_MB:-$HEAP_MB_DEFAULT}" > /etc/systemd/system/f2r-app.service
  systemctl daemon-reload
  systemctl enable f2r-app.service
  echo "provision done: jar 배포(deploy-ec2.yml) 후 f2r-app 시작"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then main "$@"; fi
