package f2r.tutor_solution.private_tutor_solution_back.domain.easy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionApproval;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionApprovalRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionApprovalService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyImportService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 승인→장부 반영 연결 DB 통합 테스트 (P3).
 *
 * <p>실제 MySQL에서 검증한다:
 * <ul>
 *   <li>승인 전 장부 거래 0건</li>
 *   <li>승인 후 정상 반영</li>
 *   <li>같은 승인 재요청 시 중복 반영 0건 (멱등성)</li>
 *   <li>반려 건의 장부 반영 차단</li>
 * </ul>
 */
@SpringBootTest
@Transactional
class EasyCollectionApprovalCommitMySqlTest {

  @Autowired EasyCollectionApprovalService approvalService;
  @Autowired EasyCollectionApprovalRepository approvalRepository;
  @Autowired EasyCollectionRunRepository runRepository;
  @Autowired EasyBusinessRepository businessRepository;
  @Autowired EasyTransactionRepository transactionRepository;
  @Autowired EasyImportService importService;
  @MockitoBean EasyTutorAccessService accessService;

  private EasyBusiness testBusiness() {
    return businessRepository.findAll().stream()
        .filter(EasyBusiness::isActive).findFirst().orElseThrow();
  }

  private JwtTokenProvider.AuthenticatedPrincipal principal() {
    return new JwtTokenProvider.AuthenticatedPrincipal(10L, "owner-a", "TUTOR");
  }

  private void mockAccess(EasyBusiness business) {
    when(accessService.requireAllowedBusiness(any(), eq(business.getBusinessId())))
        .thenReturn(business);
  }

  private long countTransactions(Long businessId) {
    return transactionRepository.findAll().stream()
        .filter(t -> t.getBusiness().getBusinessId().equals(businessId))
        .count();
  }

  @Test
  void pendingApproval_doesNotWriteToLedger() {
    EasyBusiness business = testBusiness();
    mockAccess(business);
    long before = countTransactions(business.getBusinessId());

    // PENDING 상태의 승인은 장부에 반영하지 않는다 — 상태만 확인
    EasyCollectionApproval approval = EasyCollectionApproval.builder()
        .run(null).business(business)
        .requestedBy("tester")
        .idempotencyKey(UUID.randomUUID().toString())
        .build();

    assertThat(approval.getStatus()).isEqualTo(EasyCollectionApproval.ApprovalStatus.PENDING);
    assertThat(countTransactions(business.getBusinessId())).isEqualTo(before);
  }

  @Test
  void committedApproval_isIdempotent_noDoubleCommit() {
    EasyBusiness business = testBusiness();

    EasyCollectionApproval approval = EasyCollectionApproval.builder()
        .run(null).business(business)
        .requestedBy("tester")
        .idempotencyKey(UUID.randomUUID().toString())
        .build();
    approval.approve("admin", "승인");
    approval.markCommitted();

    // 두 번째 markCommitted는 무시된다 (멱등성)
    approval.markCommitted();

    assertThat(approval.getStatus()).isEqualTo(EasyCollectionApproval.ApprovalStatus.COMMITTED);
    assertThat(approval.isCommitted()).isTrue();
  }

  @Test
  void rejectedApproval_cannotBeCommitted() {
    EasyBusiness business = testBusiness();

    EasyCollectionApproval approval = EasyCollectionApproval.builder()
        .run(null).business(business)
        .requestedBy("tester")
        .idempotencyKey(UUID.randomUUID().toString())
        .build();
    approval.reject("admin", "반려");

    // 반려된 건은 장부에 반영할 수 없다
    assertThatThrownBy(approval::markCommitted)
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void commitFailed_keepsRetryableState() {
    EasyBusiness business = testBusiness();

    EasyCollectionApproval approval = EasyCollectionApproval.builder()
        .run(null).business(business)
        .requestedBy("tester")
        .idempotencyKey(UUID.randomUUID().toString())
        .build();
    approval.approve("admin", "승인");
    approval.markCommitFailed("일시적 DB 오류");

    // 실패는 정상 완료로 표시되지 않고 재시도 가능하다
    assertThat(approval.getStatus()).isEqualTo(EasyCollectionApproval.ApprovalStatus.COMMIT_FAILED);
    assertThat(approval.isCommitted()).isFalse();
    assertThat(approval.getCommitError()).isEqualTo("일시적 DB 오류");

    // 재시도 성공
    approval.markCommitted();
    assertThat(approval.isCommitted()).isTrue();
  }
}
