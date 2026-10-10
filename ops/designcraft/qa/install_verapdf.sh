#!/usr/bin/env bash
#
# install_verapdf.sh — veraPDF CI 설치 스크립트 (DesignCraft M-1)
#
# 다운로드: https://software.verapdf.org/rel/verapdf-installer.zip
#   - verapdf-greenfield-*/verapdf-izpack-installer-*.jar 포함
#   - izpack jar 내부: resources/packs/pack-veraPDF CLI (16MB fat jar)
#   - Qoder 실측: 이 팩을 직접 verapdf-cli.jar로 사용 가능
#
# 사용법:
#   ./install_verapdf.sh [install_dir]
#
# 종료 코드: 성공=0, 실패=1
# 설치 실패 시 반드시 FAIL (SUCCESS 아님)
#
# 라이선스: veraPDF는 GPLv3+/MPLv2+ 듀얼 — 외부 CLI 프로세스로만 호출 (링크 금지)
#
set -euo pipefail

INSTALL_DIR="${1:-/opt/verapdf}"
VERAPDF_JAR="$INSTALL_DIR/verapdf-cli.jar"

echo "=== veraPDF 설치 ==="
echo "설치 디렉토리: $INSTALL_DIR"

# Java 확인 (필수)
if ! command -v java &> /dev/null; then
    echo "[FAIL] java가 설치되지 않았습니다."
    exit 1
fi
echo "Java: $(java -version 2>&1 | head -1)"

# unzip 확인
if ! command -v unzip &> /dev/null; then
    echo "[FAIL] unzip이 설치되지 않았습니다."
    exit 1
fi

# 기존 설치 확인
if [[ -f "$VERAPDF_JAR" ]]; then
    echo "기존 veraPDF jar 발견: $VERAPDF_JAR"
    if java -cp "$VERAPDF_JAR" org.verapdf.apps.GreenfieldCliWrapper --version &> /dev/null; then
        echo "[PASS] 기존 veraPDF가 정상 동작함. 설치 스킵."
        echo "VERAPDF_JAR=$VERAPDF_JAR"
        exit 0
    else
        echo "기존 jar 동작 실패. 재설치 진행."
        rm -f "$VERAPDF_JAR"
    fi
fi

mkdir -p "$INSTALL_DIR"
WORK_DIR=$(mktemp -d)
trap "rm -rf $WORK_DIR" EXIT
cd "$WORK_DIR"

# 1. verapdf-installer.zip 다운로드
INSTALLER_URL="https://software.verapdf.org/rel/verapdf-installer.zip"
INSTALLER_ZIP="$WORK_DIR/verapdf-installer.zip"

echo "다운로드: $INSTALLER_URL"
if ! curl -sL --fail --retry 3 --max-time 300 -o "$INSTALLER_ZIP" "$INSTALLER_URL"; then
    echo "[FAIL] veraPDF 설치기 다운로드 실패"
    exit 1
fi
echo "다운로드 완료: $(du -h "$INSTALLER_ZIP" | cut -f1)"

# 2. izpack 설치기 jar 추출 (하위 디렉토리 포함)
echo "izpack 설치기 추출 중..."
if ! unzip -o -q "$INSTALLER_ZIP" "*/verapdf-izpack-installer-*.jar"; then
    echo "[FAIL] izpack 설치기 추출 실패"
    unzip -l "$INSTALLER_ZIP" | head -10
    exit 1
fi

IZPACK_JAR=$(find "$WORK_DIR" -name "verapdf-izpack-installer-*.jar" | head -1)
if [[ -z "$IZPACK_JAR" || ! -f "$IZPACK_JAR" ]]; then
    echo "[FAIL] izpack 설치기 jar를 찾을 수 없음"
    exit 1
fi
echo "설치기: $(basename "$IZPACK_JAR") ($(du -h "$IZPACK_JAR" | cut -f1))"

# 3. "pack-veraPDF CLI" 팩 추출
# 경로: resources/packs/pack-veraPDF CLI (공백 포함, 확장자 없음)
echo "veraPDF CLI 팩 추출 중..."
PACK_PATH="resources/packs/pack-veraPDF CLI"

# 팩 존재 확인
if ! unzip -l "$IZPACK_JAR" "$PACK_PATH" &> /dev/null; then
    echo "[FAIL] 팩을 찾을 수 없음: $PACK_PATH"
    echo "사용 가능한 팩:"
    unzip -l "$IZPACK_JAR" | grep "resources/packs/" | head -10
    exit 1
fi

# 팩 추출 (팩 자체가 fat jar이므로 직접 사용)
unzip -p "$IZPACK_JAR" "$PACK_PATH" > "$VERAPDF_JAR"

if [[ ! -f "$VERAPDF_JAR" ]] || [[ ! -s "$VERAPDF_JAR" ]]; then
    echo "[FAIL] jar 추출 실패"
    exit 1
fi
echo "추출 완료: $(du -h "$VERAPDF_JAR" | cut -f1)"

# 4. 동작 확인 (java -cp 방식 — java -jar는 corrupt jarfile 오류)
echo "동작 확인 중..."
if java -cp "$VERAPDF_JAR" org.verapdf.apps.GreenfieldCliWrapper --version 2>&1 | head -5; then
    echo ""
    echo "[PASS] veraPDF 설치 완료"
    echo "  - jar: $VERAPDF_JAR"
    echo "  - 실행: java -cp \$VERAPDF_JAR org.verapdf.apps.GreenfieldCliWrapper"
    echo ""
    echo "VERAPDF_JAR=$VERAPDF_JAR"
    exit 0
else
    echo "[FAIL] veraPDF 동작 확인 실패"
    exit 1
fi
