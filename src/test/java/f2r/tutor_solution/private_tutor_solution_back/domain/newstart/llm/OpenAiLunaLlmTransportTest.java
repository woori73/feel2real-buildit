package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OpenAiLunaLlmTransportTest {
  private static final String SUCCESS = """
      {"status":"completed","output":[
        {"type":"reasoning","summary":[{"text":"private reasoning"}]},
        {"type":"message","role":"assistant","content":[
          {"type":"output_text","text":"실제 사례 답변"}]}],
        "usage":{"input_tokens":100,"output_tokens":20}}
      """;

  @Test
  void givenCompletedResponse_whenParsed_thenOnlyFinalAnswerIsReturned() {
    var transport = new OpenAiLunaLlmTransport("");
    var result = transport.parse(SUCCESS, 4000);
    assertTrue(result.ok());
    assertEquals("실제 사례 답변", result.text());
    assertEquals("gpt-6-luna", result.model());
    assertFalse(result.text().contains("private reasoning"));
  }

  @Test
  void givenIncompleteOrInvalidResponse_whenParsed_thenControlledFailure() {
    var transport = new OpenAiLunaLlmTransport("");
    for (String body : new String[]{"{}", "not json", "null",
        "{\"status\":\"incomplete\",\"output\":[]}",
        "{\"status\":\"completed\",\"output\":[]}"}) {
      var result = transport.parse(body, 4000);
      assertFalse(result.ok());
      assertTrue(result.fallbackEligible());
    }
  }

  @Test
  void givenMissingKey_whenCalled_thenNoNetworkAndNoFallback() {
    var result = new OpenAiLunaLlmTransport("").complete("system", "question", 4000);
    assertFalse(result.ok());
    assertFalse(result.fallbackEligible());
  }

  @Test
  void givenResponsesEndpoint_whenCalled_thenPreservesPromptAndBoundsUsage() throws Exception {
    var requests = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/responses", exchange -> {
      requests.incrementAndGet();
      var body = new ObjectMapper().readTree(exchange.getRequestBody());
      assertEquals("Bearer test-only-key", exchange.getRequestHeaders().getFirst("Authorization"));
      assertEquals("gpt-6-luna", body.path("model").asText());
      assertEquals("사례 우선 원칙", body.path("instructions").asText());
      assertEquals("전자책 근거와 질문", body.path("input").asText());
      assertFalse(body.path("store").asBoolean(true));
      assertEquals(4096, body.path("max_output_tokens").asInt());
      byte[] response = SUCCESS.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      var transport = new OpenAiLunaLlmTransport("test-only-key", HttpClient.newHttpClient(),
          URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses"));
      var result = transport.complete("사례 우선 원칙", "전자책 근거와 질문", 4000);
      assertTrue(result.ok());
      assertEquals(1, requests.get());
    } finally { server.stop(0); }
  }
  @Test
  void givenImageInput_whenCalled_thenResponsesImagePartsArePreserved() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/responses", exchange -> {
      var body = new ObjectMapper().readTree(exchange.getRequestBody());
      var content = body.path("input").path(0).path("content");
      assertEquals("input_text", content.path(0).path("type").asText());
      assertEquals("input_image", content.path(1).path("type").asText());
      assertEquals("data:image/png;base64,test", content.path(1).path("image_url").asText());
      assertEquals(16384, body.path("max_output_tokens").asInt());
      byte[] response = SUCCESS.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      var transport = new OpenAiLunaLlmTransport("test-only-key", HttpClient.newHttpClient(),
          URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses"));
      assertTrue(transport.completeWithImage("policy", "read", "data:image/png;base64,test", 24000).ok());
    } finally { server.stop(0); }
  }
}
