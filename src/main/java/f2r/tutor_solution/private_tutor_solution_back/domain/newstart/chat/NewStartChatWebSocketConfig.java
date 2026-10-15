package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * NewStart 회원 채팅 WebSocket 설정 — raw WebSocket({@code /ws/newstart-chat}).
 *
 * <p>STOMP는 쓰지 않는다. 공용방 1개에 필요한 것은 "구독 + 전송 broadcast"뿐이고,
 * 프런트에 STOMP client 라이브러리를 붙일 빌드 체계도 없어 raw WebSocket + JSON
 * 이벤트가 가장 단순하고 안정적이다. handshake 인증/origin 검사는
 * {@link NewStartChatHandshakeInterceptor}가 담당한다.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class NewStartChatWebSocketConfig implements WebSocketConfigurer {

  private final NewStartChatWebSocketHandler newStartChatWebSocketHandler;
  private final NewStartChatHandshakeInterceptor newStartChatHandshakeInterceptor;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(newStartChatWebSocketHandler, "/ws/newstart-chat")
        .addInterceptors(newStartChatHandshakeInterceptor);
  }
}
