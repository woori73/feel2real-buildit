package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("NewStartPasswordGateInterceptor — 새 비밀번호를 정할 때까지 다른 NewStart 기능을 막는다")
class NewStartPasswordGateInterceptorTest {

  private UserRepository userRepository;
  private NewStartPasswordGateInterceptor interceptor;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
    beanFactory.addBean("userRepository", userRepository);
    interceptor = new NewStartPasswordGateInterceptor(beanFactory.getBeanProvider(UserRepository.class));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("초기화된 회원의 화면 요청 → 302로 새 비밀번호 설정 화면에 보낸다")
  void forcedMemberPageIsRedirected() throws Exception {
    login(77L, "ns-member-01", "STUDENT");
    when(userRepository.existsByUserIdAndPasswordResetRequired(77L, true)).thenReturn(true);
    MockHttpServletResponse response = response();

    boolean allowed = interceptor.preHandle(request("GET", "/newstart/thanks"), response, new Object());

    assertThat(allowed).isFalse();
    assertThat(response.getRedirectedUrl())
        .isEqualTo(NewStartPasswordGateInterceptor.SET_PASSWORD_PATH);
  }

  @Test
  @DisplayName("초기화된 회원의 API 호출 → 428 + PASSWORD_CHANGE_REQUIRED (HTML로 위장 리다이렉트 하지 않는다)")
  void forcedMemberApiIsRejectedWith428() throws Exception {
    login(77L, "ns-member-01", "STUDENT");
    when(userRepository.existsByUserIdAndPasswordResetRequired(77L, true)).thenReturn(true);
    MockHttpServletResponse response = response();

    boolean allowed = interceptor.preHandle(request("POST", "/api/newstart/thanks/sessions"), response, new Object());

    assertThat(allowed).isFalse();
    assertThat(response.getStatus()).isEqualTo(428);
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(response.getContentAsString())
        .contains(NewStartPasswordGateInterceptor.API_ERROR_CODE)
        .doesNotContain("kq7m2xt9pbad");
  }

  @Test
  @DisplayName("초기화되지 않은 회원은 통과 — 게이트가 다른 회원을 막지 않는다")
  void unaffectedMemberPassesThrough() throws Exception {
    login(77L, "ns-member-01", "STUDENT");
    when(userRepository.existsByUserIdAndPasswordResetRequired(77L, true)).thenReturn(false);

    assertThat(interceptor.preHandle(request("GET", "/newstart/thanks"), response(), new Object())).isTrue();
  }

  @Test
  @DisplayName("ADMIN·비로그인은 DB를 보지 않고 통과 — 운영자가 회원 화면을 열어 확인하는 길은 막지 않는다")
  void adminAndAnonymousBypassTheFlagLookup() throws Exception {
    login(1L, "admin-f2r", "ADMIN");
    assertThat(interceptor.preHandle(request("GET", "/newstart/thanks"), response(), new Object())).isTrue();

    SecurityContextHolder.clearContext();
    assertThat(interceptor.preHandle(request("GET", "/newstart"), response(), new Object())).isTrue();

    verify(userRepository, never()).existsByUserIdAndPasswordResetRequired(anyLong(), anyBoolean());
  }

  @Test
  @DisplayName("JPA 레포가 컨텍스트에 없는 슬라이스에서는 게이트가 뜨지 않는다 (@WebMvcTest 호환)")
  void sliceWithoutRepositoryDoesNotBreak() throws Exception {
    login(77L, "ns-member-01", "STUDENT");
    NewStartPasswordGateInterceptor sliceShaped = new NewStartPasswordGateInterceptor(
        new StaticListableBeanFactory().getBeanProvider(UserRepository.class));

    assertThat(sliceShaped.preHandle(request("GET", "/newstart/thanks"), response(), new Object())).isTrue();
  }

  @Test
  @DisplayName("리다이렉트는 컨텍스트 경로를 앞에 붙인다 — 하위 경로 배포에서도 절대 경로로 이동한다")
  void redirectKeepsContextPath() throws Exception {
    login(77L, "ns-member-01", "STUDENT");
    when(userRepository.existsByUserIdAndPasswordResetRequired(77L, true)).thenReturn(true);
    MockHttpServletRequest request = request("GET", "/newstart/chat");
    request.setContextPath("/app");
    MockHttpServletResponse response = response();

    interceptor.preHandle(request, response, new Object());

    assertThat(response.getRedirectedUrl()).isEqualTo("/app" + NewStartPasswordGateInterceptor.SET_PASSWORD_PATH);
  }

  private MockHttpServletRequest request(String method, String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setRequestURI(uri);
    return request;
  }

  private MockHttpServletResponse response() {
    return new MockHttpServletResponse();
  }

  private void login(long userId, String loginId, String role) {
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedPrincipal(userId, loginId, role), null, List.of()));
  }
}
