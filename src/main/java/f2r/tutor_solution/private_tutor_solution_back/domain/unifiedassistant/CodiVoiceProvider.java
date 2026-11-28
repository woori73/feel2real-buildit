package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Component;

@Component
class CodiVoiceProvider {
  private final URI base;
  private final String key, agent, line;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  @Autowired
  CodiVoiceProvider(@Value("${codi.sip.api-key:}") String key, @Value("${codi.sip.agent-id:}") String agent,
                    @Value("${codi.sip.phone-number-id:}") String line) {
    this(URI.create("https://api.elevenlabs.io/v1/convai/"), key, agent, line);
  }
  CodiVoiceProvider(URI base, String key, String agent, String line) {
    this.base = base; this.key = key; this.agent = agent; this.line = line;
  }
  boolean configured() { return !key.isBlank() && !agent.isBlank() && !line.isBlank(); }

  record Start(String conversation, String status) {}
  Start start(String number, String task) throws IOException {
    var payload = Map.of("agent_id", agent, "agent_phone_number_id", line, "to_number", number,
        "conversation_initiation_client_data", Map.of("dynamic_variables", Map.of("call_task", task)));
    JsonNode response = exchange("sip-trunk/outbound-call", AssistantJson.write(payload));
    if (response != null && response.path("success").isBoolean() && !response.path("success").booleanValue()) return new Start(null, "failed");
    if (response == null) throw new IOException("Empty call response");
    String id = response.path("conversation_id").asText("");
    if (!response.path("success").asBoolean() || !id.matches("[A-Za-z0-9_-]{1,128}")) throw new IOException("Missing call acknowledgment");
    return new Start(id, "initiated");
  }

  CodiCalls.Result status(String id) throws IOException {
    JsonNode details = exchange("conversations/" + id, null);
    if (details == null || !id.equals(details.path("conversation_id").asText())) throw new IOException("Conversation mismatch");
    String status = details.path("status").asText("");
    String summary = "done".equals(status) ? details.path("analysis").path("transcript_summary").asText("") : "";
    return new CodiCalls.Result(status, summary);
  }

  private JsonNode exchange(String path, String body) throws IOException {
    var request = HttpRequest.newBuilder(base.resolve(path)).header("xi-api-key", key).timeout(Duration.ofSeconds(15));
    if (body != null) request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
    try {
      var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) throw new IOException("Voice provider HTTP " + response.statusCode());
      return AssistantJson.read(response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); throw new IOException("Voice request interrupted");
    }
  }
}
