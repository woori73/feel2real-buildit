package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionApproval;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionApprovalRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 수집 자료 장부 반영 승인 서비스 — P1/P3.
 *
 * <p>보안 규칙:
 * <ul>
 *   <li>승인 전에는 장부에 반영되지 않는다</li>
 *   <li>승인 재시도 시 중복 반영 방지 (멱등성)</li>
 *   <li>승인·반려·반영 이력은 감사 로그에 기록된다</li>
 *   <li>테넌트 격리: 요청자의 사업체만 처리 가능</li>
 * </ul>
 *
 * <p>P3 트랜잭션 설계:
 * <ul>
 *   <li>1단계 (트랜잭션): PENDING → APPROVED 상태 확정</li>
 *   <li>2단계 (트랜잭션): 장부 commit 시도 → COMMITTED 또는 COMMIT_FAILED</li>
 * </ul>
 * 두 단계를 분리한 이유: commit 실패 시 APPROVED 상태와 오류 기록을 유지해야
 * 재시도할 수 있기 때문이다. 단일 트랜잭션으로 묶으면 실패 시 오류 기록마저
 * 롤백되어 재시도 근거가 사라진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyCollectionApprovalService {

  private static final Logger log = LoggerFactory.getLogger(EasyCollectionApprovalService.class);

  private final EasyCollectionApprovalRepository approvalRepository;
  private final EasyCollectionRunRepository runRepository;
  private final EasyTutorAccessService tutorAccessService;
  private final EasyImportService importService;

  /**
   * 같은 클래스 안의 {@code this} 호출은 프록시를 거치지 않아 메서드별 {@code @Transactional}이 적용되지 않는다.
   * 클래스 기본값이 readOnly라, 그대로 두면 장부 INSERT와 비관적 잠금(SELECT ... FOR UPDATE)이
   * "READ ONLY transaction" 오류로 실패한다. 그래서 반영 경로는 프로그램 방식 트랜잭션으로 경계를 명시한다.
   */
  @Autowired
  private PlatformTransactionManager transactionManager;

  /**
   * 승인 요청을 생성한다. 이미 요청이 있으면 기존 요청을 반환한다 (멱등성).
   */
  @Transactional
  public EasyCollectionApproval requestApproval(Long businessId, Long runId, String requestedBy,
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    // 테넌트 격리: 요청자의 사업체인지 확인
    EasyBusiness business = tutorAccessService.requireAllowedBusiness(principal, businessId);

    EasyCollectionRun run = runRepository.findById(runId)
        .orElseThrow(() -> new IllegalArgumentException("수집 실행을 찾을 수 없습니다: " + runId));

    // run의 사업체와 요청 사업체가 일치하는지 확인
    if (!run.getBusiness().getBusinessId().equals(business.getBusinessId())) {
      throw new SecurityException("다른 사업체의 수집 자료입니다.");
    }

    // 이미 승인 요청이 있으면 반환 (멱등성)
    return approvalRepository.findByRun_RunId(runId)
        .orElseGet(() -> {
          String idempotencyKey = UUID.randomUUID().toString();
          EasyCollectionApproval approval = EasyCollectionApproval.builder()
              .run(run)
              .business(business)
              .requestedBy(requestedBy)
              .idempotencyKey(idempotencyKey)
              .build();
          EasyCollectionApproval saved = approvalRepository.save(approval);
          log.info("collection approval requested businessId={} runId={} approvalId={}",
              businessId, runId, saved.getApprovalId());
          return saved;
        });
  }

  /**
   * 승인한다. 이미 처리된 요청은 예외를 발생시킨다.
   * 실제 장부 반영은 별도 단계에서 수행되며, 여기서는 상태만 변경한다.
   */
  @Transactional
  public EasyCollectionApproval approve(Long businessId, Long approvalId, String decidedBy,
      String note, JwtTokenProvider.AuthenticatedPrincipal principal) {
    EasyBusiness business = tutorAccessService.requireAllowedBusiness(principal, businessId);

    EasyCollectionApproval approval = approvalRepository.findById(approvalId)
        .orElseThrow(() -> new IllegalArgumentException("승인 요청을 찾을 수 없습니다: " + approvalId));

    if (!approval.getBusiness().getBusinessId().equals(business.getBusinessId())) {
      throw new SecurityException("다른 사업체의 승인 요청입니다.");
    }

    importService.requireCollectionReview(approval.getRun().getImportBatchId(), note);
    approval.approve(decidedBy, note);
    log.info("collection approval approved businessId={} approvalId={} decidedBy={}",
        businessId, approvalId, maskUser(decidedBy));
    return approval;
  }

  /**
   * 반려한다. 이미 처리된 요청은 예외를 발생시킨다.
   */
  @Transactional
  public EasyCollectionApproval reject(Long businessId, Long approvalId, String decidedBy,
      String note, JwtTokenProvider.AuthenticatedPrincipal principal) {
    EasyBusiness business = tutorAccessService.requireAllowedBusiness(principal, businessId);

    EasyCollectionApproval approval = approvalRepository.findById(approvalId)
        .orElseThrow(() -> new IllegalArgumentException("승인 요청을 찾을 수 없습니다: " + approvalId));

    if (!approval.getBusiness().getBusinessId().equals(business.getBusinessId())) {
      throw new SecurityException("다른 사업체의 승인 요청입니다.");
    }

    approval.reject(decidedBy, note);
    log.info("collection approval rejected businessId={} approvalId={} decidedBy={}",
        businessId, approvalId, maskUser(decidedBy));
    return approval;
  }

  /**
   * 대기 중인 승인 목록을 조회한다.
   */
  public List<EasyCollectionApproval> listPending(Long businessId, JwtTokenProvider.AuthenticatedPrincipal principal) {
    EasyBusiness business = tutorAccessService.requireAllowedBusiness(principal, businessId);
    return approvalRepository.findByBusiness_BusinessIdAndStatusOrderByRequestedAtDesc(
        business.getBusinessId(), EasyCollectionApproval.ApprovalStatus.PENDING);
  }

  /**
   * 승인 + 장부 반영을 한 번에 수행한다 (P3).
   *
   * <p>1단계: PENDING → APPROVED (행 잠금 아래 별도 트랜잭션으로 확정, 동시 승인은 한쪽만 통과)
   * 2단계: staged batch → 장부 commit + COMMITTED 기록 (한 트랜잭션, 원자적)
   *
   * <p>이미 COMMITTED된 건은 재반영하지 않고 그대로 반환한다 (멱등성).
   * 승인만 되고 반영이 실패하면 COMMIT_FAILED로 남으며 정상 완료로 표시하지 않는다.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public EasyCollectionApproval approveAndCommit(Long businessId, Long approvalId, String decidedBy,
      String note, JwtTokenProvider.AuthenticatedPrincipal principal) {
    writeTx().executeWithoutResult(status ->
        approveLocked(businessId, approvalId, decidedBy, note, principal));
    return commitApproved(businessId, approvalId, principal);
  }

  /**
   * COMMIT_FAILED 상태의 건을 재시도한다.
   * COMMITTED된 건은 그대로 반환한다 (중복 반영 방지).
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public EasyCollectionApproval retryCommit(Long businessId, Long approvalId,
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    return commitApproved(businessId, approvalId, principal);
  }

  /**
   * 승인된 건의 장부 반영을 수행한다.
   *
   * <p>트랜잭션 설계:
   * <ol>
   *   <li>검증(읽기 전용): 테넌트·상태·batchId 확인</li>
   *   <li>반영(쓰기 한 트랜잭션): 승인 행 비관적 잠금 → 상태 재확인 → {@code importService.confirm()} →
   *       COMMITTED 기록. 하나라도 실패하면 장부 INSERT까지 함께 롤백되므로
   *       "장부는 반영됐는데 상태는 미반영" 상태가 만들어지지 않는다.</li>
   *   <li>실패 시(별도 쓰기 트랜잭션): COMMIT_FAILED + 오류 기록 — 재시도 근거를 남긴다.</li>
   * </ol>
   *
   * <p>중복 방지 장치:
   * <ol>
   *   <li>승인 행 잠금: 동시 반영 요청은 직렬화되고 뒤 요청은 COMMITTED를 보고 그대로 반환</li>
   *   <li>행 수준: {@code createdTxn != null}인 행은 {@code confirm()}이 건너뜀</li>
   *   <li>장부 수준: {@code postRow()}의 fingerprint 중복 검사</li>
   * </ol>
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public EasyCollectionApproval commitApproved(Long businessId, Long approvalId,
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    // 1단계: 검증 (읽기 전용 트랜잭션 — 지연 로딩 연관을 세션 안에서 읽는다)
    CommitTarget target = readTx().execute(status ->
        validateCommitTarget(businessId, approvalId, principal));

    // 이미 반영 완료 — 멱등성
    if (target.approval().isCommitted()) {
      log.info("collection approval already committed approvalId={}", approvalId);
      return approvalRepository.findById(approvalId).orElseThrow();
    }

    // 2단계: 잠금 아래 장부 commit + COMMITTED 기록 (한 트랜잭션)
    try {
      writeTx().executeWithoutResult(status -> commitLocked(businessId, approvalId, principal));
    } catch (RuntimeException e) {
      // 3단계: 실패 기록 (별도 트랜잭션 — 위 트랜잭션의 롤백과 무관하게 남는다)
      String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      writeTx().executeWithoutResult(status -> markCommitFailedTx(approvalId, errorMsg));
      log.info("collection approval commit failed approvalId={} batchId={}",
          approvalId, target.batchId());
      return approvalRepository.findById(approvalId).orElseThrow();
    }
    log.info("collection approval committed businessId={} approvalId={} batchId={}",
        businessId, approvalId, target.batchId());
    return approvalRepository.findById(approvalId).orElseThrow();
  }

  /** 승인 단계 — 행 잠금 아래서 PENDING 여부를 확인하므로 동시 승인은 한쪽만 통과한다. */
  private void approveLocked(Long businessId, Long approvalId, String decidedBy, String note,
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    EasyBusiness business = tutorAccessService.requireAllowedBusiness(principal, businessId);
    EasyCollectionApproval approval = approvalRepository.findWithLockByApprovalId(approvalId)
        .orElseThrow(() -> new IllegalArgumentException("승인 요청을 찾을 수 없습니다: " + approvalId));
    if (!approval.getBusiness().getBusinessId().equals(business.getBusinessId())) {
      throw new SecurityException("다른 사업체의 승인 요청입니다.");
    }
    importService.requireCollectionReview(approval.getRun().getImportBatchId(), note);
    approval.approve(decidedBy, note);
    log.info("collection approval approved businessId={} approvalId={} decidedBy={}",
        businessId, approvalId, maskUser(decidedBy));
  }

  /** 반영 단계 — 호출한 쓰기 트랜잭션 안에서 잠금 → 장부 commit → COMMITTED 기록. */
  private void commitLocked(Long businessId, Long approvalId, JwtTokenProvider.AuthenticatedPrincipal principal) {
    EasyCollectionApproval approval = approvalRepository.findWithLockByApprovalId(approvalId)
        .orElseThrow(() -> new IllegalArgumentException("승인 요청을 찾을 수 없습니다: " + approvalId));
    if (approval.isCommitted()) {
      return; // 동시 요청이 먼저 반영함
    }
    if (approval.getStatus() != EasyCollectionApproval.ApprovalStatus.APPROVED
        && approval.getStatus() != EasyCollectionApproval.ApprovalStatus.COMMIT_FAILED) {
      throw new IllegalStateException("장부 반영할 수 없는 상태입니다: " + approval.getStatus());
    }
    Long batchId = approval.getRun().getImportBatchId();
    if (batchId == null) {
      throw new IllegalStateException("연결된 import 배치가 없습니다.");
    }
    importService.confirmApprovedCollection(businessId, approvalId, principal);
    approval.markCommitted();
  }

  private TransactionTemplate writeTx() {
    return new TransactionTemplate(transactionManager);
  }

  private TransactionTemplate readTx() {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setReadOnly(true);
    return template;
  }

  /** 검증용 읽기 — 테넌트·상태·batchId를 확인하고 반환한다. */
  @Transactional(readOnly = true)
  public CommitTarget validateCommitTarget(Long businessId, Long approvalId,
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    EasyBusiness business = tutorAccessService.requireAllowedBusiness(principal, businessId);

    EasyCollectionApproval approval = approvalRepository.findById(approvalId)
        .orElseThrow(() -> new IllegalArgumentException("승인 요청을 찾을 수 없습니다: " + approvalId));

    if (!approval.getBusiness().getBusinessId().equals(business.getBusinessId())) {
      throw new SecurityException("다른 사업체의 승인 요청입니다.");
    }

    if (approval.getStatus() != EasyCollectionApproval.ApprovalStatus.APPROVED
        && approval.getStatus() != EasyCollectionApproval.ApprovalStatus.COMMIT_FAILED
        && approval.getStatus() != EasyCollectionApproval.ApprovalStatus.COMMITTED) {
      throw new IllegalStateException("장부 반영할 수 없는 상태입니다: " + approval.getStatus());
    }

    Long batchId = approval.getRun().getImportBatchId();
    if (batchId == null
        && approval.getStatus() != EasyCollectionApproval.ApprovalStatus.COMMITTED) {
      throw new IllegalStateException("연결된 import 배치가 없습니다.");
    }

    return new CommitTarget(approval, batchId);
  }

  /** COMMITTED 기록 — 호출자의 쓰기 트랜잭션 안에서 행 잠금 후 기록한다. */
  @Transactional
  public void markCommittedTx(Long approvalId) {
    EasyCollectionApproval approval = approvalRepository.findWithLockByApprovalId(approvalId)
        .orElseThrow(() -> new IllegalArgumentException("승인 요청을 찾을 수 없습니다: " + approvalId));
    approval.markCommitted();
  }

  /** COMMIT_FAILED 기록 — 호출자의 쓰기 트랜잭션 안에서 행 잠금 후 기록한다. COMMITTED면 덮어쓰지 않는다. */
  @Transactional
  public void markCommitFailedTx(Long approvalId, String error) {
    EasyCollectionApproval approval = approvalRepository.findWithLockByApprovalId(approvalId)
        .orElseThrow(() -> new IllegalArgumentException("승인 요청을 찾을 수 없습니다: " + approvalId));
    if (approval.isCommitted()) {
      return; // 다른 요청이 이미 반영 완료 — 실패로 되돌리지 않는다
    }
    approval.markCommitFailed(error);
  }

  /** 검증 결과 홀더. */
  public record CommitTarget(EasyCollectionApproval approval, Long batchId) {
  }

  /** 사용자 식별자 마스킹 (로그용). */
  private String maskUser(String user) {
    if (user == null || user.length() <= 2) {
      return "***";
    }
    return user.substring(0, 2) + "***";
  }
}
