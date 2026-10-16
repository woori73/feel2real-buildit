# DesignCraft 0.8.0 통합 검증 · 2026-10-16

## 판정

코드 수정·격리 CLI 실측 완료. 전체 사용자 E2E 완료 아님. 운영 변경·main 병합 없음.
로그인→Spring/MySQL→실제 Muse Spark→승인→PDF/HWPX 다운로드 전체 흐름과 1440/390px 화면은 미검증이며 병합 보류가 필요하다.

## 1. 원본·버전·라이선스

- 원본: https://github.com/storytold/designcraft . CLI `--help`의 원본 링크, 배포 아카이브 README, 공식 Cargo workspace의 repository·라이선스와 일치.
- MIT OR Apache-2.0. 배포 폰트 고지는 별도 OFL이며 한국어 템플릿 폰트는 Noto Sans KR/OFL.
- 기존 저장소 패키지: `ops/designcraft/designcraft-cli-0.6.0.tar.gz.part-*`를 합쳐 추출, `--version` = 0.6.0. 실행파일 SHA-256 `64a467a06dceac46a7cca5990e46946faa1027d3b33228f1c42caaff9e33da87`.
- 이전 템플릿 recipe는 0.5.0 생성 기록. 기존 배포 바이너리의 원본 소스 SHA·EC2 실제 설치 버전은 기록/접근 증거가 없어 미확인.
- 공식 latest 안정판: v0.8.0, prerelease=false, tag commit `11a51d5b665aec8d6e675bd107a8a01514f10d00`.
- Linux x86_64 아카이브 공식 SHA-256: `96a835fd89ebf6970b7dec93652e1b2f081123a69c78e19998ca6c84ca322d77`. 내려받은 파일과 일치. CLI 실행 및 설치 스크립트 재설치 실측 성공.
- 공식 릴리스: https://github.com/storytold/designcraft/releases/tag/v0.8.0

## 2. 구조·업데이트

Spring PublishingWorker는 템플릿/Document Fonts를 작업 디렉토리에 배치한다. PublishingEditService와 PdfExportService는 Java ProcessBuilder로 Rust CLI를 실행한다. `run --in --cmd`, `script - --in --save`, PNG page export 계약은 0.8.0에서도 호환된다.

Python HWPX Worker(`python-hwpx==6.8.0`)는 별도 HwpxWorker/HwpxCliExecutor 경로이며 DesignCraft native 파일 변환기가 아니다. 기존 HWP/HWPX를 열어 표 셀을 수정하고 HWPX로 저장한다. 출판 편집기 산출물 목록에는 PDF/EPUB만 연결돼 있다.

0.8.0에는 원자적 파일 저장/출력·복구 보존·선행 빈 문단 반입 보완·CR 문단 처리·표 병합/선택 보완·MCP 재접속·UTF-8 BOM script 지원 등이 포함된다. 공식 릴리스에 명시된 별도 보안 패치/CVE는 이번 조사에서 확인하지 못했다. Rust 소스 빌드 최소 1.95, 서버 실행은 Linux ELF CLI이므로 Rust/JDK 추가 설치가 필요 없다(기존 Spring은 JDK 21).

## 3. 재현한 결함과 수정

| 결함 | 근거와 수정 |
|---|---|
| 짧은 한글 문장의 과도한 자간 | 기존 ko-body의 fullyJustified가 마지막 줄까지 양끝 정렬. 기본 템플릿을 leftJustified로 수정 |
| DOCX 표의 한글이 PDF에서 빠짐 | 셀의 Basic Paragraph에 한글 폰트가 없음. 기본 문단 스타일에 Noto Sans KR 지정 |
| 기존 제목 크기 수정이 새 스타일 복제만 생성 | CLI create는 기존 이름에 2를 붙임. 기존 스타일용 style.paragraph.edit를 허용·정규화하고 계획에 스타일 목록/사용 구분을 제공 |
| AI가 문서를 모르고 계획 | 제목·템플릿만 보냈음. 읽기 전용 story.get 맥락을 총 12000자로 제한해 포함 |
| AI 명령 계획에 홈페이지 카피라이터 system prompt 사용 | 공유 transport는 유지하되 PublishingAiService에서 문서 명령 계획용 system prompt를 지정. 다른 서비스 변경 없음 |
| 문단/문장 직접 수정 미구현 | document.replaceText를 서버에서 고유 원문 일치 검사 후 story.replaceRange UTF-8 범위로 변환. 추가/삭제 가능, 한 계획당 한 구간 |
| 선택 상태 유실·계획 중간만 적용 | 명령별 새 CLI 프로세스 대신 승인 명령 전체를 한 script로 실행, 성공 후 정본 원자 교체 |
| 실행 취소 무효 | CLI undo 이력은 새 프로세스에 없음. 이전 스냅샷 파일 복원으로 수정 |
| 스냅샷 덮어쓰기 | 초 단위 파일명 충돌. 고유 임시 파일명을 사용 |
| stderr가 JSON을 오염·큰 script 파이프 교착 가능 | stdout/stderr 분리, 동시 drain·stdin 쓰기, 출력 메모리 제한, timeout 시 자식 종료 |
| 원고 반입 실패 안내 소실 | 지속 오류 배너와 재시도 추가. PR #715 AI 패널/모바일 시트 변경 그대로 재사용 |
| HWPX 병합셀 잘못 집계·다른 셀 수정 | 모든 cell.address를 병합으로 세던 오류 및 동일 span 인접 셀을 합치던 탐색 제거. 실제 anchor address·고유 span으로 처리 |

기존 '조판 엔진 실행 실패/반입 실패' 운영 사례 자체의 원인은 미확정이다. 0.6.0에서도 동일 원고 반입이 성공했다. 운영 호스트의 실행파일·권한·설정·로그를 직접 확인하지 않아 'CLI 미설치가 확정 원인'이라고 단정하지 않는다. 현재 deploy-ec2 워크플로에는 CLI 설치 단계가 없다.

## 4. 변경 파일

- publishing service: DesignCraftCliExecutor, PublishingEditService, PublishingAiService, CommandAllowlist 및 PublishingProperties 문서.
- 웹: publishing_editor.js/css, designcraft/editor.html. #715의 5개 파일 변경 재사용 포함.
- 3개 native 템플릿의 ops/JAR 양쪽 정본 및 recipe 정렬 설명.
- hwpx_editor.py·Worker 회귀 테스트, Java 회귀 테스트, build-check/DesignCraft QA 테스트 등록.
- `ops/designcraft/install-engine.sh`, `qa/test_native_pipeline.py`, `qa/requirements-native.txt`, API/FLOWS와 본 보고서.

## 5. 실제 문서 검증

0.6.0과 0.8.0 각각 TXT/MD/RTF/DOCX = 8개 조합. 수정한 A4 템플릿으로 실제 CLI 반입 → 스타일 적용 → 한글 문장 UTF-8 범위 수정 → PDF 저장 → PNG 렌더 → PDF 재열기/한글 텍스트 확인 모두 PASS. DOCX 2×2 표의 항목·금액·교재·20000 보존 확인. 대표 PNG를 직접 검수해 짧은 문장 정렬과 한글 표가 정상인 것을 확인했다.

4페이지 템플릿으로 4페이지 출력(빈 후속 페이지 포함). 과도한 페이지 자동 제거·장문/복잡한 중첩 표·이미지·병합 DOCX 표·HWP 원본 실물은 미검증. PDF/X-4·PDF/A-2b 적합성 인증은 이번 출력 테스트가 증명하지 않는다(standard=none 검증).

HWPX Worker는 생성한 한글/인접 병합 표로 수정·저장·재열기·다른 셀 보존 3개 회귀 테스트 PASS. HWPX 전용 CLI info/edit 경로도 실행. 한컴 프로그램에서 시각적으로 재열기는 미검증. DesignCraft→HWPX 내보내기는 오픈소스/현재 연동 모두 지원하지 않아 미구현.

재현:
```bash
bash ops/designcraft/install-engine.sh /ABS/EMPTY/engine-0.8.0
python -m venv /ABS/qa-venv
/ABS/qa-venv/bin/pip install -r ops/designcraft/qa/requirements-native.txt
/ABS/qa-venv/bin/python ops/designcraft/qa/test_native_pipeline.py --cli /ABS/EMPTY/engine-0.8.0/bin/designcraft-cli --out /ABS/qa-results
```

## 6. AI·브라우저·테스트

- Java 29건 PASS: 실제 shell 프로세스 실패/JSON 분리/원자 저장, 스냅샷 충돌, 승인 전 미적용, 승인 후 호출, UTF-8 원문 교체·중복 거부, 명령 batch, 이전 버전 undo 포함.
- JS 계약 15건 PASS: #715 패널과 실제 API 어댑터. UI DOM 수동/브라우저 결과를 대신하지 않는다.
- Python HWPX 회귀 3건 PASS.
- AI 테스트는 mock transport로 계획/승인 계약을 검증. 실제 Muse Spark 응답은 API 키/인증이 없어 미검증. API 키·비밀번호 수집 또는 로그인 우회 없음.
- 운영 `/designcraft/editor`: 비로그인 소개/로그인 화면 200 확인, 캡처 1363px. `/designcraft`와 홈페이지 전체 운영 회귀는 미검증.
- PC 1440px·모바일 390px 수정 화면 캡처 없음. 로컬 Chromium CDN이 Site Unavailable, 클라우드 브라우저는 file:// 접근 보안 정책으로 차단. 우회하지 않음.
- 로컬 MySQL 8.0.46 다운로드·격리 초기화는 성공했으나 서버가 UNIX socket Operation not permitted로 중단. Spring+MySQL 실제 로그인 E2E 미실행.
- 테스트 JVM은 이 환경에서 동적 attach가 막혀 ByteBuddy agent를 테스트 시작 인자로 넣어 검증(저장소 JVM 설정 변경 없음).
- CI 결과는 PR의 check-runs를 별도로 확인. 로컬 PASS를 MySQL CI 성공으로 혼동하지 않는다.

증거: `designcraft-080-evidence/`의 native-results.json, DOCX before/after PNG, 운영 로그인 화면. JSON의 절대 경로는 이번 격리 환경 경로이며 운영 파일이 아니다.

## 7. 운영 준비 및 롤백 (명령 실행 전 별도 승인)

읽기 전용 우선 확인: `uname -sm`, `ldd --version`, 설정된 CLI의 `--version`, `test -x`, `namei -l`, `df -h`, `free -m`, systemd 서비스 사용자와 PUBLISHING_CLI_PATH/PUBLISHING_TEMPLATE_DIR의 해당 항목, 관련 오류 로그. 환경파일 전체/키는 출력하지 않는다. 이번에는 EC2 접속 수단이 없어 미실행.

제안 경로 `/opt/designcraft/releases/0.8.0/bin/designcraft-cli`. 승인 후 설치 스크립트를 **존재하지 않는 버전별 경로**로 실행하고 `--version`·SHA·서비스 사용자 실행을 확인한다. 기본 `/opt/designcraft/designcraft-cli`는 호환을 위해 유지하며 새 환경변수로 명시 선택한다. 필요한 라이브러리는 실측 libgcc_s, libm, libc, ELF loader. 배포 대상 glibc 버전은 별도 호환 확인 필요. Rust·GUI 패키지·유료 인프라는 불필요. HWPX는 별도 Python 3.12 venv/requirements 설치가 필요하며 운영 스위치는 승인 전 유지한다.

템플릿·OFL/Noto 폰트를 `/opt/designcraft/templates/<code>/Document Fonts`에 서비스 사용자가 읽을 수 있게 배치한다. 서비스 사용자는 작업 저장소에만 쓰기 권한을 갖는다. 버전별 CLI/템플릿을 함께 고정해 현재 환경파일·구버전 바이너리·기존 프로젝트를 백업한 후에만 전환한다. deploy-ec2 자동 설치/설정 변경은 추가하지 않았다. 운영 배포 자체가 자동 트리거될 수 있으므로 이 PR을 승인 없이 병합하지 않는다.

환경변수/JAR 전환 후 Spring 재시작 필요. CLI는 요청별 프로세스이므로 별도 daemon 없음. 0.6→0.8 파일 열기는 샘플에서 성공했지만 0.8 저장본을 0.6에서 역호환한다고 보장하지 않는다. 롤백은 구 JAR·구 CLI 경로·구 템플릿·전환 전 프로젝트 파일 사본을 함께 복원하고 서비스를 재시작한다. DB/Flyway 변경 없음.

디스크: 공식 아카이브 약 68.2MiB, 전체 설치본 약 144MiB. 기존 버전과 신규 버전/압축본/백업을 함께 둘 여유 필요. 한국어 글꼴은 템플릿당 약 12MiB이며 현재 구조는 작업마다 복사한다. 스냅샷/PNG/PDF 저장량은 문서 수에 비례하며 정리정책은 별도 필요. 작은 4페이지 DOCX PDF 프로세스 실측 약 0.038초/최대 RSS 44MiB이나 대문서·동시성 부하 결과는 아니다.

## CI 과정에서 추가 재현한 QA 결함

첫 DesignCraft QA #26에서 veraPDF 비규격 PDF 판정 후 `grep | head`의 SIGPIPE가 pipefail에 걸려 기대한 exit 1 대신 exit 2로 처리됐다. grep -q 앞 echo 파이프도 같은 조기 종료 위험이 있어 here-string으로 바꾸고, 상세 출력은 끝까지 소비하는 sed로 변경했다. 규격 기준을 낮추거나 검사 건너뛰기 없음. 큰 실패 리포트 mock으로 wrapper 종료코드 1 회귀 확인 후 실제 CI를 재확인한다.

## 8. 남은 필수 작업·승인

- 실제 사용자 로그인은 사용자 직접 인증/승인이 필요. 운영에서는 읽기만 하며 테스트 업로드·수정은 하지 않는다.
- 격리 Spring/MySQL 환경에서 1440/390px 로그인→업로드→AI 실제 계획→승인→미리보기→PDF 다운로드/복구 E2E 및 API/로그/콘솔 증거 확보.
- HWPX 통합 내보내기는 문서 모델/표/레이아웃을 보존할 별도 변환기 설계·구현 필요. 기존 셀 편집 Worker 성공으로 대체할 수 없음.
- 표 추가·수정·마스터 배치/목차의 AI 정확도, 대문서, 실제 Muse Spark는 미검증. 원본 CLI에 관련 명령이 있어도 BuildIt allowlist에는 추가하지 않았으며 임의 성공 처리하지 않음.
- CI build/mysql-integration-test 결과 확인 후에도 위 E2E가 남아 있어 draft/병합 보류. 운영 설치·설정 변경·배포는 구체 실행계획 검토 후 별도 승인.
