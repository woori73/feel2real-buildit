# DesignCraft M-1: PDF 품질 검사 자동화

DesignCraft 출판 파이프라인의 PDF 품질 게이트 (M-1).

## 구성

```
ops/designcraft/qa/
├── verify_pdf.py              # PyMuPDF 기반 PDF 품질 검사
├── check_glyph_coverage.py    # fontTools 기반 한글 커버리지 검사
├── run_verapdf.sh             # veraPDF 실행 래퍼 (PDF/A 검증)
├── test/
│   ├── good_korean.pdf        # 정상 한글 PDF (회귀 테스트용)
│   └── missing_glyph.pdf      # 글리프 누락 PDF (회귀 테스트용)
└── README.md                  # 이 파일
```

## 사용법

### 1. PDF 품질 검사

```bash
# 기본 검사 (페이지 수·크기·폰트·텍스트)
python verify_pdf.py output.pdf \
  --expect-pages 2 \
  --expect-text "제1장,한글 테스트" \
  --expect-size A4

# 종료 코드: PASS=0, FAIL=1
```

**검사 항목:**
- 페이지 수 (기대값과 비교)
- 페이지 크기 (A4: 595×842pt, 허용 오차 ±2pt)
- 폰트 임베드 확인 (서브셋 폰트명 출력)
- 기대 한글 문구 존재 확인 (텍스트 추출 기반)

### 2. 한글 글리프 커버리지 검사

```bash
# 폰트 파일의 한글 11,172자 커버리지 검사
python check_glyph_coverage.py NotoSansKR-Regular.otf

# 종료 코드: 100%=0, 아니면 1
```

**검사 항목:**
- 한글 음절 U+AC00–U+D7A3 전수 검사 (11,172자)
- 누락 글리프 목록 출력 (처음 20개)

### 3. veraPDF (PDF/A 검증)

```bash
# PDF/A-2b 검증
./run_verapdf.sh --flavour 2b output.pdf

# veraPDF jar 경로 지정
./run_verapdf.sh --flavour 2b --jar /path/to/verapdf-cli.jar output.pdf
# 또는 환경변수: VERAPDF_JAR=/path/to/verapdf-cli.jar

# 종료 코드: PASS=0, FAIL=1
```

**주의:**
- `java -jar`는 "corrupt jarfile" 오류 발생 → `java -cp` 방식 사용 (스크립트에 내장)
- veraPDF는 GPLv3+/MPLv2+ → **외부 CLI 프로세스로만 호출** (링크 금지)

## CI 통합

`.github/workflows/designcraft-qa.yml`에서 자동 실행:
- `ops/designcraft/**` 변경 시 PR에서 실행
- `workflow_dispatch`로 수동 실행 가능

## 검사 한계 (중요)

### 텍스트 추출 기반 검사의 한계

**정상 PDF의 텍스트 추출 성공만으로 모든 글리프가 정상이라고 판정하지 마십시오.**

- `verify_pdf.py`는 PDF에서 추출된 텍스트에 기대 문구가 있는지만 확인합니다.
- 추출된 텍스트에 **없는** 글리프는 검출할 수 없습니다.
- 예시:
  - PDF에 "가"가 있고 추출되면 → PASS
  - 하지만 "힣"이 tofu(□)로 렌더링되어도, 추출 텍스트에 "힣"이 없으면 → 검출 불가
- **완전한 검사는 DesignCraft preflight의 `missingGlyphs`와 병행해야 합니다.**

### 권장 검사 조합

```
1. fontTools 커버리지 (사전): 폰트 자체가 한글을 지원하는지 확인
   → check_glyph_coverage.py

2. PyMuPDF 텍스트 검사 (사후): 출력 PDF에 기대 문구가 있는지 확인
   → verify_pdf.py

3. DesignCraft preflight (사후): 실제 렌더링 시 글리프 누락 확인
   → preflight.run → missingGlyphs = 0

4. veraPDF (납품): PDF/A 표준 준수 확인
   → run_verapdf.sh
```

4단계를 모두 통과해야 "정상"으로 판정합니다.

## 라이선스

| 도구 | 용도 | 라이선스 | 제약 |
|---|---|---|---|
| `verify_pdf.py` | 이 스크립트 | MIT | 없음 |
| `check_glyph_coverage.py` | 이 스크립트 | MIT | 없음 |
| `run_verapdf.sh` | 이 스크립트 | MIT | 없음 |
| PyMuPDF | PDF 파싱 (verify_pdf.py) | **AGPL-3.0** | **CI·로컬 QA용으로만 사용. 서버 통합 금지.** |
| fontTools | 폰트 분석 | MIT | 없음 |
| veraPDF | PDF/A 검증 | **GPLv3+/MPLv2+** | **외부 CLI 프로세스로만 호출. 링크·임베드 금지.** |
| pikepdf/qpdf | (M-3에서 사용 예정) | MPL-2.0 | 없음 |

**AGPL/GPL 경계 원칙:**
- PyMuPDF: Python 프로세스 내에서 import하여 사용 (CI 러너, 로컬)
- veraPDF: `java -cp` 별도 프로세스로 실행 (절대 Java/Python에 링크하지 않음)
- 서버(EC2)에는 어떤 경우에도 통합하지 않음

## 회귀 테스트

```bash
# 정상 케이스 (PASS 예상)
python verify_pdf.py test/good_korean.pdf \
  --expect-pages 2 \
  --expect-text "제1장 한글 테스트" \
  --expect-size A4
# → [PASS], exit 0

# 결함 케이스 (FAIL 예상)
python verify_pdf.py test/missing_glyph.pdf \
  --expect-text "제1장 한글 테스트"
# → [FAIL], exit 1, "text MISSING" 출력
```
