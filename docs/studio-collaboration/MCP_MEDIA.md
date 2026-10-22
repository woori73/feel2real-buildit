# MCP 문서·이미지 전달 — 실제 판독 시험 전 단계

기존 `/mcp`는 읽기 전용으로 유지한다. 기존 19도구에 `studio_document`, `studio_image`만 추가해 21개이며 모두 readOnlyHint=true/destructiveHint=false다. 후보 등록·삭제·승인·배포 도구는 없다. 운영 설정 `app.mcp.enabled=false`를 변경하지 않는다.

- `studio_document({fileId,offset?,limit?})`: 프로젝트 권한을 기존 GET API에서 검사하고 파일명·원본 SHA·본문을 제공한다. HTML의 내장 base64 이미지·스크립트·스타일·폼을 제외한 본문, Markdown/JSON/MD5 텍스트, PDF 처음 100페이지의 텍스트다. 원본 20 MiB·본문 20만 자, 1회 최대 2만 자이며 페이지 조회한다. 스캔 PDF OCR·모델 호출 없음. 기존 JSON 마스킹을 적용한다. 목록 API의 기존 limit100을 확대하지 않고 문서의 문자 limit에만 20000 상한을 둔다.
- `studio_image({fileId})`: 기존 `studio:read` + 사용자 별도 동의 `originals:read` + 현재 프로젝트 권한을 모두 검사한다. 원본 PNG를 변경하지 않고 화면용 JPEG를 메모리에서 생성한다. 입력 20 MiB/4천만 화소·최대1024px·100 KiB, 동시 생성2건이다. 메타데이터 text 블록(원본 SHA/파생본 SHA/크기/근거 URL)과 **실제 `type=image` 콘텐츠 블록**을 반환한다. 기존 원본 청크 API는 그대로다. 파생본은 저장하지 않고 임의 URL·저장소 키를 받지 않는다.

별도 동의가 없는 토큰은 이미지 처리 전에 거부한다. 이미지의 개인정보는 마스킹할 수 없으므로 기본 선택 해제와 민감정보 경고를 유지한다. 원본 bytes 동의 범위를 이미지 파생본까지 화면에 명시했다. 실제 학원·프로젝트 권한은 동의를 대신하지 않는다. 원본 SHA와 JPEG 파생본 SHA를 혼동하지 않는다. 일반 API JSON이 `type=image`라고 주장해도 이미지 블록으로 승격하지 않고, 서버의 지정된 내부 결과 타입만 콘텐츠 블록으로 변환한다.

공식 [MCP 2025-11-25 Tools 규격](https://modelcontextprotocol.io/specification/2025-11-25/server/tools)의 image 콘텐츠를 따른다. [OpenAI 커스텀 MCP 연결 안내](https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt)는 계정/워크스페이스 권한 및 HTTPS/지원 터널을 요구한다(2026-10-22 확인). 규격 적합성은 제품에서 모델이 이미지를 직접 판독한다는 보장이 아니다. 기존 base64 텍스트 청크 성공도 이미지 판독 증거가 아니다.

사용자가 격리 HTTPS·두 제품 계정 미준비를 확인했으므로 **ChatGPT 실제 판독 NOT TESTED, Claude 실제 판독 NOT TESTED**다. 외부 모델 API 호출 0이다. 로컬 Java/공식 SDK 검증은 이와 별개로 기록한다. 실제 연결 후 같은 문서의 본문 내용과 원본 SHA, 동일 이미지에서 메타데이터에 없는 세부를 각각 판독하고 두 제품별 결과/클라이언트 버전을 기록해야 한다. 이미지가 도착하지 않거나 텍스트만 보이면 실패로 기록하고 운영 활성화하지 않는다. 비인증 공개 원본 URL을 대안으로 만들지 않는다.
