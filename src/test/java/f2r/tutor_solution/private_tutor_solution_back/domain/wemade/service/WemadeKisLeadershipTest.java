package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.cluster.MysqlNamedLocks;
import java.net.http.WebSocket;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WemadeKisLeadershipTest {
  @Test void disabledByDefaultNeverAcquiresOrConnects() {
    var client = mock(WemadeKisClient.class);
    var locks = mock(MysqlNamedLocks.class);
    var socket = new WemadeKisWebSocket(client, new ObjectMapper(), locks, false);
    socket.start();
    assertFalse(socket.isLive());
    verifyNoInteractions(locks, client);
    socket.stop();
  }

  @Test void leadershipLossAbortsSocketBeforeReleasingDatabaseSession() {
    var session = mock(MysqlNamedLocks.Session.class);
    var connection = mock(WebSocket.class);
    var socket = new WemadeKisWebSocket(mock(WemadeKisClient.class), new ObjectMapper(), mock(MysqlNamedLocks.class), true);
    ReflectionTestUtils.setField(socket, "leadership", session);
    ReflectionTestUtils.setField(socket, "webSocket", connection);
    when(session.valid()).thenReturn(false);
    ReflectionTestUtils.invokeMethod(socket, "checkLeadership");
    var order = inOrder(connection, session);
    order.verify(connection).abort();
    order.verify(session).close();
    assertFalse(socket.isLive());
    assertNull(ReflectionTestUtils.getField(socket, "leadership"));
    socket.stop();
    verify(connection, times(1)).abort();
    verify(session, times(1)).close();
  }
}
