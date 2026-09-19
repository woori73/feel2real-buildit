package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartChatService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * NewStart 회원 채팅 WebSocket handler (raw WebSocket + JSON, 단일 공용방).
 *
 * <p>클라이언트 → 서버: {@code {"message": "본문"}} 한 가지뿐이다. 작성자 신원은
 * handshake interceptor가 검증해 세션 attributes에 넣은 principal에서 결정한다 —
 * 클라이언트가 userId/nickname을 보낼 통로 자체가 없다(사칭 원천 차단).
 *
 * <p>서버 → 클라이언트: {@code new}/{@code deleted}/{@code error} 이벤트
 * ({@link NewStartChatMessageEvent}). 전송 결과는 성공/실패 관계없이 방 전체에
 * broadcast된 new 이벤트가 원본이다(전송자 포함 — 클라이언트는 id로 중복 렌더를 막는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewStartChatWebSocketHandler extends TextWebSocketHandler {

  private final NewStartChatService newStartChatService;
  private final NewStartChatBroadcaster broadcaster;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    broadcaster.register(session);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    broadcaster.unregister(session);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    Object attribute = session.getAttributes().get(NewStartChatHandshakeInterceptor.CHAT_PRINCIPAL_ATTR);
    if (!(attribute instanceof JwtTokenProvider.AuthenticatedPrincipal principal)) {
      try {
        session.close(CloseStatus.POLICY_VIOLATION);
      } catch (IOException ignored) {
        // 닫기 실패는 세션 정리에 맡긴다.
      }
      return;
    }
    ChatSendPayload payload;
    try {
      payload = objectMapper.readValue(message.getPayload(), ChatSendPayload.class);
    } catch (IOException e) {
      broadcaster.sendJson(session, NewStartChatMessageEvent.error("메시지 형식이 올바르지 않습니다."));
      return;
    }
    newStartChatService.send(principal, payload.message());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.debug("newstart chat transport error: {}", exception.getMessage());
    broadcaster.unregister(session);
  }

  /** 클라이언트가 보내는 유일한 payload. userId/nickname은 구조적으로 없다. */
  public record ChatSendPayload(String message) {}
}
