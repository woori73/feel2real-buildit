# 최종 전환 준비 보고 (2026-10-18)

> 상태: **전환 미실행.** 운영 DB 쓰기 중지·최종 스냅샷 복원·DNS 전환·구 AWS 종료는 별도 최종 승인 후에만 한다.

## 0. 범위 변경 (대표 결정 2026-10-18)

DesignCraft와 기존 기출분석 서비스는 폐기한다. 이전·추가 검증 대상에서 제외한다. 신규 환경에서는 DesignCraft 전용 Worker(`PUBLISHING_WORKER_ENABLED=false`)만 비활성화했다(`p2-disable-designcraft`). 엔진·템플릿 파일, DB, 백업, 공개 URL은 유지하며 영구 삭제·URL 차단은 운영 영향 확인 후 별도 승인으로 진행한다. 기출분석("내신영어 기출분석 + 출제방향 제작", `/exam-analysis`, `examinsight` 모듈)은 신규 환경에서 `APP_EXAM_INSIGHT_ENABLED=false`로 비활성화한다. 홈·대시보드 카드/메뉴 제거와 전용 코드 정리는 후속 작업이다(랜딩 계약 테스트 수정 동반). 대시보드·관리자 허브의 "내신 예상문제 제작 1회 2만원 의뢰하기"가 같은 `/exam-analysis`로 연결되어 있어, 유료 서비스 진입 경로를 새로 정해야 한다. 아래 DesignCraft 관련 항목은 폐기 결정 이전의 기록이다.

## 1. 신규 환경 현황 (계정 425805163960, ap-northeast-2)

| 항목 | 상태 | 근거 |
|---|---|---|
| VPC·서브넷·SG | 생성됨 | all-infra 성공 |
| EC2 t3.medium(EBS 30GB 암호화) | 실행 중, SSM Online | 프로비저닝 성공(Java21·Python3.11·ffmpeg·한글 폰트) |
| RDS db.t4g.micro(gp3, 백업 7일) | 복원됨(리허설용 스냅샷) | 앱 접속, Flyway 184개 검증·스키마 최신 |
| ALB + ACM(와일드카드) + HTTPS 443 | 정상 | ALB 주소로 `/actuator/health`·`/` HTTP 200 |
| 앱(BuildIt) | 배포 성공 | `deploy-ec2.yml target=new` 헬스체크 통과 |
| 비밀값(SSM SecureString) | 이관 완료 | secrets-transfer 성공(원문 비출력) |
| S3 업로드 버킷 `f2r-uploads-425805163960` | 이전·검증 완료 | 구 1,603개/920,702,618B = 신규 1,603개/920,702,618B, 키+크기 불일치 0, sha256 표본 25건 불일치 0 |
| DesignCraft 0.8.0 | 설치·실행 확인 | `--version` 정상, ldd 누락 0, 폰트 확인. PNG/PDF 출력은 `p2-dc-verify` 결과 참조 |

### S3_LIST_FAILED 원인
세 가지가 겹쳐 있었다: (1) 신규 앱 `S3_BUCKET`이 구 계정 버킷명을 그대로 복사, (2) 신규 인스턴스 역할에 그 버킷 권한 없음, (3) 데이터가 신규 계정에 없음. 신규 버킷 생성·복사·`S3_BUCKET` 교체 후 재시작해 재발 0건(권한 문제는 정책 추가가 아니라 "구 계정 버킷을 더 이상 가리키지 않게" 해결).

## 2. 기능 검증 결과

신규 ALB(Host: 서비스 도메인)에 인증 없는 GET만 사용. 운영 데이터 쓰기 없음.

| 항목 | 결과 | 비고 |
|---|---|---|
| 앱 기동·헬스 | 통과 | |
| `/`·`/login`·`/ledger`·`/easy`·`/testprep` | 200 | |
| 보호 경로(`/easy/ledger`, `/api/me`, `/api/builder/pages`) | 302/401(로그인 요구) | 인증 게이트 정상 |
| Naver 인증 시작(`/oauth2/authorization/naver`) | 302 → nid.naver.com, `client_id`·`state` 존재 | redirect_uri는 운영 도메인 |
| Naver 로그인 완료(콜백) | **미검증** | 콜백 도메인이 운영 도메인이라 DNS 전환 후에만 가능 |
| 홈페이지 Builder·EASY 장부·TESTPREP 로그인 후 동작 | **미검증** | 로그인 계정 필요. CI에는 해당 통합·브라우저 테스트가 이미 있음(EasyLedgerBrowserQa 등) |
| ADMIN/TUTOR 권한·테넌트 격리 | **미검증(신규 환경)** | CI 테스트로 로직 검증됨. 신규 환경은 전환 후 스모크 |
| PDF/HWPX 워커 | **미검증** | 로그인 후 작업 생성 필요 |
| 파일 업로드·다운로드 | **부분** | 신규 버킷 접근 확인(재시작 후 S3 오류 0). 실제 업로드/다운로드는 로그인 후 확인 |
| DesignCraft 독립 실행 | 통과 | PNG/PDF는 p2-dc-verify |

미검증 항목은 운영 계정·데이터를 건드리지 않고는 확인할 수 없는 것들이다. 전환 직후 **스모크 체크리스트**(아래 5)로 확인하고, 실패하면 롤백한다.

## 3. 데이터 이전 결과

- RDS: 리허설 복원 완료(스냅샷 f2r-mig-20261018 기반). 최종 전환 시에는 **쓰기 중지 후 새 스냅샷**으로 재복원해야 한다(리허설 복원본은 최신 데이터가 아님).
- S3: 위 표. 전환 시 구 버킷 변경분을 증분 동기화(`aws s3 sync`)하고 같은 검증을 반복한다.
- 비밀값: SSM `/f2r/app/*`. 최종 전환 직전 구 `/etc/f2r/env`에 변경이 있었는지 키 단위 비교(값 비출력)로 확인한다.
- 구 환경 데이터 삭제 없음. 임시 산출물: 구 deploy 버킷의 `migration-tmp/designcraft-*.tgz`(정리 대상, 삭제는 별도 확인 후), 구 계정 KMS `alias/f2r-migration`, 스냅샷 `f2r-mig-20261018`.

## 4. 예상 중단 시간 (추정)

| 단계 | 시간 |
|---|---|
| 구 서비스 쓰기 중지(점검 모드) → 최종 스냅샷 생성 | 5~10분 |
| 구 계정 KMS 재암호화 복사 + 공유 | 3~5분 |
| 신규 계정 복사 + 복원(db.t4g.micro) + gp3·백업 설정 | 10~20분 |
| 엔드포인트 교체·앱 재시작·헬스 | 3~5분 |
| S3 증분 동기화·검증 | 1~5분 |
| DNS 전환(레코드 TTL까지 전파, TTL 사전 인하 시 60초) | 1~5분 |
| **합계(쓰기 중지 구간)** | **약 25~45분** + DNS 전파 |

읽기 전용 안내(점검 화면)로 중단 영향을 줄일 수 있는지는 앱의 기존 admission gate/drain을 그대로 사용한다. 이 문서는 해당 안전장치를 변경하지 않는다.

## 5. 전환 절차(승인 후)

1. 전환 24시간 전: 서비스 도메인 DNS 레코드 TTL을 60초로 낮춘다(구 Route 53, 변경 전 값 기록).
2. 점검 모드 진입(admission gate 닫기, drain), 쓰기 중지.
3. 구 RDS 최종 스냅샷 → KMS 재암호화 복사 → 신규 공유·복사 → `f2r-db` 재복원(기존 `f2r-db`는 이름을 바꿔 보존).
4. SSM 파라미터의 RDS 엔드포인트 교체 → 신규 호스트 env 반영 → 앱 재시작·헬스.
5. S3 증분 동기화·검증.
6. 스모크: Naver 로그인(전환 후), Builder 저장·게시, EASY 장부 조회, TESTPREP 진입, PDF/HWPX 작업 1건, 파일 업로드·다운로드, ADMIN/TUTOR 권한, 로그·알람.
7. DNS 전환(A/ALIAS → 신규 ALB). 정상 확인 후 점검 모드 해제.

## 6. 롤백 절차

- DNS 전환 **전**: 신규 환경 폐기 없이 구 서비스 쓰기 재개(점검 모드 해제)만 하면 된다. 구 DB·S3는 변경되지 않았다.
- DNS 전환 **후** 문제 발생: DNS를 구 서버로 되돌린다(TTL 60초). 신규에서 받은 쓰기가 있으면 구 DB에 반영되지 않으므로, 전환 후 검증 시간 동안은 "신규 쓰기 허용 시점"을 명시하고 그 이전에 롤백 판단을 끝낸다. 구 RDS·EC2·S3는 검증 통과 전까지 유지한다.
- 구 AWS 종료는 신규 정상 확인 후 별도 승인 시에만.

## 7. 정리 항목(이전 완료 후)

- 구 계정 `f2r-migration-admin-temp` 역할 삭제(`AdministratorAccess`), 신규 계정 `f2r-infra-builder`의 `AdministratorAccess` 분리.
- 구 deploy 버킷의 `migration-tmp/` 임시 객체, KMS `alias/f2r-migration`(삭제 예약), 복사 스냅샷은 구 환경 종료 승인 시 함께 처리.
- GitHub Environment 승인 설정 복구(Required reviewers).
