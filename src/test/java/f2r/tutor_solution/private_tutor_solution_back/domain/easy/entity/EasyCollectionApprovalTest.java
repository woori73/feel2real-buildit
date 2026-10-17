package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EasyCollectionApproval — P1 승인 엔티티 테스트.
 */
@DisplayName("EasyCollectionApproval — 승인 상태 전이")
class EasyCollectionApprovalTest {

  private EasyCollectionApproval pendingApproval() {
    return EasyCollectionApproval.builder()
        .run(null)
        .business(null)
        .requestedBy("tester")
        .idempotencyKey("test-key-123")
        .build();
  }

  @Test
  @DisplayName("given PENDING when approve then APPROVED로 전이된다")
  void approve_transitionsToApproved() {
    EasyCollectionApproval approval = pendingApproval();

    approval.approve("admin", "확인 완료");

    assertThat(approval.getStatus()).isEqualTo(EasyCollectionApproval.ApprovalStatus.APPROVED);
    assertThat(approval.getDecidedAt()).isNotNull();
    assertThat(approval.getDecidedBy()).isEqualTo("admin");
  }

  @Test
  @DisplayName("given PENDING when reject then REJECTED로 전이된다")
  void reject_transitionsToRejected() {
    EasyCollectionApproval approval = pendingApproval();

    approval.reject("admin", "자료 오류");

    assertThat(approval.getStatus()).isEqualTo(EasyCollectionApproval.ApprovalStatus.REJECTED);
    assertThat(approval.getDecidedAt()).isNotNull();
  }

  @Test
  @DisplayName("given 이미 처리됨 when approve then 예외가 발생한다 (중복 반영 방지)")
  void approve_alreadyDecided_throws() {
    EasyCollectionApproval approval = pendingApproval();
    approval.approve("admin", "1차 승인");

    assertThatThrownBy(() -> approval.approve("admin", "2차 승인"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("이미 처리된");
  }

  @Test
  @DisplayName("given 이미 처리됨 when reject then 예외가 발생한다")
  void reject_alreadyDecided_throws() {
    EasyCollectionApproval approval = pendingApproval();
    approval.reject("admin", "반려");

    assertThatThrownBy(() -> approval.reject("admin", "재반려"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("given 생성 when 초기 상태 then PENDING이다 (승인 전 장부 미반영)")
  void initialStatus_isPending() {
    EasyCollectionApproval approval = pendingApproval();

    assertThat(approval.getStatus()).isEqualTo(EasyCollectionApproval.ApprovalStatus.PENDING);
    assertThat(approval.getDecidedAt()).isNull();
  }

  @Test
  @DisplayName("given APPROVED when markCommitted then COMMITTED로 전이된다")
  void markCommitted_fromApproved_transitionsToCommitted() {
    EasyCollectionApproval approval = pendingApproval();
    approval.approve("admin", "승인");

    approval.markCommitted();

    assertThat(approval.getStatus()).isEqualTo(EasyCollectionApproval.ApprovalStatus.COMMITTED);
    assertThat(approval.getCommittedAt()).isNotNull();
    assertThat(approval.isCommitted()).isTrue();
  }

  @Test
  @DisplayName("given COMMITTED when markCommitted then 재반영하지 않고 그대로 둔다 (멱등성)")
  void markCommitted_alreadyCommitted_idempotent() {
    EasyCollectionApproval approval = pendingApproval();
    approval.approve("admin", "승인");
    approval.markCommitted();

    approval.markCommitted(); // 두 번째 호출 — 예외 없이 무시

    assertThat(approval.getStatus()).isEqualTo(EasyCollectionApproval.ApprovalStatus.COMMITTED);
  }

  @Test
  @DisplayName("given APPROVED when markCommitFailed then COMMIT_FAILED로 전이되고 오류가 기록된다")
  void markCommitFailed_recordsError() {
    EasyCollectionApproval approval = pendingApproval();
    approval.approve("admin", "승인");

    approval.markCommitFailed("DB 연결 실패");

    assertThat(approval.getStatus()).isEqualTo(EasyCollectionApproval.ApprovalStatus.COMMIT_FAILED);
    assertThat(approval.getCommitError()).isEqualTo("DB 연결 실패");
    assertThat(approval.isCommitted()).isFalse();
  }

  @Test
  @DisplayName("given COMMIT_FAILED when markCommitted then 재시도 성공 시 COMMITTED로 전이된다")
  void markCommitted_fromCommitFailed_retrySucceeds() {
    EasyCollectionApproval approval = pendingApproval();
    approval.approve("admin", "승인");
    approval.markCommitFailed("일시적 오류");

    approval.markCommitted();

    assertThat(approval.getStatus()).isEqualTo(EasyCollectionApproval.ApprovalStatus.COMMITTED);
    assertThat(approval.getCommitError()).isNull();
  }

  @Test
  @DisplayName("given PENDING when markCommitted then 예외가 발생한다 (승인 전 반영 금지)")
  void markCommitted_fromPending_throws() {
    EasyCollectionApproval approval = pendingApproval();

    assertThatThrownBy(approval::markCommitted)
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("given REJECTED when markCommitted then 예외가 발생한다 (반려 건 반영 차단)")
  void markCommitted_fromRejected_throws() {
    EasyCollectionApproval approval = pendingApproval();
    approval.reject("admin", "반려");

    assertThatThrownBy(approval::markCommitted)
        .isInstanceOf(IllegalStateException.class);
  }
}
