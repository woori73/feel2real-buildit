package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 대화형 홈페이지 화면(통합 화면)의 기능 스위치.
 *
 * <p>{@code buildit.assistant.enabled}가 꺼져 있으면(기본) 모두 기존 빌더를 쓴다. 켜져 있고
 * {@code buildit.assistant.tutor-login-ids}가 비어 있으면 모든 TUTOR, 값이 있으면 그 계정만 쓴다
 * (관리자 시범 운영). 어느 경우든 {@code /builder?classic=1}로 기존 빌더에 돌아올 수 있다.
 */
@Component
public class AssistantFeatureFlag {

  private final boolean enabled;
  private final Set<String> allowedLoginIds;

  public AssistantFeatureFlag(
      @Value("${buildit.assistant.enabled:false}") boolean enabled,
      @Value("${buildit.assistant.tutor-login-ids:}") String loginIds) {
    this.enabled = enabled;
    this.allowedLoginIds = Arrays.stream(loginIds.split(","))
        .map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toSet());
  }

  public boolean isOn(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (!enabled || principal == null) return false;
    return allowedLoginIds.isEmpty() || allowedLoginIds.contains(principal.loginId());
  }
}
