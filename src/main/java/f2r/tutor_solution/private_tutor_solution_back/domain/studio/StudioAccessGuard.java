package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI AGENT 제작소 진입 권한 — ADMIN 계정과 지정한 운영 계정(기본 loginId {@code modu})만 허용한다.
 *
 * <p>SecurityConfig가 화면·API·파일 다운로드·MCP 내부 호출을 포함한 제작소 전 경로에서 이 판정을 쓴다.
 * modu에는 ADMIN 권한을 부여하지 않는다. 판정은 서명된 JWT의 user_id를 저장소에서 조회한 modu의 실제
 * user_id와 대조하며, 문자열 loginId만 신뢰하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class StudioAccessGuard {

  private final UserRepository userRepository;

  @Value("${app.studio.access.operator-login-id:modu}")
  private String operatorLoginId;

  /** false면 기존 초대 회원도 진입할 수 있다(격리 테스트 전용). 운영 기본값은 true. */
  @Value("${app.studio.access.operators-only:true}")
  private boolean operatorsOnly = true;

  /** 조회 성공 시에만 캐싱해 계정 생성·교체를 따라간다. */
  private final AtomicReference<Long> cachedOperatorUserId = new AtomicReference<>();

  public boolean isAllowed(AuthenticatedPrincipal p) {
    if (p == null) return false;
    return !operatorsOnly || "ADMIN".equals(p.role()) || isOperator(p);
  }

  public boolean isOperator(AuthenticatedPrincipal p) {
    if (p == null || p.userId() == null) return false;
    Long operatorUserId = cachedOperatorUserId.get();
    if (operatorUserId == null) {
      operatorUserId = userRepository.findByLoginId(operatorLoginId).map(User::getUserId).orElse(null);
      if (operatorUserId == null) return false;
      cachedOperatorUserId.set(operatorUserId);
    }
    return operatorUserId.equals(p.userId());
  }
}
