package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 문법 문제은행 접근 권한 — ADMIN 계정과 지정한 운영 계정(기본 loginId {@code modu})만 허용한다.
 *
 * <p>modu에는 ADMIN 권한을 부여하지 않는다. 판정은 인증된 주체의 고유 식별자(서명된 JWT의 user_id,
 * 즉 DB {@code users.user_id}) 기준으로 하되, 문자열 loginId만 신뢰하지 않고 저장소에서 조회한
 * modu의 실제 user_id와 대조한다. 다른 서비스 권한·계정 데이터는 건드리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrammarBankAccessGuard {

  private static final String ADMIN_ROLE = "ADMIN";

  private final UserRepository userRepository;

  @Value("${grammar-bank.access.operator-login-id:modu}")
  private String operatorLoginId;

  /** 저장소에서 조회한 운영 계정 고윳셈(user_id). 조회 성공 시에만 캐싱해 계정 생성·교체를 따라간다. */
  private final AtomicReference<Long> cachedOperatorUserId = new AtomicReference<>();

  public boolean isAllowed(AuthenticatedPrincipal principal) {
    if (principal == null) {
      return false;
    }
    if (ADMIN_ROLE.equalsIgnoreCase(principal.role())) {
      return true;
    }
    Long operatorUserId = resolveOperatorUserId();
    return operatorUserId != null && operatorUserId.equals(principal.userId());
  }

  /** 미로그인은 401, 로그인했으나 권한이 없으면 403. 모든 문법 문제은행 API·화면에서 동일하게 적용한다. */
  public void require(AuthenticatedPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    if (!isAllowed(principal)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "문법 문제은행 접근 권한이 없습니다.");
    }
  }

  private Long resolveOperatorUserId() {
    Long cached = cachedOperatorUserId.get();
    if (cached != null) {
      return cached;
    }
    User operator = userRepository.findByLoginId(operatorLoginId).orElse(null);
    if (operator == null) {
      return null;
    }
    cachedOperatorUserId.set(operator.getUserId());
    log.debug("문법 문제은행 운영 계정 확인: loginId={} 고유 식별자 매핑 완료", operatorLoginId);
    return operator.getUserId();
  }
}
