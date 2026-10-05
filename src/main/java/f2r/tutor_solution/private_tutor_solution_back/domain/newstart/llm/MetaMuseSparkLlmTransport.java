package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.NewStartLlmProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Meta Muse Spark DIRECT 호출(OpenAI-compatible chat completions). NewStart 지피지기 primary.
 *
 * <p>요청 계약(model / messages / temperature)은 OpenAI 호환이며, 지피지기의 system prompt·question·
 * 근거 컨텍스트를 그대로 전달하므로 RAG·프롬프트 조립·출처 표시 로직은 변경되지 않는다.
 *
 * <p><b>Reasoning 특성 주의:</b> muse-spark-1.3-contributor는 reasoning 모델이라
 * {@code max_tokens}가 부족하면 reasoning 토큰에 소진되어 assistant {@code content}가
 * null로 반환된다(Vision 실측에서 확인). 지피지기 답변 상한(4000자)을 감안해
 * {@code max_tokens}는 8000 이상을 유지해야 한다.
 *
 * <p>assistant {@code content}만 최종 답변으로 쓴다. {@code reasoning_content}(내부 사고)는 읽지 않고
 * 폐기하며, 어떤 응답·로그·예외 메시지에도 실리지 않는다. API 키는 환경변수로만 주입한다.
 */
@Component
public class MetaMuseSparkLlmTransport implements NewStartLlmTransport {

  private static final Logger log = LoggerFactory.getLogger(MetaMuseSparkLlmTransport.class);

  private static final String GENERIC_ERROR = "AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.";

  private final NewStartLlmProperties.MetaMuseSpark config;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public MetaMuseSparkLlmTransport(NewStartLlmProperties properties) {
    this.config = properties.getMeta();
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
  }

  @Override
  public String name() {
    return "meta-muse-spark";
  }

  @Override
  public NewStartLlmCompletion complete(String systemPrompt, String prompt, int maxOutputChars) {
    if (config.getApiKey() == null || config.getApiKey().isBlank()) {
      // 키 미설정은 설정 상태이므로 fallback 대상이 아니다.
      return NewStartLlmCompletion.failed(GENERIC_ERROR, false);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", config.getModel());
    body.put("messages", List.of(
        Map.of("role", "system", "content", systemPrompt == null || systemPrompt.isBlank() ? " " : systemPrompt),
        Map.of("role", "user", "content", prompt == null || prompt.isBlank() ? " " : prompt)));
    body.put("temperature", config.getTemperature());
    body.put("max_tokens", config.getMaxTokens());
    long tStart = System.nanoTime();
    try {
      String payload = objectMapper.writeValueAsString(body);
      HttpRequest request = HttpRequest
          .newBuilder(URI.create(config.getBaseUrl() + "/chat/completions"))
          .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + config.getApiKey())
          .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
          .build();
      long tSend = System.nanoTime();
      HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      long tHttp = System.nanoTime();
      if (response.statusCode() != 200) {
        // §4: 429·5xx만 재시도 대상. 그 외 4xx(인증·요건 오류)는 fallback 없이 통제된 실패로 알린다.
        boolean eligible = response.statusCode() == 429 || response.statusCode() >= 500;
        log.info("newstart meta-muse-spark call status={} httpMs={} requestChars={} maxTokens={}",
            response.statusCode(), ms(tHttp - tSend), payload.length(), config.getMaxTokens());
        return NewStartLlmCompletion.failed(GENERIC_ERROR, eligible);
      }
      NewStartLlmCompletion completion = parse(response.body());
      log.info("newstart meta-muse-spark call status=200 httpMs={} parseMs={} ok={} model={} requestChars={}",
          ms(tHttp - tSend), ms(System.nanoTime() - tHttp), completion.ok(), completion.model(), payload.length());
      return completion;
    } catch (IOException exception) {
      log.info("newstart meta-muse-spark call ioFailure httpMs={} maxTokens={}",
          ms(System.nanoTime() - tStart), config.getMaxTokens());
      return NewStartLlmCompletion.failed(GENERIC_ERROR, true);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return NewStartLlmCompletion.failed(GENERIC_ERROR, true);
    }
  }

  private NewStartLlmCompletion parse(byte[] responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode choices = root.path("choices");
      if (!choices.isArray() || choices.isEmpty()) {
        return NewStartLlmCompletion.failed(GENERIC_ERROR, true);
      }
      JsonNode choice = choices.get(0);
      String finishReason = choice.path("finish_reason").asText("");
      if ("interrupted".equals(finishReason)) {
        return NewStartLlmCompletion.failed(GENERIC_ERROR, true);
      }
      // reasoning 모델은 max_tokens 부족 시 content가 null로 반환된다 — 기술적 실패로 처리한다.
      JsonNode contentNode = choice.path("message").path("content");
      String content = contentNode.isNull() ? "" : contentNode.asText("");
      if (content.isBlank()) {
        return NewStartLlmCompletion.failed(GENERIC_ERROR, true);
      }
      return NewStartLlmCompletion.success(content.strip(), name(),
          root.path("model").asText(config.getModel()));
    } catch (IOException exception) {
      return NewStartLlmCompletion.failed(GENERIC_ERROR, true);
    }
  }

  private static long ms(long nanos) {
    return Math.round(nanos / 1_000_000.0);
  }
}
