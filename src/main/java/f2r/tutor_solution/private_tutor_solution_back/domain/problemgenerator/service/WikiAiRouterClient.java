package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * BuildIt Wiki AI Router(problem-generator/wiki-backend-src/backend/ai_router.py, provider=auto)를
 * Java 서버에서 서버-서버로 호출하는 공용 client. 기존 {@link WikiRouterAcademyAiProvider}가
 * 혼자 들고 있던 호출 로직을 Academy(AiProvider 어댑터)와 NewStart 질문(일반 Q&A)이 함께
 * 쓰도록 추출했다 — provider/모델 정책은 Wiki Router 단일 출처만 따른다.
 *
 * <p>Gemini/OpenRouter 키는 Wiki 백엔드(Python) 프로세스에서만 관리되며 여기서 절대 다루지
 * 않는다. 이 client가 하는 일은 로그인된 principal 기준으로 {@link WikiIdentitySigner}와 동일한
 * X-BuildIt-* 신원 서명을 붙여 {@code POST /api/ai/complete}를 호출하는 것뿐이다.
 * 브라우저에서 provider에 직접 호출하는 경로는 존재하지 않는다.
 *
 * <p>실패는 예외 대신 result-object({@link Completion#ok()})로 보고한다. provider 장애가
 * 호출부 제어 흐름을 깨지 않게 하기 위해서다({@code ChartGeminiClient.GroundedResponse}와
 * {@code AiProvider.AiCompletion}이 따르는 기존 패턴).
 */
@Component
public class WikiAiRouterClient {
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
  private static final String DEFAULT_PROVIDER = "auto";
  private static final String GENERIC_ERROR_MESSAGE = "AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.";

  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WikiIdentitySigner identitySigner;
  private final AuthenticatedUserResolver authenticatedUserResolver;
  private final String baseUrl;

  public WikiAiRouterClient(
      WikiIdentitySigner identitySigner,
      AuthenticatedUserResolver authenticatedUserResolver,
      @Value("${app.wiki.ai-router.base-url:http://127.0.0.1:8000}") String baseUrl) {
    this.identitySigner = identitySigner;
    this.authenticatedUserResolver = authenticatedUserResolver;
    this.baseUrl = baseUrl;
  }

  /** 완성 요청. {@code systemPrompt}가 null/빈이면 Wiki Router 기본 동작(빈 system)으로 위임한다. provider는 항상 auto. */
  public Completion complete(String systemPrompt, String prompt, int maxOutputChars) {
    return complete(systemPrompt, prompt, maxOutputChars, null);
  }

  /**
   * provider를 명시한 완성 요청 — Qwen(auto) vs NVIDIA Lightning(nvidia)처럼 동일 프롬프트/근거를
   * 다른 provider로 비교(A/B)하기 위한 통로. null/빈값이면 기존과 동일한 auto 정책이다.
   * provider/모델의 실제 해석은 Wiki Router 단일 출처(Python {@code ai_router.generate()})이 한다.
   */
  public Completion complete(String systemPrompt, String prompt, int maxOutputChars, String provider) {
    JwtTokenProvider.AuthenticatedPrincipal principal = authenticatedUserResolver.currentPrincipal().orElse(null);
    if (principal == null) {
      return Completion.failed("로그인이 필요합니다.");
    }
    Map<String, Object> requestBody = new LinkedHashMap<>();
    if (systemPrompt != null && !systemPrompt.isBlank()) {
      requestBody.put("system", systemPrompt);
    }
    requestBody.put("prompt", prompt);
    requestBody.put("provider", provider == null || provider.isBlank() ? DEFAULT_PROVIDER : provider);
    requestBody.put("max_output_chars", maxOutputChars);
    try {
      byte[] json = objectMapper.writeValueAsBytes(requestBody);
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/api/ai/complete"))
          .timeout(REQUEST_TIMEOUT)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofByteArray(json));
      identitySigner.sign(builder, principal);
      HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        return Completion.failed(errorMessage(response));
      }
      JsonNode node = objectMapper.readTree(response.body());
      String text = node.path("text").asText("");
      if (text.isBlank()) {
        return Completion.failed("AI가 빈 응답을 반환했습니다.");
      }
      return new Completion(true, text, null, node.path("provider").asText(null), node.path("model").asText(null));
    } catch (IOException exception) {
      return Completion.failed(GENERIC_ERROR_MESSAGE);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return Completion.failed(GENERIC_ERROR_MESSAGE);
    }
  }

  private String errorMessage(HttpResponse<byte[]> response) {
    try {
      JsonNode node = objectMapper.readTree(response.body());
      JsonNode detail = node.path("detail");
      if (detail.isTextual()) return detail.asText();
      if (detail.has("message")) return detail.path("message").asText();
    } catch (IOException ignored) {
      // 아래 기본 메시지로 대체
    }
    return GENERIC_ERROR_MESSAGE;
  }

  /**
   * 호출 결과. 성공 시 {@code text}만 의미 있고, 실패 시 {@code text}는 항상 빈 문자열이며
   * {@code errorMessage}만 의미 있다. {@code provider}/{@code model}은 Wiki Router가 반환한
   * 식별자(로깅/검증용)로, 실패 시 null이다.
   */
  public record Completion(boolean ok, String text, String errorMessage, String provider, String model) {
    public static Completion failed(String errorMessage) {
      return new Completion(false, "", errorMessage, null, null);
    }
  }
}
