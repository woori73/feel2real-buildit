package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CodiClaudeTransportTest {
  @Test void givenDisabledClaude_whenChatting_thenNoDemoAnswerIsInvented() {
    var service = new ClaudeChatService(false,null,null,null,null,null);
    assertThatThrownBy(() -> service.codiChat(List.of(), "질문", true))
        .isInstanceOf(f2r.tutor_solution.private_tutor_solution_back.config.DemoFeatureDisabledException.class);
  }

  @Test void givenPublicQuestion_whenUsingClaude_thenOnlyPublicTurnsAndSearchToolAreSent() throws Exception {
    var mapper = new ObjectMapper();
    var requestBody = new AtomicReference<JsonNode>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/v1/messages", exchange -> {
      requestBody.set(mapper.readTree(exchange.getRequestBody()));
      byte[] bytes = """
          {"id":"msg_local","type":"message","role":"assistant","model":"claude-sonnet-4-6",
           "content":[{"type":"text","text":"공개 자료 답변","citations":[
              {"type":"web_search_result_location","url":"https://example.org/source","title":"공개 자료","encrypted_index":"test","cited_text":"예시"},
              {"type":"web_search_result_location","url":"javascript:alert(1)","title":"무효","encrypted_index":"test","cited_text":"예시"}]}],
           "stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":1}}
          """.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type","application/json");
      exchange.sendResponseHeaders(200,bytes.length);
      try (var output=exchange.getResponseBody()) {output.write(bytes);}
    });
    server.start();
    var client = AnthropicOkHttpClient.builder().apiKey("local-test-key")
        .baseUrl("http://127.0.0.1:"+server.getAddress().getPort()).build();
    try {
      // No repositories or file storage are supplied on purpose: this path cannot load F2R records.
      var service = new ClaudeChatService(client,null,null,null,null,null,"claude-sonnet-4-6");
      var reply = service.codiChat(List.of(new ClaudeChatService.PublicExchange("수업 방법","짝 활동")),"예시를 알려줘",true);
      JsonNode request = requestBody.get();
      assertThat(request.path("messages").size()).isEqualTo(3);
      assertThat(request.path("messages").get(0).path("content").asText()).isEqualTo("수업 방법");
      assertThat(request.path("messages").get(2).path("content").asText()).isEqualTo("예시를 알려줘");
      assertThat(request.path("tools").size()).isEqualTo(1);
      assertThat(request.path("tools").get(0).path("type").asText()).isEqualTo("web_search_20250305");
      assertThat(request.path("tools").get(0).path("max_uses").asInt()).isEqualTo(3);
      assertThat(reply.text()).isEqualTo("공개 자료 답변");
      assertThat(reply.sources()).containsExactly(new ClaudeChatService.SourceLink("공개 자료","https://example.org/source"));
      service.codiChat(List.of(),"검색 없이 설명해 줘",false);
      assertThat(requestBody.get().has("tools")).isFalse();
    } finally { client.close(); server.stop(0); }
  }
}
