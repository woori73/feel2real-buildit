package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.NewStartLlmProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport;
import java.io.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

class CollaborationProvidersTest {
  @Test
  void defaultNeverCallsPaidApiAndReadinessNeverExposesCredential() throws Exception {
    var muse = mock(MetaMuseSparkLlmTransport.class);
    var provider =
        new CollaborationProviders(new MockEnvironment(), muse, new NewStartLlmProperties());
    for (String agent : List.of("GPT", "CODEX", "CLAUDE", "MUSE")) {
      assertFalse(provider.readiness(agent).enabled());
      assertThrows(IllegalStateException.class, () -> provider.complete(agent, "data"));
    }
    verifyNoInteractions(muse);
  }

  MockEnvironment settings(String agent) {
    return new MockEnvironment()
        .withProperty("app.collaboration.api-enabled", "true")
        .withProperty("app.collaboration.daily-budget-microusd", "1000000")
        .withProperty("app.collaboration.providers." + agent + ".enabled", "true")
        .withProperty("app.collaboration.providers." + agent + ".billing-approved", "true")
        .withProperty("app.collaboration.providers." + agent + ".api-key", "SECRET_CANARY")
        .withProperty("app.collaboration.providers." + agent + ".model", "configured-test-model")
        .withProperty(
            "app.collaboration.providers." + agent + ".input-microusd-per-million-tokens",
            "1000000")
        .withProperty(
            "app.collaboration.providers." + agent + ".output-microusd-per-million-tokens",
            "2000000");
  }

  @Test
  void priceAndBillingMustBeExplicitAndUtf8ReservationIsConservative() {
    var env = settings("gpt");
    var provider =
        new CollaborationProviders(
            env, mock(MetaMuseSparkLlmTransport.class), new NewStartLlmProperties());
    assertTrue(provider.readiness("GPT").enabled());
    assertFalse(provider.readiness("GPT").toString().contains("SECRET_CANARY"));
    assertTrue(provider.reserveMicrousd("GPT", "한글") > provider.reserveMicrousd("GPT", "abc"));
    env.withProperty("app.collaboration.providers.gpt.billing-approved", "false");
    assertFalse(provider.readiness("GPT").enabled());
    env.withProperty("app.collaboration.providers.gpt.billing-approved", "true")
        .withProperty("app.collaboration.providers.gpt.input-microusd-per-million-tokens", "0");
    assertFalse(provider.readiness("GPT").enabled());
  }

  @SuppressWarnings("unchecked")
  @Test
  void officialWireContractsUseFixedOriginsAndDoNotImportAppConversationsOrTools()
      throws Exception {
    for (String agent : List.of("GPT", "CODEX", "CLAUDE")) {
      var provider =
          new CollaborationProviders(
              settings(agent.toLowerCase(Locale.ROOT)),
              mock(MetaMuseSparkLlmTransport.class),
              new NewStartLlmProperties());
      var client = mock(HttpClient.class);
      HttpResponse<InputStream> response = mock(HttpResponse.class);
      when(response.statusCode()).thenReturn(200);
      String structured = "{\"text\":\"review report\",\"decision\":\"PASS\"}";
      String wire =
          agent.equals("CLAUDE")
              ? "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"PRIVATE_REASONING\"},{\"type\":\"text\",\"text\":"
                  + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(structured)
                  + "}]}"
              : "{\"output\":[{\"type\":\"reasoning\",\"summary\":[]},{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":"
                  + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(structured)
                  + "}]}]}";
      when(response.body())
          .thenReturn(new ByteArrayInputStream(wire.getBytes(StandardCharsets.UTF_8)));
      when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(response);
      ReflectionTestUtils.setField(provider, "http", client);
      var reply = provider.complete(agent, "only task-scoped shared evidence");
      assertEquals("review report", reply.text());
      assertFalse(reply.toString().contains("PRIVATE_REASONING"));
      var request = ArgumentCaptor.forClass(HttpRequest.class);
      verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
      assertEquals(
          agent.equals("CLAUDE")
              ? "https://api.anthropic.com/v1/messages"
              : "https://api.openai.com/v1/responses",
          request.getValue().uri().toString());
      assertTrue(request.getValue().headers().firstValue("Cookie").isEmpty());
      var bytes = new ByteArrayOutputStream();
      var done = new CompletableFuture<Void>();
      request
          .getValue()
          .bodyPublisher()
          .orElseThrow()
          .subscribe(
              new java.util.concurrent.Flow.Subscriber<ByteBuffer>() {
                public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
                  s.request(Long.MAX_VALUE);
                }

                public void onNext(ByteBuffer b) {
                  byte[] v = new byte[b.remaining()];
                  b.get(v);
                  bytes.writeBytes(v);
                }

                public void onError(Throwable t) {
                  done.completeExceptionally(t);
                }

                public void onComplete() {
                  done.complete(null);
                }
              });
      done.get(5, TimeUnit.SECONDS);
      String payload = bytes.toString(StandardCharsets.UTF_8);
      assertFalse(payload.contains("SECRET_CANARY"));
      assertFalse(payload.contains("\"tools\""));
      assertFalse(payload.contains("previous_response_id"));
      if (!agent.equals("CLAUDE")) assertTrue(payload.contains("\"store\":false"));
    }
  }
}
