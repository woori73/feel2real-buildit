package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class CodiVoiceConnectionTest {
  @Test void givenApprovedTask_whenStartingAndReading_thenUsesProviderWireContract() throws Exception {
    var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    var body = new AtomicReference<String>();
    var key = new AtomicReference<String>();
    server.createContext("/sip-trunk/outbound-call", exchange -> {
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      key.set(exchange.getRequestHeaders().getFirst("xi-api-key"));
      exchange.sendResponseHeaders(200, 0);
      exchange.getResponseBody().write("{\"success\":true,\"conversation_id\":\"conv_test\"}".getBytes());
      exchange.close();
    });
    server.createContext("/conversations/conv_test", exchange -> {
      exchange.sendResponseHeaders(200, 0);
      exchange.getResponseBody().write("{\"conversation_id\":\"conv_test\",\"status\":\"done\",\"analysis\":{\"transcript_summary\":\"재고 8권\"}}".getBytes(StandardCharsets.UTF_8));
      exchange.close();
    });
    server.start();
    try {
      var provider = new CodiVoiceProvider(URI.create("http://localhost:" + server.getAddress().getPort() + "/"), "test-key", "agent", "line");
      var call = provider.start("+821000000000", "교재 재고 문의");
      assertEquals("initiated", call.status());
      assertEquals("test-key", key.get());
      var payload = AssistantJson.read(body.get());
      assertEquals("line", payload.path("agent_phone_number_id").asText());
      assertEquals("+821000000000", payload.path("to_number").asText());
      assertFalse(payload.has("call_recording_enabled"));
      assertEquals("교재 재고 문의", payload.at("/conversation_initiation_client_data/dynamic_variables/call_task").asText());
      assertEquals("재고 8권", provider.status(call.conversation()).summary());
    } finally { server.stop(0); }
  }

  @Test void givenLostProviderResponse_whenReplayed_thenNeverDialsTwice() throws Exception {
    var provider = mock(CodiVoiceProvider.class);
    var attempts = mock(CodiCallRepository.class);
    var permissions = mock(UnifiedAssistantService.class);
    var academy = mock(AcademyAssistantAdapter.class);
    var calls = new CodiCalls(permissions, academy, provider, attempts);
    var principal = new AuthenticatedPrincipal(3L, "pilot", "TUTOR");
    var id = UUID.randomUUID();
    var request = new CodiCalls.Request(id, 7L, "010-0000-0000", "재고 문의", true);
    when(provider.configured()).thenReturn(true);
    assertThrows(ResponseStatusException.class, () -> calls.start(principal, request));
    verifyNoInteractions(attempts);
    ReflectionTestUtils.setField(calls, "enabled", true);
    ReflectionTestUtils.setField(calls, "numbers", List.of("+821000000000"));
    assertThrows(ResponseStatusException.class, () -> calls.start(principal, new CodiCalls.Request(id, 7L, request.number(), request.task(), false)));
    assertThrows(ResponseStatusException.class, () -> calls.start(principal, new CodiCalls.Request(id, 7L, "119", request.task(), true)));
    var saved = new AtomicReference<CodiCallAttempt>();
    doAnswer(invocation -> { saved.set(invocation.getArgument(0)); return null; })
        .doThrow(new DuplicateKeyException("duplicate")).when(attempts).claim(any());
    when(attempts.find(id.toString(), 3L, 7L)).thenAnswer(invocation -> Optional.of(saved.get()));
    when(provider.start(anyString(), anyString())).thenThrow(new java.io.IOException());
    assertEquals("unconfirmed", calls.start(principal, request).status());
    assertEquals("unconfirmed", calls.start(principal, request).status());
    verify(provider, times(1)).start("+821000000000", request.task());
    assertThrows(ResponseStatusException.class, () -> calls.status(principal, 8L, id));
    verify(academy).owner(principal, 8L);
  }
}
