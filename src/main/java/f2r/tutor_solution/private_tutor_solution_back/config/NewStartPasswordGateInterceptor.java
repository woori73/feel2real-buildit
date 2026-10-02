package f2r.tutor_solution.private_tutor_solution_back.config;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * ADMIN이 임시 비밀번호로 초기화한 회원이 새 비밀번호를 정하기 전에는 NewStart 기능을 쓰지 못하게
 * 하는 서버 게이트.
 *
 * <p>화면 안내나 JS 분기로만 막지 않는다 — 직접 URL·API 호출까지 서버가 차단해야 "강제 이동"이
 * 실제로 성립한다. 대상은 {@code /newstart/**} 화면과 {@code /api/newstart/**} API이고,
 * 로그인 화면·비밀번호 설정 화면·설정 API·가입 API는 WebMvcConfig에서 제외했다(그 길목에서
 * 잡히면 영원히 빠져나올 수 없다).
 *
 * <p>화면 요청은 302로 {@code /newstart/password/set}에 보내고, API 요청은 428 +
 * {@code PASSWORD_CHANGE_REQUIRED}로 돌려보낸다(브라우저가 JS 응답을 HTML처럼 리다이렉트하지 않도록).
 * ADMIN·운영 계정은 이 게이트를 적용하지 않는다 — 운영자가 회원 화면을 열어 확인하는 길은 막지 않는다.
 *
 * <p>{@code HandlerInterceptor} 구현체는 {@code @WebMvcTest} 슬라이스가 스캔하지만 JPA 레포는 없다.
 * 그래서 {@code UserRepository}를 지연 조회하고, 없는 경우(슬라이스 전용 상황)에만 게이트를
 * 건너뛴다. 전체 부팅에서는 레포가 항상 등록되므로 이 분기는 운영 경로에서 성립하지 않는다.
 */
@Component
public class NewStartPasswordGateInterceptor implements HandlerInterceptor {

  public static final String SET_PASSWORD_PATH = "/newstart/password/set";
  public static final String API_ERROR_CODE = "PASSWORD_CHANGE_REQUIRED";
  private static final String DENY_MESSAGE = "새 비밀번호를 먼저 설정해 주세요.";

  private final ObjectProvider<UserRepository> userRepositoryProvider;

  public NewStartPasswordGateInterceptor(ObjectProvider<UserRepository> userRepositoryProvider) {
    this.userRepositoryProvider = userRepositoryProvider;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    AuthenticatedPrincipal principal = currentPrincipal();
    if (principal == null || !"STUDENT".equals(principal.role())) {
      return true;
    }
    UserRepository userRepository = userRepositoryProvider.getIfAvailable();
    if (userRepository == null) {
      return true;
    }
    if (!userRepository.existsByUserIdAndPasswordResetRequired(principal.userId(), true)) {
      return true;
    }

    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (path.startsWith("/api/")) {
      response.setStatus(HttpStatus.PRECONDITION_REQUIRED.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getWriter().write("{\"code\":\"" + API_ERROR_CODE + "\",\"message\":\"" + DENY_MESSAGE + "\"}");
      return false;
    }
    response.sendRedirect(request.getContextPath() + SET_PASSWORD_PATH);
    return false;
  }

  private AuthenticatedPrincipal currentPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
      return null;
    }
    return principal;
  }
}
