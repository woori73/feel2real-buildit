package f2r.tutor_solution.private_tutor_solution_back.config.security;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 쿠키에서 JWT를 읽어 SecurityContext를 채우는 필터.
 *
 * <p>토큰이 없거나 검증 실패면 그냥 통과시킨다 — 인증 자체는 막지 않는다. 보호된 엔드포인트라면
 * 다음 Security 단계(AuthorizationFilter)에서 401/403으로 떨어진다. permitAll 엔드포인트는 그대로 진행.
 *
 * <p>인증 성공 시 {@link AuditorContextHolder}에 loginId를 set하여 BaseEntity의 created_by/updated_by가
 * 자동으로 채워지게 한다. 요청 종료 시점에 반드시 clear.
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider tokenProvider;
  private final CookieUtil cookieUtil;
  // ADMIN 세션의 서버 측 유효성 확인(JPA 의존 → ObjectProvider 지연 조회, @WebMvcTest 슬라이스 호환).
  private final org.springframework.beans.factory.ObjectProvider<
      f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminSessionValidator> adminSessionValidator;

  public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, CookieUtil cookieUtil) {
    this(tokenProvider, cookieUtil, null);
  }

  public JwtAuthenticationFilter(
      JwtTokenProvider tokenProvider,
      CookieUtil cookieUtil,
      org.springframework.beans.factory.ObjectProvider<
          f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminSessionValidator> adminSessionValidator) {
    this.tokenProvider = tokenProvider;
    this.cookieUtil = cookieUtil;
    this.adminSessionValidator = adminSessionValidator;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {

    Optional<String> token = cookieUtil.readAccessToken(request);
    boolean auditSet = false;
    try {
      if (token.isPresent()) {
        try {
          JwtTokenProvider.AuthenticatedPrincipal principal = tokenProvider.parse(token.get());
          var validator = adminSessionValidator == null ? null : adminSessionValidator.getIfAvailable();
          if (validator != null && !validator.isValid(principal)) {
            // ID·비밀번호가 바뀌었거나 stamp 없는 이전 ADMIN 세션 — 인증 미설정으로 통과(보호 경로는 401).
            log.debug("ADMIN 세션 무효(자격 증명 변경)");
            chain.doFilter(request, response);
            return;
          }
          var authority = new SimpleGrantedAuthority("ROLE_" + principal.role());
          var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
          SecurityContextHolder.getContext().setAuthentication(auth);
          AuditorContextHolder.set(principal.loginId());
          auditSet = true;
        } catch (JwtException | IllegalArgumentException e) {
          // 위변조/만료/형식 오류 — 인증 미설정 상태로 통과. 보호 엔드포인트에서 401로 떨어짐.
          log.debug("JWT 검증 실패: {}", e.getMessage());
        }
      }
      chain.doFilter(request, response);
    } finally {
      if (auditSet) AuditorContextHolder.clear();
    }
  }
}
