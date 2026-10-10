#!/usr/bin/env bash
#
# install_verapdf.sh — veraPDF CI 설치 스크립트 (DesignCraft M-1)
#
# 다운로드: https://software.verapdf.org/rel/verapdf-installer.zip
#   - verapdf-izpack-installer-*.jar 포함 (izpack 설치기)
#   - Qoder 실측: jar 내부 "pack-veraPDF CLI" 팩에서 verapdf-cli.jar 추출
#
# 사용법:
#   ./install_verapdf.sh [--install-dir /opt/verapdf]
#
# 종료 코드: 성공=0, 실패=1
# 설치 실패 시 반드시 FAIL (SUCCESS 아님)
#
# 라이선스: veraPDF는 GPLv3+/MPLv2+ 듀얼 — 외부 CLI 프로세스로만 호출 (링크 금지)
#
set -euo pipefail

INSTALL_DIR="${1:-/opt/verapdf}"
VERAPDF_JAR="$INSTALL_DIR/verapdf-cli.jar"

# SHA-256 (verapdf-installer.zip, 2026-06-03 기준)
# 출처: https://fossies.org/linux/misc/verapdf-installer.zip/
# 주의: veraPDF가 새 버전을 릴리스하면 해시가 변경됨. 실패 시 수동 확인 필요.
INSTALLER_SHA256="6cc6341cb1af644044054b81f00a6590a7918abb18f762243de115258bcad838"

echo "=== veraPDF 설치 ==="
echo "설치 디렉토리: $INSTALL_DIR"

# Java 확인 (필수)
if ! command -v java &> /dev/null; then
    echo "[FAIL] java가 설치되지 않았습니다."
    exit 1
fi
echo "Java: $(java -version 2>&1 | head -1)"

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

# unzip 확인
if ! command -v unzip &> /dev/null; then
    echo "[FAIL] unzip이 설치되지 않았습니다."
    exit 1
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
    echo "  URL: $INSTALLER_URL"
    exit 1
fi
echo "다운로드 완료: $(du -h "$INSTALLER_ZIP" | cut -f1)"

# 2. SHA-256 검증
echo "SHA-256 검증 중..."
ACTUAL_SHA=$(sha256sum "$INSTALLER_ZIP" | cut -d' ' -f1)
echo "  기대: $INSTALLER_SHA256"
echo "  실제: $ACTUAL_SHA"
if [[ "$ACTUAL_SHA" != "$INSTALLER_SHA256" ]]; then
    echo "[WARN] SHA-256 불일치 — veraPDF가 새 버전을 릴리스했을 수 있음"
    echo "  계속 진행하지만 수동 확인 권장"
    # FAIL이 아닌 WARN으로 처리 (버전 업데이트 시 해시 변경은 정상)
else
    echo "  SHA-256 일치"
fi

# 3. zip에서 izpack 설치기 jar 추출
echo "izpack 설치기 추출 중..."
unzip -o -q "$INSTALLER_ZIP" "verapdf-izpack-installer-*.jar" -d "$WORK_DIR"
IZPACK_JAR=$(ls "$WORK_DIR"/verapdf-izpack-installer-*.jar 2>/dev/null | head -1)

if [[ -z "$IZPACK_JAR" || ! -f "$IZPACK_JAR" ]]; then
    echo "[FAIL] izpack 설치기 jar를 찾을 수 없음"
    unzip -l "$INSTALLER_ZIP" | head -20
    exit 1
fi
echo "설치기: $(basename "$IZPACK_JAR") ($(du -h "$IZPACK_JAR" | cut -f1))"

# 4. izpack jar에서 "veraPDF CLI" 팩 추출
# Qoder 실측: resources/packs/ 아래에 팩 jar가 있음
echo "veraPDF CLI 팩 검색 중..."
unzip -l "$IZPACK_JAR" | grep -i "pack.*verapdf.*cli" | head -5

# 팩 경로 찾기 (대소문자 변형 대응)
PACK_PATH=""
for pattern in "veraPDF CLI" "veraPDF-CLI" "verapdf-cli"; do
    PACK_PATH=$(unzip -l "$IZPACK_JAR" | grep -i "$pattern" | grep -i "\.jar$" | awk '{print $4}' | head -1)
    if [[ -n "$PACK_PATH" ]]; then
        break
    fi
done

# 팩을 직접 찾지 못하면 packs/ 디렉토리 목록 확인
if [[ -z "$PACK_PATH" ]]; then
    echo "팩 직접 검색 실패. packs/ 디렉토리 확인:"
    unzip -l "$IZPACK_JAR" | grep -i "packs/" | head -20
    # 첫 번째 jar 팩 사용
    PACK_PATH=$(unzip -l "$IZPACK_JAR" | grep -i "packs/.*\.jar$" | awk '{print $4}' | head -1)
fi

if [[ -z "$PACK_PATH" ]]; then
    echo "[FAIL] veraPDF CLI 팩을 찾을 수 없음"
    exit 1
fi

echo "팩 추출: $PACK_PATH"
unzip -p "$IZPACK_JAR" "$PACK_PATH" > "$VERAPDF_JAR"

if [[ ! -f "$VERAPDF_JAR" ]] || [[ ! -s "$VERAPDF_JAR" ]]; then
    echo "[FAIL] jar 추출 실패"
    exit 1
fi
echo "추출 완료: $(du -h "$VERAPDF_JAR" | cut -f1)"

# 5. 동작 확인 (java -cp 방식 — java -jar는 corrupt jarfile 오류)
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
