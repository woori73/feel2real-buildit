# ArtCraft 소스 임포트 설계 — P0-④ HTML→PDF 입력 경로

- 상태: 설계 + 최소 구현 완료 (코드 리뷰 수준 검증, PR 미생성)
- 브랜치: `buildit-phase2/artcraft-p0-recovery`
- 날짜: 2026-10-10
- 관련: `ArtCraftEditorController` / `ArtCraftEditorService` (`domain/artcraft`)

## 1. 배경

PdfCraft에 HTML 입력 기능이 없다. ArtCraft 프로젝트에 편집할 소스 PDF를
주입할 경로가 필요했다.

Qoder 실측 결과: **PDF→HTML 역변환은 2단 조판·밑줄·mm 단위 조판이 전부
소실**된다. 따라서 아래 정본 정책을 확정한다.

## 2. 정본 정책 (Source of Truth)

| 구분 | 역할 | 파일 |
|---|---|---|
| 정본 | 학습지 HTML (예: 내신위키 학습지) — 사람이 읽고 고치는 원본 | `source.html` |
| 편집 입력 | 정본 HTML을 Chromium이 렌더링한 PDF. pdfcraft-cli가 여는 파일 | `source.pdf` |
| 편집 결과 | pdfcraft-cli `doc_save` 산출물. 권장 파일명 `output.pdf` | `output.pdf` |

규칙:

1. **HTML이 정본, PDF는 산출물이다.** 편집은 항상 `source.pdf`를 입력으로
   수행하고, 결과는 `output.pdf`(또는 `doc_save`가 지정한 경로)로만 저장한다.
2. **PDF→HTML 역변환을 원본 복원 수단으로 사용하지 않는다.**
   (근거: Qoder 실측 — 조판 정보 소실)
3. HTML 업로드 시 원본 HTML은 `source.html`에 그대로 보존한다.
   PDF를 직접 업로드하면 정본은 그 PDF 자체이며, 이전에 남아 있던
   `source.html`(내용 불일치 가능)은 삭제한다.
4. `source.pdf` 덮어쓰기는 허용한다. 프로젝트 버전·스냅샷(`project.json` 계열)은
   기존 스냅샷 메커니즘으로 그대로 보존된다.

## 3. 워크스페이스 구조

```
{storage-root}/artcraft-projects/{academyId}/{projectId}/
├── project.json        # 프로젝트 메타 + version (기존)
├── snapshots/          # v{n}.json 버전 스냅샷 (기존)
├── source.html         # 정본 HTML 원본 (HTML 업로드 시에만 존재)
├── source.pdf          # 편집 입력 PDF (필수 선행조건)
└── output.pdf          # 편집 결과 (doc_save 권장 경로)
```

`{storage-root}` 기본값: `ArtCraftEditorProperties.storageRoot`.

## 4. 새 API — `POST /api/artcraft/projects/{id}/source`

### 요청

- `Content-Type: multipart/form-data`, 파트명 `file` (HTML 또는 PDF, 1개)
- 쿼리 파라미터 `academyId` (선택, ADMIN 전용 — 기존 컨벤션과 동일)
- 권한: **ADMIN / TUTOR**만 가능 (`requireEditorRole`, 그 외 403)

### 동작

1. 파일 형식 판정: 확장자(`.html`/`.htm`/`.pdf`) + `Content-Type`
   (`text/html`, `application/xhtml+xml`, `application/pdf`) — 둘 다 아니면 400.
2. 크기 상한 20MB 초과 시 400
   (앱 전역 문서 업로드 상한 `upload.max-document-bytes`와 동일한 값).
3. 프로젝트가 없으면 404 (기존 `ArtCraftNotFoundException`).
4. **PDF**: `{workspace}/source.pdf`로 그대로 저장. 기존 `source.html`이
   있으면 삭제 (정본 불일치 방지).
5. **HTML**: `{workspace}/source.html`에 원본 보존 → 서버 로컬 headless
   Chromium `--print-to-pdf`로 `{workspace}/source.pdf` 생성.
   Chromium을 찾지 못하면 **503** + 명확한 메시지 (아래 5절).
6. 기존 `source.pdf`가 있으면 덮어쓴다 (버전은 증가하지 않음).
7. SSE `project` 이벤트(`source_uploaded`) 브로드캐스트 + 감사 로그.

### 응답

`200 OK` + `SourceUploadResponse`:

```json
{
  "projectId": "abc123",
  "academyId": 7,
  "version": 3,
  "sourceType": "html",
  "sourcePath": "source.pdf",
  "converted": true
}
```

- `sourceType`: `"html"` | `"pdf"`
- `converted`: HTML→PDF 변환 수행 여부 (PDF 직접 업로드면 `false`)
- `version`: 업로드 시점의 프로젝트 버전. **소스 업로드로 버전은 증가하지 않는다**
  (버전은 `edit`의 낙관적 잠금 카운터 전용).

### 에러

| 상태 | 조건 |
|---|---|
| 400 | 파일 없음·빈 파일·지원하지 않는 형식·20MB 초과 |
| 401 | 미인증 (아래 6절) |
| 403 | STUDENT 등 편집 권한 없음, 타 학원 접근 |
| 404 | 프로젝트 없음 |
| 409 | (업로드가 아님) `edit` 시 `source.pdf` 미등록 — "소스를 먼저 업로드하세요" |
| 503 | HTML 업로드인데 서버에 Chromium 없음 |

## 5. HTML→PDF 변환 (headless Chromium)

`ArtCraftHtmlToPdfConverter` (신규 `@Component`):

- **런타임 탐지**: `artcraft.editor.chromium-path` 설정값이 있으면 그것을 사용,
  비어 있으면 PATH에서
  `chromium` → `chromium-browser` → `google-chrome` →
  `google-chrome-stable` → `chrome` 순으로 탐색.
  찾지 못하면 `ArtCraftCliException(503)` — "관리자가 chromium 패키지를
  설치하거나 `artcraft.editor.chromium-path`에 바이너리 경로를 설정해야 합니다."
- **실행**: 
  `chromium --headless --disable-gpu --no-sandbox --no-pdf-header-footer
  --print-to-pdf={workspace}/source.pdf {workspace}/source.html`
  - 작업 디렉토리 = 프로젝트 디렉토리 (상대 리소스 해석용)
  - 타임아웃 = `artcraft.editor.cli-timeout-seconds` (기본 60초) 재사용
  - exit≠0 → 502, 타임아웃 → 504 (pdfcraft-cli와 동일한 매핑)
  - 변환 후 `source.pdf`가 실제로 생성되지 않았으면 502
- Chromium이 없는 환경(현재 서버 포함 가능)에서는 HTML 업로드가 503이 되며,
  PDF 직접 업로드는 정상 동작한다. 운영 배포 전 서버에 `chromium` 패키지
  설치가 필요하다 (P1).

## 6. 인증/인가 — SecurityConfig 변경 불필요

`SecurityConfig`에 `.requestMatchers("/api/**").authenticated()` catch-all이
이미 존재하므로, `/api/artcraft/projects/{id}/source`는 **자동으로 인증 필수**
가 된다. 별도 규칙 추가가 필요 없다 (실측 확인: `SecurityConfig.java` 460행
부근). 역할 검사는 서비스의 `requireEditorRole` (ADMIN/TUTOR)이 담당한다.

## 7. `edit` 선행조건

`editProject`는 프로젝트 로드 직후 `{workspace}/source.pdf` 존재를 검증한다.
없으면 `ArtCraftSourceMissingException` → **409 Conflict**:

```json
{ "message": "소스를 먼저 업로드하세요: 프로젝트 '...'에 편집 소스(source.pdf)가 없습니다. ..." }
```

## 8. 남은 작업 (P1)

1. 운영 서버에 headless Chromium 설치
   (`apt install chromium` 또는 `chromium-path` 설정) 후 HTML 업로드 실측.
2. `--print-to-pdf` 출력 품질 검증 (2단 조판·밑줄·한글 폰트·mm 여백).
3. 소스 업로드 이력: 현재 `project.json` history에 기록하지 않음.
   필요 시 `source_uploaded` history 항목 추가 검토.
4. 대용량 HTML(외부 이미지·폰트 포함)의 변환 안정성 검증.
5. `output.pdf` 파일명 강제 여부: 현재는 `doc_save`의 `path`를 그대로 신뢰
   (프로젝트 디렉토리 내부로 제한됨). 편집 결과명을 `output.pdf`로 통일할지
   결정 필요 — **주의: 작업 지시 원문에 `edited.pdf`와 `output.pdf` 두 이름이
   혼재한다. 본 문서는 기존 코드·테스트의 관례(`output.pdf`)를 따른다.**
