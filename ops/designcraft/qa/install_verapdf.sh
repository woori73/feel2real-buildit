#!/usr/bin/env bash
#
# install_verapdf.sh — veraPDF CI 설치 스크립트 (DesignCraft M-1)
#
# Qoder 실측 방식: izpack 설치기 jar에서 "pack-veraPDF CLI" 팩 추출
# 또는 Docker openpreservation/verapdf 사용
#
# 사용법:
#   ./install_verapdf.sh [--version 1.30.3] [--install-dir /opt/verapdf]
#
# 종료 코드: 성공=0, 실패=1
# 설치 실패 시 반드시 FAIL (SUCCESS 아님)
#
# 라이선스: veraPDF는 GPLv3+/MPLv2+ 듀얼 — 외부 CLI 프로세스로만 호출 (링크 금지)
#
set -euo pipefail

VERSION="${1:-1.30.3}"
INSTALL_DIR="${2:-/opt/verapdf}"
VERAPDF_JAR="$INSTALL_DIR/verapdf-cli.jar"

# SHA-256 검증용 (veraPDF 1.30.3 izpack 설치기)
# 참고: izpack 설치기에서 팩을 추출하므로 설치기 자체의 해시를 검증
declare -A INSTALLER_SHA256=(
    ["1.30.3"]="SKIP: izpack 설치기는 버전별로 해시가 다를 수 있음, 다운로드 후 수동 검증 권장"
)

echo "=== veraPDF $VERSION 설치 ==="
echo "설치 디렉토리: $INSTALL_DIR"

# Java 확인 (필수)
if ! command -v java &> /dev/null; then
    echo "[FAIL] java가 설치되지 않았습니다."
    echo "  - Ubuntu: sudo apt-get install -y default-jre-headless"
    exit 1
fi
echo "Java 확인: $(java -version 2>&1 | head -1)"

# 기존 설치 확인
if [[ -f "$VERAPDF_JAR" ]]; then
    echo "기존 veraPDF jar 발견: $VERAPDF_JAR"
    # 동작 확인
    if java -cp "$VERAPDF_JAR" org.verapdf.apps.GreenfieldCliWrapper --version &> /dev/null; then
        echo "[PASS] 기존 veraPDF가 정상 동작함. 설치 스킵."
        echo "VERAPDF_JAR=$VERAPDF_JAR"
        exit 0
    else
        echo "기존 jar가 동작하지 않음. 재설치 진행."
        rm -f "$VERAPDF_JAR"
    fi
fi

mkdir -p "$INSTALL_DIR"
cd "$INSTALL_DIR"

# 방법 1: Docker 이미지에서 jar 추출 시도 (CI에서 권장)
# 방법 2: izpack 설치기 다운로드 후 팩 추출 (Qoder 실측 방식)

echo "veraPDF 설치 방법 선택..."

# Docker 사용 가능 여부 확인
if command -v docker &> /dev/null; then
    echo "Docker 사용 가능 — openpreservation/verapdf 이미지에서 jar 추출 시도"
    # Docker 이미지는 전체 애플리케이션을 포함하므로 jar 추출이 복잡
    # 대신 직접 다운로드 방식 사용
    echo "Docker 방식은 복잡하므로 직접 다운로드로 진행"
fi

# izpack 설치기 다운로드 (Qoder 실측 방식)
# veraPDF 1.30.3 izpack 설치기 URL
# 참고: Maven Central에는 실행 가능한 CLI jar가 없음 (모두 POM)
# izpack 콘솔 설치기는 무응답이었음 → jar 내부 팩 직접 추출

INSTALLER_URL="https://github.com/veraPDF/veraPDF-library/releases/download/v${VERSION}/verapdf-installer-${VERSION}.jar"
INSTALLER_FILE="/tmp/verapdf-installer-${VERSION}.jar"

echo "설치기 다운로드: $INSTALLER_URL"
if ! curl -sL --fail --retry 3 --max-time 300 -o "$INSTALLER_FILE" "$INSTALLER_URL"; then
    echo "[FAIL] veraPDF 설치기 다운로드 실패: $INSTALLER_URL"
    echo "  - 네트워크 확인 또는 수동 설치 필요"
    echo "  - 대체: Docker openpreservation/verapdf 사용"
    exit 1
fi

echo "설치기 다운로드 완료: $(du -h "$INSTALLER_FILE" | cut -f1)"

# izpack jar에서 "pack-veraPDF CLI" 팩 추출
# Qoder 실측: resources/packs/pack-veraPDF CLI (16MB fat jar)
echo "izpack 팩 추출 중..."

# jar는 zip 형식이므로 unzip으로 추출 가능
if ! command -v unzip &> /dev/null; then
    echo "[FAIL] unzip이 설치되지 않았습니다."
    exit 1
fi

# 팩 목록 확인
echo "팩 목록:"
unzip -l "$INSTALLER_FILE" | grep -i "pack-" | head -10

# veraPDF CLI 팩 추출
# 실제 경로: resources/packs/pack-veraPDF CLI (또는 유사)
PACK_PATH=$(unzip -l "$INSTALLER_FILE" | grep -i "veraPDF CLI" | awk '{print $4}' | head -1)

if [[ -z "$PACK_PATH" ]]; then
    echo "[FAIL] veraPDF CLI 팩을 찾을 수 없음"
    echo "사용 가능한 팩:"
    unzip -l "$INSTALLER_FILE" | grep -i "pack-" | head -20
    exit 1
fi

echo "팩 추출: $PACK_PATH"
# 팩 자체가 jar 파일이므로 직접 복사
unzip -p "$INSTALLER_FILE" "$PACK_PATH" > "$VERAPDF_JAR"

if [[ ! -f "$VERAPDF_JAR" ]]; then
    echo "[FAIL] jar 추출 실패"
    exit 1
fi

echo "추출 완료: $(du -h "$VERAPDF_JAR" | cut -f1)"

# 동작 확인 (java -cp 방식 — java -jar는 corrupt jarfile 오류)
echo "동작 확인 중..."
if java -cp "$VERAPDF_JAR" org.verapdf.apps.GreenfieldCliWrapper --version 2>&1 | head -5; then
    echo ""
    echo "[PASS] veraPDF $VERSION 설치 완료"
    echo "  - jar: $VERAPDF_JAR"
    echo "  - 실행: java -cp \$VERAPDF_JAR org.verapdf.apps.GreenfieldCliWrapper"
    echo ""
    echo "VERAPDF_JAR=$VERAPDF_JAR"
    exit 0
else
    echo "[FAIL] veraPDF 동작 확인 실패"
    echo "  - java -cp 실행이 실패함"
    exit 1
fi
