package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartChatService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartChatService.NewStartChatMessageView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartChatService.NewStartChatSendResult;
import java.net.URI;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@DisplayName("NewStartChatWebSocketHandler — principal 기반 전송 위임")
class NewStartChatWebSocketHandlerTest {

  private NewStartChatService service;
  private NewStartChatBroadcaster broadcaster;
  private NewStartChatWebSocketHandler handler;

  @BeforeEach
  void setUp() {
    service = mock(NewStartChatService.class);
    broadcaster = mock(NewStartChatBroadcaster.class);
    handler = new NewStartChatWebSocketHandler(service, broadcaster);
  }

  @Test
  @DisplayName("given 검증된 principal 세션 when 메시지 then service에 principal과 본문을 위임한다")
  void delegatesToServiceWithPrincipal() throws Exception {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getAttributes()).thenReturn(new HashMap<>(java.util.Map.of(
        NewStartChatHandshakeInterceptor.CHAT_PRINCIPAL_ATTR,
        new JwtTokenProvider.AuthenticatedPrincipal(1L, "u1", "STUDENT"))));
    when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/newstart-chat"));
    when(service.send(any(), anyString())).thenReturn(NewStartChatSendResult.success(
        new NewStartChatMessageView(1L, 1L, "별명", "안녕", false, "2026-09-19T09:00:00")));

    handler.handleTextMessage(session, new TextMessage("{\"message\":\"안녕\"}"));

    verify(service).send(any(JwtTokenProvider.AuthenticatedPrincipal.class), anyString());
  }

  @Test
  @DisplayName("given principal 없는 세션 when 메시지 then 세션을 닫고 service를 호출하지 않는다")
  void closesSessionWithoutPrincipal() throws Exception {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getAttributes()).thenReturn(new HashMap<>());

    handler.handleTextMessage(session, new TextMessage("{\"message\":\"해킹 시도\"}"));

    verify(session).close(CloseStatus.POLICY_VIOLATION);
    verify(service, org.mockito.Mockito.never()).send(any(), anyString());
  }

  @Test
  @DisplayName("given 잘못된 JSON when 메시지 then 오류 이벤트를 보내고 service를 호출하지 않는다")
  void sendsErrorOnMalformedJson() throws Exception {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getAttributes()).thenReturn(new HashMap<>(java.util.Map.of(
        NewStartChatHandshakeInterceptor.CHAT_PRINCIPAL_ATTR,
        new JwtTokenProvider.AuthenticatedPrincipal(1L, "u1", "STUDENT"))));
    when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/newstart-chat"));
    doThrow(new RuntimeException("nope")).when(service).send(any(), anyString());

    handler.handleTextMessage(session, new TextMessage("not-json"));

    verify(broadcaster).sendJson(any(), any());
    verify(service, org.mockito.Mockito.never()).send(any(), anyString());
  }

  @Test
  @DisplayName("given 연결 종료 when afterConnectionClosed then registry에서 제거한다")
  void unregistersOnClose() {
    WebSocketSession session = mock(WebSocketSession.class);

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    verify(broadcaster).unregister(session);
    assertThat(true).isTrue();
  }
}
