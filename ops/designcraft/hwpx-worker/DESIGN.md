# HWPX Python Worker 설계

> 작성일: 2026-10-15 · 상태: 설계 (격리 검증 후 별도 브랜치 PR 예정)
> 전제: PR #687 병합 전까지 repo 변경 없음. Claude 브랜치(`fix/designcraft-editor-frontend`) 불변.

## 1. 목표

HWP/HWPX 업로드 → 지정 셀 수정 → HWPX 저장·다운로드 기능을
기존 DesignCraft API·화면과 충돌 없이 제공한다.

**하지 않는 것**
- 기존 `/api/publishing/*` API 변경 없음
- 기존 출판 편집기 화면 변경 없음
- 기존 PDF 출판 기능 변경 없음 (중복 개발 금지)
- 운영 배포·AWS 변경·운영 DB 변경 없음 (이번 단계)

## 2. 아키텍처

```
[클라이언트]
        │  REST: /api/hwpx/*  (신규, publishing과 분리)
        ▼
[Spring Boot: domain/hwpx 패키지]  ← 신규 패키지, 기존 코드 불변
  ├─ HwpxJobController     # jobs CRUD, upload, download
  ├─ HwpxJobService        # 작업 생성·조회·소유자 격리
  ├─ HwpxWorker            # @Scheduled 폴링 (publishing 패턴 재사용)
  └─ HwpxCliExecutor       # Python CLI 호출 (DesignCraftCliExecutor 패턴)
        │  job 생성 (DB)
        ▼
[hwpx_jobs 테이블] ← HwpxWorker 폴링 (10초, FOR UPDATE SKIP LOCKED)
        │  Python CLI 실행 (작업 디렉터리 격리)
        ▼
[hwpx_artifacts 테이블] → 다운로드 API
```

**독립 Python Worker** (`hwpx-worker/`):
```
hwpx-worker/
├── hwpx_cli.py          # CLI 진입점 (argparse)
├── hwpx_editor.py       # python-hwpx 래퍼 (open/edit/save/validate)
├── requirements.txt     # python-hwpx==6.8.0
└── Dockerfile           # python:3.12-slim 기반
```

Spring은 Python을 직접 import하지 않고 CLI 프로세스로 호출한다
(DesignCraft CLI 0.6.0 패턴과 동일). Python 의존성이 JVM과 분리된다.

## 3. CLI 계약

```bash
# 셀 수정
python hwpx_cli.py edit \
  --in /work/input.hwpx \
  --edits /work/edits.json \
  --out /work/output.hwpx

# 검증
python hwpx_cli.py validate --in /work/output.hwpx

# 정보 조회 (표/단락 수)
python hwpx_cli.py info --in /work/input.hwpx
```

`edits.json` 형식:
```json
{
  "edits": [
    {"table": 0, "row": 1, "col": 2, "text": "새 내용"},
    {"table": 2, "row": 0, "col": 0, "text": "제목 변경"}
  ]
}
```

종료 코드: 0=성공, 1=입력 오류, 2=편집 실패, 3=검증 실패.
표준출력은 JSON 결과 (`{"ok": true, "output": "...", "tables": 9, ...}`).

## 4. DB 스키마 (Flyway, 신규)

```sql
-- hwpx_jobs: HWPX 편집 작업
CREATE TABLE hwpx_jobs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tutor_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  source_format VARCHAR(10) NOT NULL,  -- hwp | hwpx
  status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',  -- QUEUED|RUNNING|DONE|FAILED|CANCELLED
  progress TINYINT NOT NULL DEFAULT 0,
  current_step VARCHAR(100),
  work_dir VARCHAR(500),
  input_file VARCHAR(500),
  output_file VARCHAR(500),
  error TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_status (status)
);

-- hwpx_artifacts: 산출물 (HWPX)
CREATE TABLE hwpx_artifacts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  format VARCHAR(10) NOT NULL,  -- hwpx
  file_path VARCHAR(500) NOT NULL,
  file_size BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## 5. REST API (신규, `/api/hwpx`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | /api/hwpx/jobs | HWP/HWPX 업로드 + 셀 수정 요청 → 202 QUEUED |
| GET | /api/hwpx/jobs | 내 작업 목록 |
| GET | /api/hwpx/jobs/{id} | 작업 상태 조회 |
| GET | /api/hwpx/jobs/{id}/download | 결과 HWPX 다운로드 |
| POST | /api/hwpx/jobs/{id}/cancel | 작업 취소 |
| DELETE | /api/hwpx/jobs/{id} | 작업 삭제 |

모든 엔드포인트 JWT 인증 + tutor_id 소유자 격리 (publishing 패턴 동일).

## 6. 검증 계획 (격리 환경)

1. 모두의창업 사업계획서 HWP로 info → 표 9개·단락 28개 확인
2. edit → Q1·Q2 셀 수정 → validate 통과
3. 원본 대비 표 수·병합 셀 수·이미지 수 동일 확인
4. HWP 입력 → HWPX 출력 변환 손실 0건 (이미 검증됨)

## 7. 단계별 진행

- [x] 기술 검증 (2026-10-15 완료)
- [ ] Python Worker 구현 + 격리 검증 (현재)
- [ ] PR #687 병합 대기
- [ ] 별도 브랜치에 Spring 통합 코드 PR
- [ ] CI PASS 후 병합 승인 요청
