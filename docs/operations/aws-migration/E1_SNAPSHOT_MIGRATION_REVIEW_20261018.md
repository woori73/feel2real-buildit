# E1 스냅샷 계정 이전 — 독립 검증·보완 (2026-10-18, Claude)

> E1 = **스냅샷 복사·공유 준비 단계**(구 계정 CMK 재암호화 복사 → 신규 계정 공유 → 신규 계정 내 복사). **DB 복원·운영 전환·DNS는 E1이 아니며 별도 승인 단계(E-2 이후)다.** 이 문서와 같은 폴더의 초안 파일은 **설계·자동화 준비뿐이며 실행하지 않았다.**
> 한계: AWS 공식 문서 사이트(docs.aws.amazon.com)는 이 세션에서 열리지 않아 **공식 규격을 직접 대조하지 못했다.** 아래 AWS 동작은 알려진 문서화된 동작에 대한 기억 기반이며, 각 항목의 "확인 방법"은 첫 실행(dry 단계)에서 결과로 검증한다. 비용·시간은 모두 **추정치**다. RDS 14일 지표 추가 수집은 중단했고, `db.t4g.micro`는 우선 후보로 유지한다.

## 1. 검증 결과 (E1 설계서 §8 체크리스트)
| 항목 | 결과 |
|---|---|
| AWS 관리형 키 공유 불가 전제와 재암호화 이전 절차 | **정합.** 관리형 키(`aws/rds`)로 암호화된 스냅샷은 계정 간 공유 불가 → 구 계정 CMK로 재암호화 복사 후 공유 → 신규 계정 내 복사 순서가 맞다. 수동 스냅샷만 공유 가능(자동 스냅샷 불가) — 워크플로가 `manual`만 허용 |
| **CLI 플래그 오류** | 설계서의 `--source-dbsnapshot-identifier`/`--target-dbsnapshot-identifier`는 **존재하지 않는 플래그**(AWS CLI 1.46 파싱으로 확인: 필수 인자 `--source-db-snapshot-identifier`·`--target-db-snapshot-identifier`). 초안에서는 올바른 이름으로 수정 |
| 키 정책 최소 권한 | 대상이 신규 계정 루트(`arn:aws:iam::425805163960:root`)인 점은 맞다(없는 역할 ARN은 정책 저장 시 검증 실패 가능). **보완 2건:** ① `put-key-policy`는 정책 **전체 교체**이므로 기본 문장(구 계정 루트 `kms:*`)을 반드시 유지(없으면 키 잠금) — 초안 워크플로가 루트 문장 존재를 단언하고 추가만 한다. ② 와일드카드 방지만으로는 부족 → 조건 추가: `kms:ViaService=rds.ap-northeast-2.amazonaws.com`, `kms:CallerAccount=425805163960`, `kms:CreateGrant`는 별도 문장에 `kms:GrantIsForAWSResource=true`(`e1-old-kms-key-policy-statements.json`) |
| 구 계정 실행 주체 권한(설계서 §5) | **누락 있음:** 복사하는 주체에 CMK 사용 권한(`kms:CreateGrant`, `Decrypt`, `GenerateDataKey`, `ReEncrypt*`, `DescribeKey`)과 `rds:DescribeDBSnapshotAttributes`, `kms:GetKeyPolicy`(읽고 추가하려면 필요), `kms:TagResource`가 빠졌다 → 없으면 복사가 AccessDenied. 설계서의 "`f2r-mig-*` 한정"은 `CopyDBSnapshot`에서 **소스·대상 ARN을 둘 다 허용해야 해서 이름 제한이 불가능**(대상 이름 제한은 공유·태그 권한에만 적용) |
| 신규 계정 권한 | 설계서는 `CopyDBSnapshot`·`Restore...`만 적음. **복사에는 구 계정 CMK에 대한 `DescribeKey`·`Decrypt`·`CreateGrant`(IAM 쪽)도 필요**(키 정책과 IAM 정책 둘 다 허용해야 교차 계정 사용 가능). 복원 권한은 E1에서 제외 |
| 신규 계정 복사 키 | 설계서 "생략 시 AWS 관리형 키"는 **명시 지정을 권고**(`alias/aws/rds`로 명시). 공유받은 암호화 스냅샷 복사 시 KMS 키를 명시해야 하는지는 공식 문서로 확인하지 못함 → 명시하면 어느 경우든 안전 |
| 비용 | 설계서 "수 달러 이내"는 **상한으로 타당**. 서울 공개 가격 기준 CMK $1/키·월(삭제 예약 전까지 월할), 요청 $0.03/1만 건(월 무료 구간 있음, 사용량 무시 가능), 스냅샷 보관 $0.095/GB·월(할당 20GB를 전부 청구한다고 가정해도 월 약 $1.9, 일 약 $0.06; 실사용 약 0.9GB면 더 적을 수 있음 — 추정). 신규 계정 복사본은 신규 계정에 같은 단가. 동일 리전 계정 간 복사 전송료 $0는 기억 기반 |
| 롤백 | **보완:** ① `CreateGrant`로 생긴 **그랜트가 키에 남는다**(복사 완료 후에도) — 키 삭제 전 확인. ② 신규 계정 복사본(`f2r-shared-*`) 삭제 항목 누락. ③ **순서 제약:** 신규 계정 복사본이 `available`이고 복원 검증이 끝나기 전에는 구 계정 복사본·CMK를 비활성화·삭제하지 않는다(신규 복사본은 신규 키로 재암호화돼 독립적이지만 검증 전 철회하면 재시도가 막힘). ④ CMK 삭제는 최소 대기 7일(그 동안 키는 비활성) ⑤ 공유 취소는 이미 만든 신규 계정 복사본에는 영향 없음 |

## 2. 스냅샷 최신성과 최종 데이터 동기화 방안 (보완)
문제: 설계서는 "최신 수동 스냅샷"을 복사한다. 수동 스냅샷은 생성 시점에 고정이라 **컷오버 시점 데이터와 다르다**(생성 이후 쓰기 유실).
- **E1 복사본은 용도를 둘로 나눈다.** ① **사전 검증용**: 기존 최신 수동 스냅샷(또는 새로 만든 스냅샷)으로 KMS·권한·복사 시간·신규 계정 복원 시험·Flyway 시험(V1086~V1089)을 한다. **컷오버에 쓰지 않는다.** ② **컷오버용**: 쓰기 동결 직후 **새 수동 스냅샷**(`f2r-mig-<날짜>`)을 만들어 같은 경로(CMK 재암호화 복사 → 공유 → 신규 복사 → 복원)로 옮긴다.
- 컷오버용 스냅샷은 `rds:CreateDBSnapshot`이 필요하다 → **별도 정책**(`policy-e1-old-account-final-snapshot.json`)으로 분리해, E1 실행 시에는 부여하지 않고 **최종 컷오버 승인 때만** 붙인다. 구 DB에는 쓰기가 아닌 "스냅샷 생성"만 발생.
- **쓰기 동결:** 컷오버 직전 구 앱을 점검 모드/중지(구 환경 변경이므로 E-3 승인에 포함). 앱이 멈춘 뒤 스냅샷을 만들어야 정합하다.
- **데이터 규모(사용자 제공 실측): 약 0.9GB.** 재암호화 복사·계정 간 복사·복원 모두 작아 **컷오버 구간 소요 추정: 동결 5분 + 스냅샷 생성 5~15분 + 재암호화 복사 5~20분 + 공유 즉시 + 신규 복사 5~20분 + 복원 10~25분 + Flyway·스모크 15~30분 ≈ 45~115분(추정).** 이 구간이 서비스 중단 시간이다.
- **대안(논리 덤프·복제)은 채택하지 않음:** 바이너리 로그 복제는 구 DB 설정 변경(보존 시간 등)과 DB 자격 증명 취급이 필요하고, 덤프는 구 VPC 내 실행 주체·자격 증명이 필요하다. 데이터가 작아 스냅샷 경로가 더 단순하고 안전하다.
- **S3:** 객체 키가 DB에 저장되므로 DB와 같은 시점 기준으로 최종 delta 동기화(E-3 단계, 이번 E1 범위 아님).
- **복원 후 검증:** 행 수·체크섬 샘플·`flyway_schema_history`·앱 부팅 확인. 스냅샷 시각 이후 쓰기 여부는 구 앱 중지 시각과 스냅샷 `SnapshotCreateTime` 비교로 확인.

## 3. 자동화 준비물 (모두 비활성 초안, 이 폴더)
| 파일 | 내용 |
|---|---|
| `e1-snapshot-migration.yml` | `workflow_dispatch` 단일 트리거, 단계 선택(`old-kms`/`old-copy`/`old-share`/`new-copy`/`status`), Environment `f2r-aws-e1`(승인), 단계별 멱등(이미 있으면 재사용), 복사 완료 대기, 수동 스냅샷만 허용, **복원·삭제 단계 없음**, 로그에 키 ID·ARN·계정 ID 미출력(계정 ID는 `<ACCT>`로 가림) |
| `trust-e1-old-account.json` / `trust-e1-new-account.json` | OIDC 신뢰(불변 ID `sub`=environment `f2r-aws-e1`, `job_workflow_ref`=main의 `e1-snapshot-migration.yml`) |
| `policy-e1-old-account-snapshot-migrator.json` | 구 계정 실행 역할 최소 권한(키 생성은 `Project` 태그 필수, 키 관리는 태그된 키 한정, 파괴·복원·DB 변경 명시 Deny) |
| `policy-e1-old-account-final-snapshot.json` | **컷오버 때만** 추가하는 스냅샷 생성 권한 |
| `policy-e1-new-account-snapshot-receiver.json` | 신규 계정: 공유 스냅샷 복사 + 구 CMK/신규 관리형 키 사용(RDS 경유 한정), 삭제·복원 Deny |
| `e1-old-kms-key-policy-statements.json` | 키 정책에 **추가할** 두 문장(신규 계정 한정, 조건 포함) |
- 검증: JSON·YAML 파싱, 각 `run` 블록 `bash -n`, 정책 크기(구 역할 약 2.3KB, 신규 약 1.2KB). **IAM 평가·실제 가정·실제 복사는 하지 못했다.**
- **실행 주체:** 이 세션에는 AWS 자격 증명이 없다. 사용자에게 CloudShell 명령 실행이나 파일 다운로드를 요구하지 않는 방식은 **OIDC 역할 + Environment 승인 워크플로**뿐이며, 그래서 구 계정에 E1 전용 역할이 필요하다(신규 승인 항목). 신규 계정 역할은 승인 A와 함께 만들 수 있다.
- 알려진 한계: 구 CMK 키 ID를 정책에 고정하지 못해 신규 계정 정책의 구 계정 키 리소스는 `key/*`(RDS 경유 조건 부착). 구 CMK는 이 목적으로만 공유되므로 영향 범위는 제한적이다. 키 ID를 확정한 뒤 좁히는 것을 권고한다.

## 4. 승인 필요 항목 (현재 E1 실행은 미승인)
| 코드 | 내용 | 변경 대상 | 되돌리기 |
|---|---|---|---|
| E1-0 | 구 계정에 E1 전용 OIDC 역할 `f2r-e1-snapshot-migrator` 생성(신뢰·권한은 위 JSON), GitHub Environment `f2r-aws-e1` 생성(reviewer·main 한정) | 구 계정 IAM, GitHub 설정 | 역할·Environment 삭제 |
| E1-1 | 신규 계정 `f2r-e1-snapshot-receiver` 역할 생성(승인 A 범위에 포함 가능) | 신규 계정 IAM | 역할 삭제 |
| E1-2 | 이 워크플로를 `.github/workflows/`로 승격하는 병합(B와 별개) | GitHub main | PR revert |
| E1-3 | 실행: `old-kms` → `old-copy` → `old-share` → `new-copy`(단계마다 Environment 승인) | 구 계정 KMS 키 1개·스냅샷 복사본 1개, 신규 계정 스냅샷 1개 | 공유 취소, 복사본·키 삭제(사용자 승인 하에) |
| 별도 | 신규 계정 DB 복원(검증용 → E-2), 컷오버용 새 스냅샷 생성, 쓰기 동결, DNS 전환 | — | E-2 이후 별도 승인 |
