package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 대화형 홈페이지 화면(통합 화면)의 기능 스위치.
 *
 * <p>{@code buildit.assistant.enabled}가 꺼져 있으면(기본) 모두 기존 빌더를 쓴다. 켜져 있고
 * {@code buildit.assistant.tutor-login-ids}가 비어 있으면 모든 TUTOR, 값이 있으면 그 계정만 쓴다
 * (관리자 시범 운영). 어느 경우든 {@code /builder?classic=1}로 기존 빌더에 돌아올 수 있다.
 *
 * <p>운영(prd 프로필)에서는 허용 계정이 비어 있으면 켜져 있어도 <b>모두에게 꺼진다</b>(안전장치). 설정 실수로 전체
 * TUTOR에게 새 기능이 열리는 일을 막는다. 개발·테스트 환경은 기존대로 비어 있으면 전체 공개다.
 */
@Component
public class AssistantFeatureFlag {

  private final boolean enabled;
  private final Set<String> allowedLoginIds;
  private final boolean requireAllowList;

  @Autowired
  public AssistantFeatureFlag(
      @Value("${buildit.assistant.enabled:false}") boolean enabled,
      @Value("${buildit.assistant.tutor-login-ids:}") String loginIds,
      Environment environment) {
    this(enabled, loginIds, environment.acceptsProfiles(Profiles.of("prd")));
  }

  /** 개발·테스트용: 허용 목록이 비어 있으면 전체 공개. */
  public AssistantFeatureFlag(boolean enabled, String loginIds) {
    this(enabled, loginIds, false);
  }

  AssistantFeatureFlag(boolean enabled, String loginIds, boolean requireAllowList) {
    this.requireAllowList = requireAllowList;
    this.enabled = enabled;
    this.allowedLoginIds = Arrays.stream(loginIds.split(","))
        .map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toSet());
  }

  public boolean isOn(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (!enabled || principal == null) return false;
    if (allowedLoginIds.isEmpty()) return !requireAllowList;
    return allowedLoginIds.contains(principal.loginId());
  }
}
