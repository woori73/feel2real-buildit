# 신규 AWS 운영 진단 · 배포 준비 보고 (D1~D8)

기준: main `fc756729`(#863~#872 병합 완료, 재병합·재시험 없음). 실행한 운영 조회는 모두 기존 workflow의 **읽기 전용 단계**뿐이다. 배포·DB 변경·RDS 스냅샷·MCP 활성화·IAM 변경·영상 생성은 하지 않았다.

## 실행한 읽기 전용 조회
| 실행 | 내용 | 결과 |
|---|---|---|
| `Migration infra build` step=`validate` [run 39867933372](https://github.com/woori73/feel2real-buildit/actions/runs/39867933372) | VPC·서브넷·EC2·RDS·ALB·ACM Describe | 성공 |
| `Migration infra build` step=`app-diag` [run 39867936380](https://github.com/woori73/feel2real-buildit/actions/runs/39867936380) | SSM: 서비스 상태, app.jar ls, java -version, `free -m`, 포트, 오류 로그 grep | 성공 |

두 단계 모두 infra-builder 역할을 쓰지만 명령은 조회뿐이다(코드 확인). 새 진단 workflow는 배포 역할 신뢰 정책(`deploy-ec2`·`migration-infra-build`만 허용) 때문에 IAM 변경 없이는 실행할 수 없다.

## 운영 현황 (신규 계정 425805163960)
| 항목 | 결과 | 근거 |
|---|---|---|
| 운영 EC2 | `i-0414de33d2803c723`, **t3.large, running** (사용자 정보와 일치) | validate |
| ALB / 인증서 | `f2r-alb` / `feel2real.co.kr` ISSUED | validate |
| RDS | **`f2r-db`, `f2r-db-202610181240`, `f2r-db-202610181331` 3개**, 모두 `db.t4g.micro` MySQL | validate. 어느 것이 운영 DB인지 미확정 |
| available 메모리 | **5,425 MB** (전체 7,819 / 사용 2,162 / 여유 4,401 / 캐시 1,256) | app-diag `free -m` |
| 실행 중 앱 SHA | **`2bed3918` (강한 정황, 바이트 증명 아님)** — `app.jar` 221,747,839 B, 수정 06:24 UTC, 새 PID 06:25:44 시작 = Deploy run 39733429342(06:24:33 업로드, 06:24:44~06:26:08 SSM 배포, `target` 기본값 `new`) | app-diag + 배포 기록 |
| JDK | OpenJDK 21.0.12 | app-diag |
| 서비스 상태 | active, 8080·8000(wiki, 루프백) 수신, 오류는 KIS websocket WARN·비허용 POST WARN 뿐 | app-diag |
| RDS 엔진 버전 | **미확인** | 현 권한으로 조회 단계 없음 |
| Flyway 현재 버전 / V1094·V1095 | **미확인** (저장소 근거로는 V1093까지, V1094·V1095는 `2bed3918`에 없음 → **미적용 예상**) | |
| 자동 백업·복원 가능 시점 | **미확인** | |
| 배포 예정 코드와 차이 | `2bed3918..fc756729`: #859, #854(V1094), #853, #855(V1095), #863~#872, POI 5.3.0→5.5.1·poi-ooxml·CommonMark 추가 | git |

## D1~D8
| # | 조건 | 판정 | 증거 / 남은 일 |
|---|---|---|---|
| D1 | 운영 계정·EC2 확정 | **PASS** | 신규 계정 t3.large `i-0414de33d2803c723` 실행 중, jar 시각이 마지막 배포와 일치. 구 인스턴스는 SSM `InvalidInstanceId`로 진단 대상 아님 |
| D2 | 읽기 전용 현황 workflow·권한 | **부분** | PR **#875**: `prod-readonly-status.yml` 추가, `prod-diagnostic` 구 인스턴스 하드코딩 제거, 고정 SSM 문서 `F2R-ReadOnlyStatus`·최소 권한 역할 정의(actionlint 통과, 경로 필터로 CI 미실행). **역할·문서 생성(IAM/SSM 변경) 승인 전이라 RDS 버전·Flyway·백업은 FAIL(미확인)** |
| D3 | 배포 직전 RDS 스냅샷 | **FAIL(준비만)** | 초안 `rds-predeploy-snapshot.yml`(승인 환경 + `SNAPSHOT-<sha8>` + 생성·대기만)과 전용 역할 정의를 #875에 추가, 비활성. **운영 DB 식별자 확정이 선행**(RDS 3개) |
| D4 | 배포 범위·POI 5.5.1 | **PASS(POI) / 승인 대기(배포)** | POI 5.3.0·5.5.1로 **POI가 아닌 작성기**(LibreOffice, xlwt) 신한카드형 .xls 2개 파싱 결과 **완전히 동일**, 기존 신한카드 테스트 18건 두 버전 모두 통과(증거: `handoff/poi/dump-5.3.0.txt`·`dump-5.5.1.txt`, 프로브 `handoff/probes/ClaudePoiCompatProbeTest.java.txt`). 실제 신한카드 다운로드 원본으로는 시험하지 않음(개인정보, 저장소에 없음). 관찰(기존 동작, 회귀 아님): 숫자 셀로 저장된 카드 뒤 4자리는 `1234.0`으로 읽힌다 |
| D5 | 중단 최소화·복구 | **PASS(절차) / 미실행** | 배포 스크립트 `f2r_deploy_with_rollback`: `app.jar.previous` 백업 → 교체 → PID 추적 health 재시도 → 실패 시 자동 이전 jar 복구. 대상 그룹이 단일 인스턴스라 재시작 동안(지난 배포 기준 약 1분) 요청 실패 가능 → 저트래픽 시간. 수동 복구: `Deploy to EC2`를 `2bed3918`을 가리키는 ref로 `target=new` 재실행. V1094·V1095는 추가형이라 이전 jar 기동 가능(로컬 검증), 다운 마이그레이션 없음. 최악의 경우 스냅샷 복원(새 인스턴스, 별도 승인) |
| D6 | 운영 적재 도구 | **PASS(구현·시험) / 미실행** | PR **#876**: `--production` 기본 dry-run, HTTPS·호스트 고정, 배포 SHA(같은 카탈로그·API + 최근 성공 배포 SHA) 검증, 스냅샷 이름 확인, 0600 쿠키, SHA 중복 제거, `--state` 재시작, 부분 상태 중단, 등록 1회. 테스트 9건 통과, PR CI 3/3 통과(build·mysql-integration-test [run 39869925315](https://github.com/woori73/feel2real-buildit/actions/runs/39869925315), Studio/MCP isolated [run 39869925316](https://github.com/woori73/feel2real-buildit/actions/runs/39869925316)). 실제 운영 적재 없음 |
| D7 | 승인 캐릭터 5종 이관 | **사용자 결정 대기** | MANIFEST 5개 SHA와 일치할 때만 기존 승인 이관(코드 확인) |
| D8 | MCP·AI 쓰기·Hermes·Reactor | **PASS(OFF 유지)** | 활성화 없음. #873 검토: 배포 역할 재사용·임의 셸·공개 로그의 IP/ARN 노출·운영 호스트 공유 시 자원 격리를 보완 권고(댓글). 협업실 MCP와 인증·권한·비용 분리 |

## 추가 발견
- 구 계정·구 인스턴스를 고정한 workflow가 18개 더 있다(목록: #875 설명서). 정리 필요.
- RDS 3개 중 2개(`f2r-db-2026101812xx`)는 이전 리허설 잔여물일 가능성 → 비용 확인 대상(삭제는 소유자 판단).
- 배포 jar에 Git SHA가 기록되지 않는다. 배포 스크립트가 `app.jar.meta`(Git SHA·jar SHA-256)를 남기면 실행 SHA를 바이트로 증명할 수 있다(별도 변경 제안, #875의 문서는 파일이 있으면 출력).

## 승인 요청 (순서)
1. #875 병합 + SSM 문서 `F2R-ReadOnlyStatus`와 역할 `github-actions-f2r-readonly` 생성 → `Prod read-only status` 실행으로 RDS 버전·백업·Flyway·운영 DB 식별자 확인
2. 스냅샷 workflow 활성화(환경·역할) 또는 콘솔 수동 스냅샷
3. 배포 범위·시간 승인 → 배포 → 스모크
4. #876 병합 → dry-run → 적재 승인
5. D7 결정, MCP·Reactor는 각각 별도
