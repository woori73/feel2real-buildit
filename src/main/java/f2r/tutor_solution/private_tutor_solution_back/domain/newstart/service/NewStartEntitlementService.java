package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberGrantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NewStart 실행 권한 판정 단일 지점.
 *
 * <p>SecurityConfig는 DB를 보지 못하므로 matcher로는 표현할 수 없는 권한이다. 대신 모든
 * gating 지점이 이 서비스를 통과한다 — 화면에서 버튼을 숨기는 것만으로 처리하지 않는다
 * (비로그인 요청은 {@code /api/**} catch-all이 401, 권한 없는 회원은 여기서 403).
 *
 * <p>ADMIN은 capability row 없이 통과한다(운영자 = ALL).
 */
@Service
public class NewStartEntitlementService {

  /**
   * 권한 미부여 회원에게 내보내는 사용자 안내(내부 권한명·예외 상세는 넣지 않는다).
   *
   * <p>문구는 "감사회원 전용"을 명시한다 — 이전 문구(회원 전용 + 운영자에게 이용 신청 문의)는
   * 사용자가 "운영자만 쓸 수 있다"고 읽어서 감사회원 승인을 요청하지 못한 적이 있다(2026-09-28 보고).
   */
  public static final String DENIAL_MESSAGE = "감사회원 전용 기능입니다. 감사회원 승인을 운영자에게 요청해 주세요.";

  private final NewStartMemberGrantRepository grantRepository;

  public NewStartEntitlementService(NewStartMemberGrantRepository grantRepository) {
    this.grantRepository = grantRepository;
  }

  @Transactional(readOnly = true)
  public boolean allows(AuthenticatedPrincipal principal, NewStartCapability capability) {
    if (principal == null) return false;
    if ("ADMIN".equals(principal.role())) return true;
    return grantRepository.existsByUserIdAndCapability(principal.userId(), capability);
  }

  @Transactional(readOnly = true)
  public boolean isThanksMember(AuthenticatedPrincipal principal) {
    return allows(principal, NewStartCapability.THANKS_MEMBER);
  }

  /** 통과 못 하면 {@link NewStartEntitlementDeniedException}(→ 403 안내)로 차단한다. */
  @Transactional(readOnly = true)
  public void require(AuthenticatedPrincipal principal, NewStartCapability capability) {
    if (!allows(principal, capability)) throw new NewStartEntitlementDeniedException();
  }

  @Transactional(readOnly = true)
  public void requireThanksMember(AuthenticatedPrincipal principal) {
    require(principal, NewStartCapability.THANKS_MEMBER);
  }
}
