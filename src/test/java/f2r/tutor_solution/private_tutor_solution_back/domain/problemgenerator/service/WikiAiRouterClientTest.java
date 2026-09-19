package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * WikiAiRouterClient — BuildIt Wiki AI Router(POST /api/ai/complete, provider=auto) 서버-서버
 * 공용 client. 실제 Python 백엔드 대신 로컬 fake HTTP 서버(임의 포트)를 세워 (1) X-BuildIt-* 신원
 * 서명, (2) provider=auto, (3) 성공/실패/빈응답/네트워크단절의 result-object 계약을 검증한다.
 * (기존 WikiRouterAcademyAiProviderTest가 검증하던 내용을 추출된 client에 그대로 이식한다.)
 */
@DisplayName("WikiAiRouterClient — Wiki AI Router(provider=auto) 서버-서버 공용 client")
class WikiAiRouterClientTest {
  private static final String SECRET = "wiki-router-client-test-secret-32-bytes";
  private static final JwtTokenProvider.AuthenticatedPrincipal PRINCIPAL =
      new JwtTokenProvider.AuthenticatedPrincipal(3L, "tutor-a", "TUTOR");

  private HttpServer fakeWikiBackend;

  @AfterEach
  void stopFakeWikiBackend() {
    if (fakeWikiBackend != null) fakeWikiBackend.stop(0);
  }

  private WikiAiRouterClient clientWithPrincipal(JwtTokenProvider.AuthenticatedPrincipal principal, String baseUrl) {
    AuthenticatedUserResolver resolver = mock(AuthenticatedUserResolver.class);
    when(resolver.currentPrincipal()).thenReturn(Optional.ofNullable(principal));
    return new WikiAiRouterClient(new WikiIdentitySigner(SECRET), resolver, baseUrl);
  }

  private String startFakeWikiBackend(int status, String responseBody, AtomicReference<CapturedRequest> captured) throws IOException {
    fakeWikiBackend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fakeWikiBackend.createContext("/api/ai/complete", exchange -> {
      byte[] body = exchange.getRequestBody().readAllBytes();
      captured.set(new CapturedRequest(
          exchange.getRequestURI().getPath(),
          new String(body, StandardCharsets.UTF_8),
          exchange.getRequestHeaders().getFirst("X-BuildIt-User-Id"),
          exchange.getRequestHeaders().getFirst("X-BuildIt-Signature")));
      byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, response.length);
      try (var os = exchange.getResponseBody()) {
        os.write(response);
      }
    });
    fakeWikiBackend.start();
    return "http://127.0.0.1:" + fakeWikiBackend.getAddress().getPort();
  }

  @Test
  @DisplayName("given 정상 응답 when complete 호출 then 실 서명 헤더 + provider=auto로 요청하고 text·model을 돌려준다")
  void complete_signsRequestAndReturnsText() throws Exception {
    AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    String baseUrl = startFakeWikiBackend(200, "{\"text\":\"답변\",\"provider\":\"qwen\",\"model\":\"qwen/qwen3.7-flash\"}", captured);
    WikiAiRouterClient client = clientWithPrincipal(PRINCIPAL, baseUrl);

    WikiAiRouterClient.Completion result = client.complete("시스템", "질문", 220);

    assertThat(result.ok()).isTrue();
    assertThat(result.text()).isEqualTo("답변");
    assertThat(result.model()).isEqualTo("qwen/qwen3.7-flash");
    assertThat(captured.get().path()).isEqualTo("/api/ai/complete");
    assertThat(captured.get().body()).contains("\"provider\":\"auto\"");
    assertThat(captured.get().body()).contains("\"prompt\":\"질문\"");
    assertThat(captured.get().body()).contains("\"system\":\"시스템\"");
    assertThat(captured.get().userIdHeader()).isEqualTo("3");
    assertThat(captured.get().signatureHeader()).isNotBlank();
  }

  @Test
  @DisplayName("given 로그인 principal이 없음 when complete 호출 then 네트워크를 타지 않고 즉시 실패한다")
  void complete_withoutPrincipal_failsWithoutCallingBackend() throws Exception {
    AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    String baseUrl = startFakeWikiBackend(200, "{\"text\":\"x\"}", captured);
    WikiAiRouterClient client = clientWithPrincipal(null, baseUrl);

    WikiAiRouterClient.Completion result = client.complete(null, "프롬프트", 220);

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).contains("로그인");
    assertThat(captured.get()).isNull();
  }

  @Test
  @DisplayName("given Wiki 백엔드가 오류 상태코드를 반환 when complete 호출 then 실패로 변환하고 detail.message를 전달한다")
  void complete_translatesNonOkStatusToFailure() throws Exception {
    AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    String baseUrl = startFakeWikiBackend(502,
        "{\"detail\":{\"message\":\"AI 요청이 많아 잠시 사용할 수 없습니다.\",\"provider\":\"qwen\",\"status\":502}}", captured);
    WikiAiRouterClient client = clientWithPrincipal(PRINCIPAL, baseUrl);

    WikiAiRouterClient.Completion result = client.complete(null, "프롬프트", 220);

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("AI 요청이 많아 잠시 사용할 수 없습니다.");
  }

  @Test
  @DisplayName("given 빈 text 응답 when complete 호출 then 빈 응답으로 실패 처리한다")
  void complete_rejectsBlankText() throws Exception {
    AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    String baseUrl = startFakeWikiBackend(200, "{\"text\":\"   \",\"provider\":\"qwen\",\"model\":\"m\"}", captured);
    WikiAiRouterClient client = clientWithPrincipal(PRINCIPAL, baseUrl);

    WikiAiRouterClient.Completion result = client.complete(null, "프롬프트", 220);

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).contains("빈 응답");
  }

  @Test
  @DisplayName("given Wiki 백엔드에 연결할 수 없음 when complete 호출 then 예외 없이 실패로만 보고한다")
  void complete_handlesConnectionFailureGracefully() {
    WikiAiRouterClient client = clientWithPrincipal(PRINCIPAL, "http://127.0.0.1:1");

    WikiAiRouterClient.Completion result = client.complete(null, "프롬프트", 220);

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).isNotBlank();
  }

  private record CapturedRequest(String path, String body, String userIdHeader, String signatureHeader) {}
}
