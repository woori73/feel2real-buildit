package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiIdentitySigner;
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
 * Academy Builder "AI로 다듬기"가 새 API key나 별도 AI Router 없이, 기존 BuildIt Wiki AI
 * Router(problem-generator/wiki-backend-src/backend/ai_router.py, provider=auto)를 그대로
 * 재사용하도록 연결하는 어댑터. Gemini/OpenRouter 키는 Wiki 백엔드(Python) 프로세스에서만
 * 관리되며 여기서는 절대 다루지 않는다 — 이 클래스가 하는 일은 로그인된 principal 기준으로
 * BuildItWikiProxyController와 동일한 X-BuildIt-* 신원 서명을 붙여 서버-서버로
 * {@code POST /api/ai/complete}를 호출하는 것뿐이다(브라우저 프록시를 거치지 않는다 — Academy의
 * 흐름은 이미 서버 사이드인 {@link AcademyAiSuggestionService}에서 시작되기 때문).
 */
@Component
public class WikiRouterAcademyAiProvider implements AiProvider {
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
  private static final String DEFAULT_PROVIDER = "auto";
  private static final String SYSTEM_PROMPT =
      "너는 학원 홈페이지 문구를 다듬는 한국어 카피라이터다. 제공된 정보 밖의 사실을 새로 지어내지"
          + " 않고, 과장 없이 자연스럽고 신뢰가 가는 문장으로만 다듬는다.";

  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WikiIdentitySigner identitySigner;
  private final AuthenticatedUserResolver authenticatedUserResolver;
  private final String baseUrl;

  public WikiRouterAcademyAiProvider(
      WikiIdentitySigner identitySigner,
      AuthenticatedUserResolver authenticatedUserResolver,
      @Value("${app.wiki.ai-router.base-url:http://127.0.0.1:8000}") String baseUrl) {
    this.identitySigner = identitySigner;
    this.authenticatedUserResolver = authenticatedUserResolver;
    this.baseUrl = baseUrl;
  }

  @Override
  public AiCompletion complete(String prompt, AiTaskLimits limits) {
    JwtTokenProvider.AuthenticatedPrincipal principal = authenticatedUserResolver.currentPrincipal().orElse(null);
    if (principal == null) {
      return AiCompletion.failed("로그인이 필요합니다.");
    }
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("system", SYSTEM_PROMPT);
    requestBody.put("prompt", prompt);
    requestBody.put("provider", DEFAULT_PROVIDER);
    requestBody.put("max_output_chars", limits == null ? 0 : limits.maxOutputChars());
    try {
      byte[] json = objectMapper.writeValueAsBytes(requestBody);
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/api/ai/complete"))
          .timeout(REQUEST_TIMEOUT)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofByteArray(json));
      identitySigner.sign(builder, principal);
      HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        return AiCompletion.failed(errorMessage(response));
      }
      JsonNode node = objectMapper.readTree(response.body());
      String text = node.path("text").asText("");
      if (text.isBlank()) {
        return AiCompletion.failed("AI가 빈 응답을 반환했습니다.");
      }
      return AiCompletion.ok(text);
    } catch (IOException exception) {
      return AiCompletion.failed("AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return AiCompletion.failed("AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }
  }

  @Override
  public boolean isReady() {
    // Gemini/OpenRouter 키 보유 여부는 Wiki 백엔드(Python) 프로세스만 안다 — Java 쪽에는
    // 절대 노출하지 않는다는 요구사항 때문에 여기서 미리 알 수 없다. 실제 준비 상태는
    // complete() 호출 시 성공/실패로만 드러난다.
    return true;
  }

  @Override
  public String name() {
    return "wiki-router";
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
    return "AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.";
  }
}
