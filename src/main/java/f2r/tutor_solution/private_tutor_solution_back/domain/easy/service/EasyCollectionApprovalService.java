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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 자료 장부 반영 승인 서비스 — P1.
 *
 * <p>보안 규칙:
 * <ul>
 *   <li>승인 전에는 장부에 반영되지 않는다</li>
 *   <li>승인 재시도 시 중복 반영 방지 (멱등성)</li>
 *   <li>승인·반려는 감사 로그에 기록된다</li>
 *   <li>테넌트 격리: 요청자의 사업체만 처리 가능</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyCollectionApprovalService {

  private static final Logger log = LoggerFactory.getLogger(EasyCollectionApprovalService.class);

  private final EasyCollectionApprovalRepository approvalRepository;
  private final EasyCollectionRunRepository runRepository;
  private final EasyTutorAccessService tutorAccessService;

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

  /** 사용자 식별자 마스킹 (로그용). */
  private String maskUser(String user) {
    if (user == null || user.length() <= 2) {
      return "***";
    }
    return user.substring(0, 2) + "***";
  }
}
