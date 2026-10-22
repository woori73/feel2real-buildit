# GET 비용·부수효과 감사 (2026-10-22)

범위: 최신 main `7472d91e`와 #853·#854·#855를 합친 코드. 격리 앱의 Spring/proxy 매핑 1098개를 수집하고, 비활성 profile/feature의 Java GET 선언도 추가했다. **627개 GET 경로**를 Java AST 호출 그래프로 조사했다. 재현 가능한 전체 경로·근거·판정은 [get-safety-audit.json](get-safety-audit.json)에 있다. 외부 서비스나 금융기관을 호출하는 감사가 아니다.

MCP 실행 허용 경로는 405개다. 목록에 보이는 서비스 경로와 실행 허용 여부를 구분하며, 새 endpoint는 자동 허용하지 않는다.

| 허용 경로 분류 | 수 | 근거와 한계 |
|---|---:|---|
| 로컬 코드 정적 위험 표지 없음 | 282 | Repository/JDBC DML, 파일 쓰기, HTTP/async 호출 그래프 검사. 동적 dispatch의 안전성을 증명하지는 않음 |
| 기존 S3 읽기 | 47 | `S3Client.getObject/listObjectsV2`. 읽기 요청·전송료와 CPU 비용 존재. 실제 S3는 접속하지 않음 |
| ZIP 목록 임시 파일 | 1 | 20 MiB 이하 UUID 임시 파일만 생성·읽기·finally 삭제. 원본/사업 데이터 변경 없음. 저장소가 S3면 읽기 비용도 존재 |
| MVC 화면 등록 | 35 | SSR 본문과 화면의 기존 권한 검사. 클라이언트가 채우는 데이터는 별도 API 조회 필요 |
| Wiki 서명 프록시 | 27 | 사용자별 기존 workspace만 SQLite mode=ro/query_only. 별도 Python 격리/쓰기 거부 시험 |
| AcademyCraft 서명 프록시 | 13 | 기존 Gateway 사용자 바인딩·학원 권한 재사용. 별도 backend의 모든 동적 부수효과를 로컬 트랜잭션이 막지는 못함 |

## 실행 목록에서 제거한 GET

| 경로 | 확인된 동작 |
|---|---|
| `/api/admin/publishing/maintenance` | TTL 만료 시 status→expireIfDue→startDrain→persist/settle이 상태 파일을 저장 |
| `/api/admin/chart/settings` | 기본 설정이 없으면 `getOrCreateSettings`가 저장 |
| `/api/admin/newstart/consultations/{consultationId}` | 관리자 열람 상태 갱신·저장 |
| `/api/newstart/consultations/{consultationId}` | 회원 열람 상태 갱신·저장 |
| `/api/academies/{handle}/midterm-campaign/live-updates` | 외부 wiki HTTP 호출 |
| `/api/academies/{handle}/midterm-campaign/showcase` | 외부 wiki HTTP 호출 |
| `/api/academies/{handle}/midterm-campaign/wiki-sources` | 외부 wiki HTTP 호출 |
| `/api/academies/{handle}/midterm-campaign/wiki-sources/{sourceId}/preview` | 외부 wiki HTTP 호출 |
| `/api/tutor/lms/{handle}/midterm-campaign/wiki-candidates` | 외부 wiki HTTP 호출 |

최종 #854의 `/api/dashboard/assistant/v1/drafts` GET도 stale EXECUTING을 UNKNOWN으로 저장하는 부수효과가 있으므로 기존 MCP 미허용을 유지한다. 실제 MCP 요청 거부를 통합 경계 시험에 포함했다.

기존 서비스 API는 유지했다. MCP 정책만 제외했으며, 운영 사이트에서 위 API를 실호출하지 않았다. GET이라는 이름만으로 비용·부수효과가 없다고 판단하지 않는다.

재무의 기존 66개 정책은 제거하고 `/api/mcp/finance/summary` 하나만 허용한다. 명시한 허용 사업체의 월 합계 8개 필드만 제공한다. #853의 수집 상태/원문/증빙/계좌, 거래·미수 학생 상세, 확정 API는 제공하지 않는다. 입시 자료는 관리자 지식·재색인·통계·SSR을 제외하고 사용자 본인의 상담 history만 유지한다.

## 재현

```sh
pip install -r scripts/ai-agent-studio/requirements-audit.txt
# /tmp/f2r-route-inventory.json: 격리 MySQL HTTP 시험 또는 protocol-qa.py가 생성
python scripts/ai-agent-studio/audit-get-safety.py /tmp/f2r-route-inventory.json
```

감사 결과 허용 경로에 DB 쓰기·remote/async 표지는 0개다. 파일 쓰기 표지 1개는 위 ZIP 목록의 제한된 임시 파일이며, 이 예외를 숨기지 않는다. S3/프록시·SSR은 위와 같이 별도 검토 대상으로 분류했다. 정적 스크리닝은 실행 증명이 아니며, 외부 API 변경·리플렉션·동적 Bean·새로운 저장 경로는 후속 검수 대상이다. 기존 권한 검사, 정확한 GET target의 15초 위임 JWT, 로컬 read-only/rollback, 사용자 60회/분과 JVM 동시성 제한을 함께 유지한다. 운영 전체 자료나 모든 허용 GET을 실제 호출했다고 주장하지 않는다.


## 병행 키워드 조사 12개 후보의 수동 대조

[별도 키워드 조사](GET_SIDE_EFFECT_SURVEY.md)는 AST 호출 그래프와 방식이 달라 문자열·다른 메서드까지 후보로 잡는다. 현재 정책 405개로 재생성했고 다음과 같이 코드 본문을 대조했다. 외부 API를 호출하는 검증은 하지 않았다.

| 후보 | 실제 조회 경로의 동작 |
|---|---|
| midterm live-videos | listPublic의 Repository 조회와 영상 URL/임베드 문자열 반환. 서버의 해당 GET에서는 외부 URL 요청 없음 |
| newstart jipijigi inventory | build의 Repository count와 capability boolean/설명 문자열. embedding/LLM 호출 없음 |
| chart regions | queryForList SELECT와 메모리 Map 집계. DB insert/update/save 없음 |
| studio files text | PDF는 byte 파싱, ZIP은 위 임시 파일 예외. 원본 변경·압축 해제·OCR 호출 없음 |
| wemade catalyst events/policy, events, gap factors/timeline, ownership persons, valuation components (7개) | Repository에 저장된 자료와 JSON/Map의 deterministic 계산·sourceUrl 반환. 해당 getter 경로에서 HTTP 요청 없음 |
| sitemap.xml | 공개 학원/글 Repository 조회와 XML/URL 문자열 작성. 링크를 서버에서 요청하지 않음 |

이 대조는 정적 코드 근거이며 모든 동적 실행을 증명하지 않는다. 기존 API 수정 때 다시 조사하고, 확인된 유지보수 파일 저장 GET은 현재 정책에서 제외했다.
