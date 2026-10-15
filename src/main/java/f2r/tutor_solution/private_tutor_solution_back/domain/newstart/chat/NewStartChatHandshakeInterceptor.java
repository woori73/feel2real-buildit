package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * 회원 채팅 WebSocket handshake 검증.
 *
 * <ol>
 *   <li>동일 origin만 통과 — 교차 사이트가 브라우저의 JWT 쿠키를 이용해 WS 연결을
 *       여는 공격(CSWSH)을 차단한다. WS handshake는 CSRF 토큰이 없으므로 origin
 *       검사가 유일한 방어선이다.</li>
 *   <li>JWT principal 검증 — handshake 요청은 기� {@code JwtAuthenticationFilter}를
 *       통과하므로 SecurityContext에 로그인 principal이 이미 있다. 없으면(비로그인/
 *       만료) 401로 handshake를 막는다. 비로그인은 애초에 WS에 붙지 않는다.</li>
 * </ol>
 *
 * <p>클라이언트가 보낸 userId/nickname은 절대 신뢰하지 않는다 — principal만 저장한다.
 */
@Slf4j
@Component
public class NewStartChatHandshakeInterceptor implements HandshakeInterceptor {

  /** WS 세션 attributes에 저장되는 검증된 principal key. */
  public static final String CHAT_PRINCIPAL_ATTR = "newstartChatPrincipal";

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      java.util.Map<String, Object> attributes) {
    if (!isSameOrigin(request)) {
      log.debug("newstart chat handshake origin 거부: {}", request.getHeaders().getOrigin());
      response.setStatusCode(HttpStatus.FORBIDDEN);
      return false;
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof JwtTokenProvider.AuthenticatedPrincipal principal) {
      attributes.put(CHAT_PRINCIPAL_ATTR, principal);
      return true;
    }
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    return false;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
    // no-op
  }

  /** 브라우저는 WS handshake에 항상 Origin을 실어 보낸다. 없으면 비정상 시도로 본다. */
  private boolean isSameOrigin(ServerHttpRequest request) {
    String origin = request.getHeaders().getOrigin();
    if (origin == null || origin.isBlank()) {
      return false;
    }
    try {
      String originHost = URI.create(origin).getHost();
      String requestHost = request.getURI().getHost();
      return originHost != null
          && requestHost != null
          && originHost.equalsIgnoreCase(requestHost);
    } catch (RuntimeException e) {
      return false;
    }
  }
}
