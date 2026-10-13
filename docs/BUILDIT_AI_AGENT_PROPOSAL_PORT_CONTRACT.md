# VisualEditorProposalPort 구현 계약 (뮤즈 구현용)

- 소스: `src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/service/VisualEditorProposalPort.java` (편집기 쪽 소유, 커밋 `a336f2f`)
- 호출처: `VisualEditorService.saveDraftWithProposal` (`PUT …/visual-editor/draft` + `appliedProposalId`)
- 설계: [BUILDIT_AI_AGENT_PROPOSAL_FINAL_DESIGN.md](BUILDIT_AI_AGENT_PROPOSAL_FINAL_DESIGN.md) §2~§4
- 구현체는 **`@Service`/`@Component` 빈 하나**로 등록하면 된다. 빈이 없으면 `appliedProposalId` 요청은 400 `PROPOSAL_NOT_AVAILABLE`로 거부된다(`ObjectProvider`). 인터페이스·예외 파일은 수정하지 않는다(변경은 합의 후).

## 1. 메서드

```java
ProposalApplyResult applyInSaveTransaction(
    long academyId, long userId, long proposalId, long savedRevision, String sanitizedProjectData);
```

| 입력 | 출처 | 규칙 |
|---|---|---|
| `academyId` | 서버가 로그인 사용자 쿠키로 확정한 학원 | **요청값이 아니다.** 모든 제안 조회에 이 값을 조건으로 쓴다 |
| `userId` | `principal.userId()` | `applied_by` 기록용 |
| `proposalId` | 요청의 `appliedProposalId` | 사용자 신고값이므로 **신뢰하지 않는다** |
| `savedRevision` | flush 후 draft 엔티티의 revision(DB 값) | `applied_revision`에는 이 값만 기록한다 |
| `sanitizedProjectData` | `VisualEditorSanitizer`를 통과해 **실제로 저장되는** projectData(JSON 문자열) | 검증은 이 값 기준 |

출력: `ProposalApplyResult(long proposalId, String status, long appliedRevision)`
- `status`는 반드시 `"APPLIED"`. 그 외 값이나 `null`을 반환하면 호출처가 `PROPOSAL_VERIFICATION_FAILED`(422)로 거부하고 저장을 롤백한다(fail-closed).
- 이 값은 저장 응답의 `appliedProposal`로 그대로 노출된다.

## 2. 트랜잭션

- 호출은 **저장 트랜잭션 안**에서 일어난다(초안 `update`+`flush` 직후). 구현체 메서드는 `@Transactional(propagation = Propagation.MANDATORY)`로 선언해 트랜잭션 밖 호출을 막는다.
- 새 트랜잭션(`REQUIRES_NEW`)을 열지 않는다. 같은 트랜잭션에 참여해야 "제안 상태 변경 + 초안 저장"이 함께 커밋/롤백된다.
- 모든 실패는 **런타임 예외로 던진다**(`ProposalException`). 던지면 초안 갱신까지 롤백된다. 예외를 catch해서 삼키거나 상태만 바꾸고 정상 반환하지 않는다.
- 제안 행은 `SELECT … WHERE proposal_id = :id AND academy_id = :academyId FOR UPDATE`(`PESSIMISTIC_WRITE`)로 잠근다. 같은 제안에 대한 동시 저장은 이 잠금으로 직렬화되어 한쪽은 `NOT_PENDING`(또는 revision 충돌)으로 끝난다.
- 409(revision 충돌)에서 STALE 등 **상태를 바꾸지 않는다**: 충돌은 이 메서드 호출 전에 발생하므로 이 메서드는 아예 호출되지 않는다.

## 3. 처리 순서 (구현체가 지킬 것)

1. 잠금 조회(위 쿼리). 없음/다른 학원 → `ProposalException.notFound()` (둘을 구분하지 않는다).
2. `status != PENDING` → `ProposalException.notPending()`.
3. 설계서 §4 검증 실행. 하나라도 실패하면 `ProposalException.verificationFailed(details)`. **성공을 기본값으로 두지 않는다**(비교 대상 누락·JSON 파싱 실패·`changes` 비어 있음·알 수 없는 type 모두 FAIL).
4. `status=APPLIED`, `applied_revision=savedRevision`, `applied_at=now`, `applied_by=userId` 저장 후 `ProposalApplyResult(proposalId, "APPLIED", savedRevision)` 반환.

## 4. 오류 코드

`VisualEditorProposalPort.ProposalException`의 팩토리를 그대로 쓴다.

| 팩토리 | code | HTTP | 언제 |
|---|---|---|---|
| `notFound()` | `PROPOSAL_NOT_FOUND` | 404 | 없음 또는 다른 학원 제안 |
| `notPending()` | `PROPOSAL_NOT_PENDING` | 409 | APPLIED/REJECTED/EXPIRED/STALE |
| `verificationFailed(List<String> details)` | `PROPOSAL_VERIFICATION_FAILED` | 422 | 검증 실패. `details`는 `"TEXT_MISMATCH:ve5"`처럼 `코드:componentId` 문자열 목록 |
| `notAvailable()` | `PROPOSAL_NOT_AVAILABLE` | 400 | 호출처가 사용(구현체는 던지지 않는다) |

응답 본문 형식(호출처가 변환): `{status, message, error, code, details?}`. 404와 "다른 학원"은 같은 메시지·같은 코드여야 한다(존재 여부 비노출).

`details` 코드 권장값(설계서 §4.1): `NO_CHANGES`, `PROJECT_DATA_MISSING`, `TARGET_NOT_FOUND`, `TARGET_AMBIGUOUS`, `TEXT_MISMATCH`, `IMAGE_MISMATCH`, `STYLE_MISMATCH`, `UNKNOWN_CHANGE_TYPE`.

## 5. 구현체가 하지 않을 일

- `VisualEditorDraft`·초안/게시본 테이블·`page_settings`·`renderMode`를 읽거나 쓰지 않는다(받은 `sanitizedProjectData`만 검사).
- 학원을 요청값이나 제안 행에서 추측하지 않는다(`academyId` 인자만 사용).
- 자체적으로 revision을 비교하거나 `savedRevision`을 재조회하지 않는다.

## 6. 적합성 확인 방법

- 참고 구현(테스트 전용 가짜 포트): `VisualEditorProposalMySqlTest.FakePortConfig` — 같은 순서(잠금→학원 조건→상태→검증→기록)를 JDBC로 구현했다.
- 뮤즈 구현체를 연결하면 같은 시나리오를 실제 구현체로 재실행한다: 정상 적용, revision 충돌 롤백, 검증 실패 롤백, 타 학원 404, 비-PENDING 409, 같은 제안 동시 저장 2건(정확히 1건 성공).
- 단위 검증(뮤즈): 설계서 §7.3 V-01~V-10(실제 GrapesJS 출력 fixture 기준, `selectors:["#id"]`/`selectorsAdd` 두 형식, 스텁 부재).
- 편집기 쪽 이벤트·적용 계약은 `docs/API.md` "AI 제안 적용 저장" 참고. 제안 `changes[]` 항목은 `{componentId, type:"text"|"image"|"style", after, styles?, mediaText?}`이며 편집기는 `window.__ve.applyProposal({proposalId, changes})`로 받는다.
