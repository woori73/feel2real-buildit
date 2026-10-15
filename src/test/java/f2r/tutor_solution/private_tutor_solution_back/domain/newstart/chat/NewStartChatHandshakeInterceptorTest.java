package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("NewStartChatHandshakeInterceptor — 동일 origin + 로그인 principal 검증")
class NewStartChatHandshakeInterceptorTest {

  private final NewStartChatHandshakeInterceptor interceptor = new NewStartChatHandshakeInterceptor();
  private final ServerHttpResponse response = mock(ServerHttpResponse.class);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private ServerHttpRequest request(String origin, String requestUri) {
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    HttpHeaders headers = new HttpHeaders();
    if (origin != null) {
      headers.set("Origin", origin);
    }
    when(request.getHeaders()).thenReturn(headers);
    when(request.getURI()).thenReturn(URI.create(requestUri));
    return request;
  }

  private void login(long userId) {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
        new JwtTokenProvider.AuthenticatedPrincipal(userId, "user" + userId, "STUDENT"), null));
  }

  @Test
  @DisplayName("given 동일 origin + 로그인 when handshake then principal을 세션에 넣고 통과한다")
  void passesSameOriginAuthenticatedHandshake() {
    login(1L);

    Map<String, Object> attributes = new HashMap<>();
    boolean allowed = interceptor.beforeHandshake(
        request("https://feel2real.co.kr", "wss://feel2real.co.kr/ws/newstart-chat"),
        response,
        null,
        attributes);

    assertThat(allowed).isTrue();
    assertThat(attributes.get(NewStartChatHandshakeInterceptor.CHAT_PRINCIPAL_ATTR))
        .isEqualTo(new JwtTokenProvider.AuthenticatedPrincipal(1L, "user1", "STUDENT"));
  }

  @Test
  @DisplayName("given 교차 origin when handshake then 403으로 막는다 (CSWSH 방어)")
  void rejectsCrossOriginHandshake() {
    login(1L);

    boolean allowed = interceptor.beforeHandshake(
        request("https://evil.example.com", "wss://feel2real.co.kr/ws/newstart-chat"),
        response,
        null,
        new HashMap<>());

    assertThat(allowed).isFalse();
    verify(response).setStatusCode(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("given 로그인 없는 동일 origin when handshake then 401로 막는다")
  void rejectsAnonymousHandshake() {
    boolean allowed = interceptor.beforeHandshake(
        request("https://feel2real.co.kr", "wss://feel2real.co.kr/ws/newstart-chat"),
        response,
        null,
        new HashMap<>());

    assertThat(allowed).isFalse();
    verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("given Origin 없는 요청 when handshake then 거부한다")
  void rejectsMissingOrigin() {
    login(1L);

    boolean allowed = interceptor.beforeHandshake(
        request(null, "wss://feel2real.co.kr/ws/newstart-chat"),
        response,
        null,
        new HashMap<>());

    assertThat(allowed).isFalse();
  }
}
