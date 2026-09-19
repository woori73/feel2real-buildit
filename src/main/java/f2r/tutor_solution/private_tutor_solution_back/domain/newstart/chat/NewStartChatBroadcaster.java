package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * NewStart 회원 채팅 세션 registry + broadcast. 공용방 1개이므로 세션 목록 전체에
 * 같은 이벤트를 보내는 가장 단순한 구조다.
 *
 * <p>전송 실패 세션은 조용히 registry에서 제거한다(죽은 세션이 브로드캐스트를 막지 않게).
 * 반대 방향으로, broadcast 실패가 메시지 저장을 되돌리지 않는다 — 저장은 service가 먼저 끝낸다.
 */
@Slf4j
@Component
public class NewStartChatBroadcaster {

  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public void register(WebSocketSession session) {
    sessions.put(session.getId(), session);
  }

  public void unregister(WebSocketSession session) {
    WebSocketSession removed = sessions.remove(session.getId());
    if (removed != null && removed.isOpen()) {
      try {
        removed.close();
      } catch (IOException ignored) {
        // 이미 닫힌 세션 정리 실패는 무시한다.
      }
    }
  }

  /** 특정 세션(주로 전송자 본인)에 단건 이벤트를 보낸다 — 오류 응답 등. */
  public void sendJson(WebSocketSession session, Object payload) {
    String json = toJson(payload);
    if (json == null) {
      return;
    }
    send(session, json);
  }

  /** 방 전체에 이벤트를 JSON으로 보낸다. 직렬화 실패는 조용히 버린다(로그만). */
  public void broadcast(Object payload) {
    String json = toJson(payload);
    if (json == null) {
      return;
    }
    sessions.values().forEach(session -> send(session, json));
  }

  public int sessionCount() {
    return sessions.size();
  }

  private void send(WebSocketSession session, String json) {
    if (!session.isOpen()) {
      sessions.remove(session.getId());
      return;
    }
    try {
      // sendMessage는 동시 호출 시 스레드 안전하지 않다 — 세션 단위로 직렬화한다.
      synchronized (session) {
        session.sendMessage(new TextMessage(json));
      }
    } catch (IOException | IllegalStateException e) {
      log.debug("newstart chat broadcast skip session {}", session.getId());
      unregister(session);
    }
  }

  private String toJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      log.warn("newstart chat event 직렬화 실패", e);
      return null;
    }
  }
}
