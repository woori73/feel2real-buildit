# 동일 자료 연결 검증

## 이미 실행한 시험

`protocol-results.json`은 공식 Python MCP SDK 2.1.1로 **독립적인 OAuth client A·B**를 등록하고 각각 동의·PKCE 교환·initialize·tools/list·tools/call·search·쓰기 거부를 실행한 결과다. 두 연결이 같은 사용자·같은 자료 ID를 읽어 반환한 본문의 SHA-256이 같음을 검증했다. ChatGPT/Claude callback URI를 사용한 프로토콜 시험이며 **실제 두 제품의 구독 계정 연결 성공을 뜻하지 않는다.**

JUnit은 실제 HTTP OAuth 동의·PKCE 오류·일회용 code·token 만료·폐기·refresh 재사용 폐기·회원 권한 폐기·타 사용자 거부·경로 이동/쓰기 거부·실제 MySQL 읽기 전용 및 rollback을 확인한다. 모든 자료는 격리 DB의 합성 검증 자료다. 실제 EP01 원본은 제공되지 않았으며 운영에 등록하지 않았다.

## 실제 ChatGPT·Claude에서 수행할 절차 (미실행)

1. 독립 검수 후 승인된 HTTPS 테스트 서버 URL을 두 계정에 연결한다. 각각 Feel2Real **동일 사용자**로 로그인하고 동일 scope를 동의한다.
2. 실제 사용자가 업로드한 원본 하나의 `file.id`, 전체 `sha256`, `size_bytes`, 자료의 `revision.id`, `label`, `origin`, `approved_id`를 스튜디오에서 확인한다. 아직 실제 파일이 없으면 업로드 대기 결과를 두 클라이언트에서 먼저 비교한다.
3. 양쪽에서 동일하게 `read_studio`를 호출한다.

```json
{"endpointId":"GET /api/studio/files/{id}","pathParams":{"id":"<실제 file.id>"}}
```

```json
{"endpointId":"GET /api/studio/revisions/{id}","pathParams":{"id":"<실제 revision.id>"}}
```

4. 원문은 `GET /api/studio/files/{id}/content`를 `offset:0, limit:65536`부터 조회한다. `base64`는 원문 byte 청크이고 `chunkSha256`은 그 청크의 SHA-256이다. 전체 파일 SHA와 청크 SHA를 혼동하지 않는다. 텍스트/PDF·ZIP 목록은 `/text`로 열람할 수 있다.
5. 두 계정의 자료 ID·버전·출처·승인 포인터·전체 SHA·원문 일부를 스튜디오와 3자 대조한다. 실제 자료의 본문을 공개 PR에 복사하지 말고 검증 ID/해시와 성공 여부만 남긴다.
6. 학생·금융·상담 서비스는 테스트 사용자 각각의 허가 데이터만 확인한다. 다른 사용자/학원 ID, scope 미동의, 권한 폐기, token 폐기 이후 거부를 확인한다.
7. 읽기 도구에 POST/PUT/DELETE endpoint를 전달했을 때 거부되고 DB·S3·승인 포인터가 그대로인지 확인한다.

| 구분 | 현재 결과 |
|---|---|
| 공식 SDK A·B OAuth·동일 본문 | PASS (protocol-results.json) |
| 실제 ChatGPT 구독 계정 | NOT RUN |
| 실제 Claude 구독 계정 | NOT RUN |
| 사용자 요금제·조직 권한 메뉴 | 문서 조사 완료, 계정 내 확인 미실행 |
| 운영 모든 서비스의 실제 자료 대조 | NOT RUN (운영 접근·변경 금지 준수) |
| 별도 AcademyCraft backend·DB | Gateway 계약 구현, 외부 서비스 실연결 미실행 |
| Claude 독립 검수 | NOT RUN, CLAUDE_HANDOFF.md에 인계 |

실제 연결 미실행 사유는 공개 테스트 endpoint와 두 제품의 로그인 계정 세션이 제공되지 않았기 때문이다. 운영 배포·main 병합을 실행해 이를 우회하지 않았다. 따라서 사용자가 제시한 두 완료 조건을 모두 충족했다고 선언하지 않는다.
