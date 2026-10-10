#!/usr/bin/env bash
#
# run_verapdf.sh — veraPDF 실행 래퍼 (DesignCraft M-1)
#
# veraPDF로 PDF/A 검증을 수행합니다.
#
# 사용법:
#   ./run_verapdf.sh --flavour 2b <pdf_path>
#   ./run_verapdf.sh --flavour 2b --jar /path/to/verapdf-cli.jar <pdf_path>
#
# flavour: 1a, 1b, 2a, 2b, 2u, 3a, 3b, 3u, 4, 4e, 4f, ua1
#
# 종료 코드: PASS=0, FAIL=1
#
# 설치 (CI):
#   Qoder 실측 방식: izpack 설치기 jar에서 "pack-veraPDF CLI" 팩 추출
#   또는 Docker: openpreservation/verapdf
#
# 주의:
#   - `java -jar`는 "corrupt jarfile" 오류 발생 → `java -cp` 방식 사용
#   - veraPDF는 GPLv3+/MPLv2+ 듀얼 라이선스 → 외부 CLI 프로세스로만 호출 (링크 금지)
#
set -euo pipefail

FLAVOUR=""
JAR_PATH="${VERAPDF_JAR:-}"
PDF_PATH=""

usage() {
    echo "사용법: $0 --flavour <flavour> [--jar <jar_path>] <pdf_path>" >&2
    echo "  flavour 예: 1b, 2b, 2u, ua1" >&2
    exit 2
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --flavour) FLAVOUR="$2"; shift 2 ;;
        --jar) JAR_PATH="$2"; shift 2 ;;
        -h|--help) usage ;;
        *) PDF_PATH="$1"; shift ;;
    esac
done

if [[ -z "$FLAVOUR" || -z "$PDF_PATH" ]]; then
    usage
fi

if [[ ! -f "$PDF_PATH" ]]; then
    echo "[FAIL] $PDF_PATH"
    echo "  - ERROR: 파일을 찾을 수 없음: $PDF_PATH"
    exit 1
fi

# veraPDF jar 찾기
if [[ -z "$JAR_PATH" ]]; then
    # 기본 위치 검색
    for candidate in \
        "./verapdf-cli.jar" \
        "./assets/verapdf/vcli/verapdf-cli.jar" \
        "/opt/verapdf/verapdf-cli.jar" \
        "$HOME/verapdf/verapdf-cli.jar"; do
        if [[ -f "$candidate" ]]; then
            JAR_PATH="$candidate"
            break
        fi
    done
fi

if [[ -z "$JAR_PATH" || ! -f "$JAR_PATH" ]]; then
    echo "[FAIL] $PDF_PATH"
    echo "  - ERROR: veraPDF jar를 찾을 수 없음."
    echo "  - VERAPDF_JAR 환경변수 또는 --jar 옵션으로 지정하세요."
    echo "  - 설치: Qoder 보고서의 izpack 팩 추출 방식 참조"
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo "[FAIL] $PDF_PATH"
    echo "  - ERROR: java가 설치되지 않았습니다."
    exit 1
fi

# veraPDF 실행 (java -cp 방식 — java -jar는 corrupt jarfile 오류)
# GreenfieldCliWrapper: CLI 진입점
echo "veraPDF 실행 중: flavour=$FLAVOUR, pdf=$PDF_PATH" >&2
OUTPUT=$(java -cp "$JAR_PATH" org.verapdf.apps.GreenfieldCliWrapper \
    --flavour "$FLAVOUR" \
    "$PDF_PATH" 2>&1) || true

# 결과 파싱
# veraPDF 출력에 "isCompliant=\"true\"" 또는 "compliant=\"1\"" 포함 시 PASS
if echo "$OUTPUT" | grep -qi 'isCompliant="true"'; then
    echo "[PASS] $PDF_PATH"
    echo "  - veraPDF flavour $FLAVOUR: PASS (compliant)"
    echo "  - 근거: veraPDF XML 리포트 isCompliant=\"true\""
    exit 0
elif echo "$OUTPUT" | grep -qi 'isCompliant="false"'; then
    echo "[FAIL] $PDF_PATH"
    echo "  - veraPDF flavour $FLAVOUR: FAIL (non-compliant)"
    echo "  - 근거: veraPDF XML 리포트 isCompliant=\"false\""
    # 실패 상세 (처음 10줄)
    echo "$OUTPUT" | grep -i -A2 "rule\|error" | head -20
    exit 1
else
    echo "[FAIL] $PDF_PATH"
    echo "  - ERROR: veraPDF 실행 실패 또는 결과 파싱 불가"
    echo "$OUTPUT" | head -20
    exit 1
fi
