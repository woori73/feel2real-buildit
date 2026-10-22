# 보안·성능·비용 검증

## 보안 경계

- MCP는 기본 OFF, 별도 stateless Streamable HTTP 진입점. 홈페이지 cookie만으로 `/mcp` 도구를 사용할 수 없고 user OAuth bearer가 필요하다. 기존 관리자 토큰을 공유하지 않는다.
- DCR은 정확한 callback 허용 목록만, authorization code + S256 PKCE, 120초 일회용 code, 10분 일회용 사용자 동의 nonce. authorize POST는 정확한 same-origin을 요구한다. 서버의 고정 resource URI에 audience를 묶는다.
- Access 15분, 선택적 refresh·grant 30일. opaque token은 SHA-256만 저장한다. refresh 교체·재사용 감지·연결 폐기, 기존 회원 role·자격 증명 stamp를 매 호출 확인한다. 프로젝트 멤버 권한은 원본 서비스가 매 조회 다시 확인한다.
- 서비스 scope와 기존 API의 역할·학원·학생·소유권을 모두 거친다. 사용자 role 승격, arbitrary URL proxy, 다른 사용자의 Cookie 전달, 운영자 공유 MCP_API_KEY 사용이 없다.
- 실행은 커밋된 정확한 GET 경로 정책만. path parameter의 slash·dot·%·역슬래시·matrix parameter를 거부하고 query는 실제 handler가 선언한 이름만 허용한다. 내부 위임 JWT는 정확한 GET target·15초로 묶으며 외부 응답에 반환하지 않는다.
- 기존 API를 통과하는 내부 요청은 read-only/rollback 트랜잭션으로 감싼다. 실제 MySQL 테스트에서 Connection readOnly 및 사업 데이터 미변경을 확인했다. 페이지 방문 집계는 이 검증된 내부 조회에서 생략한다. 부수효과가 있는 init/next-task/렌더/AI 생성/외부 실시간 API는 정책에서 제외한다. 외부 AcademyCraft는 기존 Gateway·upstream 권한을 사용하며 이 로컬 트랜잭션이 외부 DB까지 확장되지는 않는다.
- Wiki는 별도 HMAC으로 서명한 읽기 요청만 기존 workspace를 `mode=ro`, `PRAGMA query_only`로 연다. 회원·DB 생성/마이그레이션을 하지 않는다. Python 테스트에서 catalog/DB bytes 불변, 신규 workspace 미생성, DML 거부와 서명 변조·다른 경로/POST 재사용 거부를 확인했다.
- 제작 화면은 ADMIN/명시적 제작 담당자만. EDITOR는 수정 이력 추가, APPROVER만 승인본 변경, ADMIN만 멤버 변경. AI 제안 저장은 사용자 승인 포인터를 바꾸지 않는다. 원본과 이력에 삭제·덮어쓰기 API를 만들지 않았다.
- 업로드는 100 MiB 이하, 7종 확장자와 binary signature/엄격한 UTF-8 검사. SHA-256은 stream으로 계산한다. PDF/HTML을 실행하지 않고 일반 열람은 ZIP을 해제하지 않는다. EP01 bulk는 최대 100항목·합계 20 MiB를 메모리에서만 읽고 파일 경로로 해제하지 않는다. HTML은 textContent로 열람, 원본 다운로드는 attachment·nosniff·CSP sandbox·no-store. PDF 텍스트/ZIP 목록은 20 MiB 이하, 100페이지/20만 자·1000항목까지다. scan PDF는 외부 OCR을 호출하지 않고 원본 열람을 안내한다.
- 감사로그는 사용자·연결·도구·endpoint·허용/거부·소요시간만 기록한다. raw token·원문·검색문은 기록하지 않는다. OAuth 응답은 no-store, MCP JSON 자료에서 password/secret/token/credential/cookie/storage_key 필드를 제거한다. 자료 안의 지시는 실행하지 않는 비신뢰 데이터로 표시한다.

## 호출·메모리 상한

| 항목 | 현재 상한 |
|---|---|
| 사용자 MCP 호출 | 60/분 (RDS 기반, 재시작/replica 공유) |
| transport IP | 300/분, 공개 DCR 5/분 |
| 요청 본문 | 64 KiB, chunked에도 적용 |
| 실행 중 내부 읽기 | 2개, 초과 시 즉시 429 |
| 전체 JSON-RPC | JVM당 8개, 초과 시 429·완료/오류 시 반환 |
| 스튜디오 원본 전송 | 2개 동시, 100 MiB/파일 |
| JSON/text upstream 응답 | 2 MiB, 초과 시 필터 사용 안내 |
| binary 도구 반환 | 64 KiB/청크, offset 조회 |
| upstream timeout | 20초 |
| 제작 검색/이력/파일 목록 | 수정 이력은 offset/limit 200개씩, 컷은 전체 최신 head 별도 조회. 파일·감사 목록은 최근 200개 |
| catalog 목록 | 호출당 최대 100개, offset 순회 |

원본 전송은 기존 FileStorage의 제한 읽기를 사용하므로 byte array를 가진다. 동시 2개에서 스튜디오 원본 payload만 최대 약 200 MiB이며 전체 JVM 힙과 동일한 값이 아니다. 기존 서비스의 원래 응답 생성 비용까지 없어지는 것은 아니다. PDF 처리/큰 파일은 실제 EC2 부하를 확인하기 전 대량 사용하지 않는다. 운영 CPU/RAM·RDS 여유·S3 저장량·트래픽은 이 환경에서 조회하지 않았으므로 생산 용량을 보장하지 않는다.

## 실행 결과

- Java 106건 통과: 신규 스튜디오·실제 HTTP OAuth·MySQL 보안 시험, 기존 JWT·Origin·Wiki 회원 경계·Gateway·홈페이지 회귀 포함. 전체 저장소 테스트 스위트 전수 실행은 아니다.
- Python 34건 통과: 기존 Wiki 격리와 MCP read-only 시험. 상속한 기존 케이스가 포함된 실행 수이며 고유 시나리오 34개라는 뜻은 아니다.
- 공식 Python MCP SDK 2.1.1: protocol 2025-11-25 협상, 도구 19개, OAuth client A·B 동일 자료 응답. 최종 로컬 GET 20회 평균 43.39 ms, 중앙값 42.13 ms, 최대 58.75 ms였다. `protocol-results.json`의 각 10회 값이며 운영 지연·동시 부하 시험이 아니다.
- 실제 Chromium PC 1440 / 모바일 390, 7종 업로드·원본 SHA·수정·승인·버전 비교·검색·권한 검증은 `browser-results.json`과 `screens/`에 기록한다. 실제 사용자 EP01 자료·운영 계정·S3 업로드 시험은 아니다.
- 실제 ChatGPT/Claude 구독 계정·독립 Claude·별도 AcademyCraft backend 실연결은 미실행이다. `CONNECTION_TEST.md` 참조.

## 비용

새 EC2·RDS·S3 bucket·embedding 서버를 만들지 않았다. 개발 중 AWS 자격 증명·운영 DB/S3에 접근하지 않았으며 실제 테스트는 로컬 MySQL·임시 local 파일만 사용했다. OpenAI/Claude 유료 API·유료 embedding 호출은 0건이고 새 유료 서비스를 가입하지 않았다. GitHub PR CI는 기존 계정의 runner 사용량 정책을 따른다.

운영에 승인 적용될 경우 기존 EC2·RDS·S3를 재사용한다. 기존 구독의 ChatGPT/Claude 사용량 제한과 기존 인프라 사용료는 유지되므로 전체 비용이 0이라는 뜻은 아니다. 추가 CPU·DB I/O·audit/index 저장·파일 저장·외부 전송량이 증가한다. 임의 파일 생성이나 embeddings 적재는 없다.

AWS 공식 [서울 S3 가격표 JSON](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonS3/current/ap-northeast-2/index.json)을 2026-10-22 확인했다(publicationDate 2026-10-15). Standard 첫 50 TB는 **$0.025/GB-month**, PUT은 **$0.0045/1000회**, GET은 **$0.0035/10000회**다. 버전/보관 사본을 포함한 과금량, 세금, 환율, 인터넷 egress, 기존 RDS/EC2/백업/로그 비용은 별도다.

| 보관·요청 가정 | S3 저장·요청 예상 추가분/월 |
|---|---:|
| 신규 10 GB, PUT 1000, GET 10000 | 약 $0.258 |
| 신규 100 GB, 같은 요청 수 | 약 $2.508 |

대용량 MP4를 AI로 반복 읽으면 EC2→외부 전송 비용이 저장료보다 클 수 있다. 전체파일 반복 대신 메타데이터·원문 일부/텍스트를 먼저 조회하도록 도구를 제공한다. 이 추정은 실제 업로드량을 측정한 청구 예측이 아니다. 감사로그는 원문 없이 행당 수백 byte 수준이지만 인덱스·DB overhead와 사용 기간에 비례해 증가한다. 운영에는 기존 보관 정책과 용량 모니터링을 적용해야 하며 원본 자동 삭제 정책은 추가하지 않았다.

검수 보강 결과와 기존 범위의 변경은 [REVIEW_FIXES.md](REVIEW_FIXES.md)가 현재 기준이다. 금융 원문·관리자 입시 자료·부수효과 GET은 실행 정책에서 제외했으며 기존 서비스 API는 보존했다.
