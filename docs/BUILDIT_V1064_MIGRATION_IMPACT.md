# V1064 `working_copy_staged_media` — 운영 적용 영향·롤백·복구 점검

> 대상: PR #628 (S3). 이 문서는 점검 결과이며 운영에는 아무것도 적용하지 않았다.

## 1. 변경 내용
```sql
ALTER TABLE academy_working_copy
  ADD COLUMN staged_media_json TEXT NULL COMMENT '...';
```
- 새 테이블·인덱스·FK·기본값·NOT NULL **없음**. 기존 행은 NULL(= 사진 예약 없음)이라 동작이 바뀌지 않는다.
- 번호: 다른 원격 브랜치에 V1064 이상 파일이 없음을 확인했다(충돌 없음). 운영 적용 max는 배포 전 `flyway-history` 진단으로 V1063임을 한 번 더 확인한다(WORKFLOW §4: out-of-order 방지).

## 2. 운영 적용 영향
| 항목 | 평가 |
|---|---|
| 실행 시점 | 새 jar 기동 시 Flyway가 자동 실행(JPA 검증 전). 기존 배포 스크립트의 재시작 구간과 같은 순간 중단 외 추가 중단 없음 |
| 락·소요 | `academy_working_copy`는 학원당 1행(수십~수백 행). MySQL 8.0.12+는 `ADD COLUMN`이 INSTANT(메타데이터만, 테이블 복사 없음). 그보다 낮은 버전이어도 소규모 테이블 복사로 사실상 즉시 |
| 운영 MySQL 버전 | **이 환경에서 확인하지 못함**(운영 접근은 읽기 전용 진단만 사용). CI는 8.0, 문서 기준 8.4. 배포 전 진단으로 버전만 확인하면 됨 |
| 원자성 | 단일 `ALTER`라 실패해도 "컬럼 없음" 상태로 남는다(부분 적용 없음) |
| JPA 검증 | `local`/`dev`는 `ddl-auto: validate` — 새 엔티티 필드는 컬럼이 있어야 하며 Flyway가 먼저 실행되므로 충족. 운영 프로필은 validate 설정이 없다 |
| 기능 노출 | 사진 예약은 대화 화면(`BUILDIT_ASSISTANT_ENABLED`, 운영 OFF) 안에서만 쓰인다. OFF면 컬럼은 계속 NULL |

## 3. 호환성·롤백
- **jar만 되돌리는 롤백(기존 `f2r_deploy_with_rollback`)은 안전하다.** 이전 jar는 새 컬럼을 매핑하지 않으며(추가 컬럼은 `validate`도 통과), 이전 코드는 `staged_media_json`을 읽지 않는다. 배포 스크립트는 DB를 되돌리지 않으므로 컬럼이 남아도 문제 없다.
- **새 jar 기동 중 마이그레이션 실패**: Flyway가 실패 기록을 남기고 기동을 거부한다 → 스크립트가 이전 jar로 롤백. 이전 jar는 `flyway_schema_history`의 실패 행 때문에 *새 마이그레이션이 없는 상태*에서는 정상 기동한다(실패 행은 새 jar 쪽 검증에서만 문제). 원인 수정 후 같은 번호의 파일을 고치는 것은 **적용 전에만** 허용(WORKFLOW §4), 이미 실패 기록이 남았다면 새 V_파일로 보정한다. `flyway_schema_history` 직접 수정은 금지.
- **컬럼 자체를 지우는 롤백은 권장하지 않는다.** 필요하면 forward 마이그레이션(`V1065__drop_staged_media_json.sql`)으로만 한다. 삭제 시 잃는 것: 게시 전 사진 예약 정보(공개 데이터는 영향 없음, 파일은 저장소에 남음).

## 4. 복구 절차(참고)
1. 예약 현황 확인(읽기 전용): `SELECT academy_id, staged_media_json FROM academy_working_copy WHERE staged_media_json IS NOT NULL;`
2. 기능 문제 시 **스위치 OFF**가 1차 완화(예약은 게시되지 않으면 공개에 영향 없음). 예약이 남아 있는 상태에서 게시를 막으려면 스위치 OFF로 대화 화면 진입을 막는다.
3. 예약 폐기가 필요하면 앱의 `working-copy/discard`(예약 포함 폐기)를 쓰거나, 승인된 절차로 `UPDATE academy_working_copy SET staged_media_json = NULL WHERE academy_id = ?`.
4. 고아 파일(교체·폐기로 안 쓰는 업로드)은 기존 정책대로 지연 정리 대상이며 즉시 삭제하지 않는다.

## 5. 배포 전 게이트(제안)
- `flyway-history` 진단: 적용 max = V1063 확인, 운영 MySQL 버전 확인(읽기 전용).
- CI(build·mysql-integration-test) 통과, 병합 후 첫 배포에서 Flyway 로그에 V1064 적용 1건 확인.
- 스모크: 서비스 health UP, 기능 스위치 OFF 상태에서 대화 화면 미노출 확인.
