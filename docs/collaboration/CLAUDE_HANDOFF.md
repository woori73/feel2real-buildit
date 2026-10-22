# Claude 독립 검수 인계

이 문서는 검수 요청이며 검수 완료 판정이 아니다. 구현자는 Codex 단일 에이전트이며 Claude를 모방한 하위 에이전트나 유료 모델 API를 실행하지 않았다. PR #862의 최신 HEAD와 CI를 기준으로 독립 판정한다.

## 우선 확인할 경계

1. 기본 설정에서 MCP append OFF, OAuth checkbox 기본 해제. 기존 읽기 전용 grant에 쓰기 scope가 생기지 않아야 한다. 기존 서비스용 도구/권한/readonly delegation 회귀를 확인한다.
2. 별도 승인된 본인 grant만 AI 역할/프로젝트에 묶인다. 도구 인자로 AI/프로젝트를 바꾸거나 다른 grant의 업로드를 이어받지 못해야 한다. 다른 프로젝트, 미선택 AI, VIEWER 쓰기, 폐기/만료, studio 회원 회수는 차단되어야 한다.
3. 모든 AI 문서/파일은 DRAFT, 원본 SHA/등록자/시각/부모 이력 보존. 동일 SHA는 blob만 재사용. 승인된 후보/제작실 정본 포인터/복원 이력은 변경하지 않아야 한다. 첨부 뒤 DB 예외에서 새 blob만 정리하고 dedup 원본은 보존해야 한다.
4. 원문/이미지 제공에 originals:read를 요구한다. 내부 storage_key·token·원격 오류가 노출되지 않아야 한다. HTML은 원본 다운로드/escaped text 또는 sandbox+CSP+스크립트/외부 URL을 제거한 임시 뷰어이며 원문 byte가 바뀌거나 실행되지 않아야 한다.
5. 선택된 역할에만 인계, Claude 수정 요청→선택된 Codex→Claude. 미선택 Codex/Muse 자동 투입 금지. 중단/상한/동시 요청/retry/version 경합과 인간 승인 분리를 확인한다. 서버가 소비자 구독 앱/유료 모델을 실행하는 코드가 없어야 한다.
6. DB에 원래 #855 자료가 있는 상태에서 V1096/V1097/V1098의 additive upgrade와 Hibernate validate를 수행한다. 운영 DB가 아닌 복제/격리 DB만 사용한다. 기존 migration 파일은 변경하지 않는다.
7. PC1440/모바일390에서 실제 파일 열람/검수/기억/검색/연결 지정/오류 안내, 키보드·screen reader·contrast를 확인한다. 자동 axe0이 수동 접근성 검수를 대체하지 않는다.
8. MCP 기록/원문을 최신10개 작업 이력·20개 기억 단위 커서로 확인한다. 자료는 cursor로536개 전체를 조회해야 한다. 미완료 업로드TTL/총저장상한 미구현은 명시된 한계이며 운영 공개 전 보존 정책을 판정한다.

## 재현

`CollaborationMySqlTest`는 실행 플래그가 없으면 건너뛴다. 격리 MySQL8.4 `127.0.0.1:3319/f2r_cluster_test`와 CI의 합성 계정을 사용한다. 기존 운영 DB/업로드 저장소를 설정하지 않는다. 기존3318 테스트 DB와 충돌하면 F2R_STUDIO_TEST_PORT로 새 loopback 포트를 지정할 수 있으며 기존 DB를 repair/reset하지 않는다. `.github/workflows/collaboration-check.yml`에 재현 가능한 서비스/의존성/브라우저/공식 MCP SDK 설치가 있다.

```sh
F2R_COLLAB_MYSQL_TEST=true F2R_COLLAB_BROWSER_TEST=true F2R_COLLAB_SDK_TEST=true \
  NODE_PATH="$PWD/e2e/collaboration/node_modules" \
  bash gradlew test --no-daemon --tests '*CollaborationMySqlTest'
```

`studio-mcp-check.yml`의 기존 studio/MCP/JWT/방문로그/Wiki 테스트도 통과해야 한다. PR 전체 Build Check/MySQL CI PASS와 별개로 독립 MySQL/브라우저 판정을 남긴다. OAuth test fixture는 합성 계정의0600 임시 파일이며 시험 종료 시 제거한다. 토큰을 PR에 붙이지 않는다.

## 실제 #863 자료 검수(이번 후속 우선)

[실자료 적재 보고서](PRODUCTION_IMPORT.md)와 `production-results/`의 dry-run/536개 SHA 표/재실행/페이지/SQL 실패·복구/실제 자료 브라우저 캡처를 확인한다. source `09d62dee`에서536개가 등록되고 승인 참조5종·러프68장·r5 JSON68행·원본경로/버전/인계4개가 보존되어야 한다. 동일SHA220개도 경로/버전별 후보가 빠지지 않아야 한다. MD5 12개는 메타자료이며 이미지가 아니어야 한다. 신규536개 DRAFT와 정식 원작 대조0/68·PASS0을 확인한다.

HTML/MD/PDF·확대·S5/S6 후보/승인 참조 비교를 실제 자료로 PC1440/모바일390에서 재검증한다. 원본은 고치지 않는다. 인계3문서의 공동 기억·결정·미해결/다음 작업도 확인한다. local importer는 명시적 flag/loopback 테스트 DB/LocalFileStorage 밖에서 동작하지 않아야 한다. 이 검수 뒤 #862/#863의 병합 순서는 사용자와 결정하며 Codex가 병합하지 않는다.

## 실제 제품 연결은 별도

공식 SDK 두 연결 테스트는 실제 OAuth/HTTP/파일 전송의 증거다. 실제 ChatGPT/Claude 계정 연결·파일 첨부·모델 실행의 증거가 아니다. 공개 HTTPS 격리 주소와 본인 계정으로 [CONNECTORS](CONNECTORS.md) 비교표를 채운다. 실제 생성 바이너리가 전달되지 않으면 미지원/미검증으로 기록한다. 과금을 허용하지 않았으므로 유료 API로 대체하지 않는다.

## 다른 PR 및 운영 승인

#860 보안 보완 `9aa8c340`, #861 리더/worker 원자 선점 `7308d609`도 별도 독립 검수 요청 상태다. #860 검수/사용자 병합 승인/병합 전 운영 a1-precheck를 실행하지 않는다. KIS 실제 네트워크 partition/GC pause fencing은 미검증이며 websocket 기본 OFF,8083 리허설 금지를 유지한다. #853 P1/#855 P2 선행 수정 근거는 [긴급 수정 보고서](../ai-agent-studio/URGENT_FIXES_20261022.md)를 사용한다.

main 병합과 운영 배포는 이 PR에서 수행하지 않았다. 저장소 소유 계정이 별도 병합한 #853/#854/#855와 이 세션의 동작을 구분한다. 사용자 최종 승인 전 운영 DB migration/배포를 실행하지 않는다.
