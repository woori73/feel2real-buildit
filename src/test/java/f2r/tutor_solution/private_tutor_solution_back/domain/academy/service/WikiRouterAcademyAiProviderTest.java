package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiIdentitySigner;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * WikiRouterAcademyAiProvider — Academy "AI로 다듬기"가 기존 BuildIt Wiki AI Router
 * (POST /api/ai/complete, provider=auto)를 서버-서버로 재사용하는 어댑터.
 *
 * <p>실제 Python 백엔드 대신 로컬 fake HTTP 서버(임의 포트)를 세워, 이 provider가 (1) 실제
 * BuildItWikiProxyController와 같은 X-BuildIt-* 신원 서명을 붙이는지, (2) provider=auto를
 * 보내는지, (3) 성공/실패/빈응답/네트워크단절 각각을 AiProvider 계약(예외 없이 AiCompletion만
 * 반환)대로 처리하는지 검증한다.
 */
@DisplayName("WikiRouterAcademyAiProvider — Wiki AI Router(provider=auto) 서버-서버 재사용")
class WikiRouterAcademyAiProviderTest {
  private static final String SECRET = "wiki-router-provider-test-secret-32-bytes-min";
  private static final JwtTokenProvider.AuthenticatedPrincipal PRINCIPAL =
      new JwtTokenProvider.AuthenticatedPrincipal(3L, "tutor-a", "TUTOR");

  private HttpServer fakeWikiBackend;

  @AfterEach
  void stopFakeWikiBackend() {
    if (fakeWikiBackend != null) fakeWikiBackend.stop(0);
  }

  private WikiRouterAcademyAiProvider providerWithPrincipal(JwtTokenProvider.AuthenticatedPrincipal principal, String baseUrl) {
    AuthenticatedUserResolver resolver = mock(AuthenticatedUserResolver.class);
    when(resolver.currentPrincipal()).thenReturn(Optional.ofNullable(principal));
    return new WikiRouterAcademyAiProvider(new WikiIdentitySigner(SECRET), resolver, baseUrl);
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
  @DisplayName("given 정상 응답 when complete 호출 then 실 서명 헤더 + provider=auto로 요청하고 text를 그대로 돌려준다")
  void complete_signsRequestAndReturnsText() throws Exception {
    AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    String baseUrl = startFakeWikiBackend(200, "{\"text\":\"다듬은 원장 소개문\",\"provider\":\"qwen\",\"model\":\"qwen/qwen3.7-flash\"}", captured);
    WikiRouterAcademyAiProvider provider = providerWithPrincipal(PRINCIPAL, baseUrl);

    AiCompletion result = provider.complete("원본 소개문", new AiTaskLimits(220));

    assertThat(result.ok()).isTrue();
    assertThat(result.text()).isEqualTo("다듬은 원장 소개문");
    assertThat(captured.get().path()).isEqualTo("/api/ai/complete");
    assertThat(captured.get().body()).contains("\"provider\":\"auto\"");
    assertThat(captured.get().body()).contains("\"prompt\":\"원본 소개문\"");
    assertThat(captured.get().userIdHeader()).isEqualTo("3");
    assertThat(captured.get().signatureHeader()).isNotBlank();
  }

  @Test
  @DisplayName("given 로그인 principal이 없음 when complete 호출 then 네트워크를 타지 않고 즉시 실패한다")
  void complete_withoutPrincipal_failsWithoutCallingBackend() throws Exception {
    AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    String baseUrl = startFakeWikiBackend(200, "{\"text\":\"x\"}", captured);
    WikiRouterAcademyAiProvider provider = providerWithPrincipal(null, baseUrl);

    AiCompletion result = provider.complete("프롬프트", new AiTaskLimits(220));

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
    WikiRouterAcademyAiProvider provider = providerWithPrincipal(PRINCIPAL, baseUrl);

    AiCompletion result = provider.complete("프롬프트", new AiTaskLimits(220));

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("AI 요청이 많아 잠시 사용할 수 없습니다.");
  }

  @Test
  @DisplayName("given 빈 text 응답 when complete 호출 then 빈 응답으로 실패 처리한다")
  void complete_rejectsBlankText() throws Exception {
    AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    String baseUrl = startFakeWikiBackend(200, "{\"text\":\"   \",\"provider\":\"qwen\",\"model\":\"m\"}", captured);
    WikiRouterAcademyAiProvider provider = providerWithPrincipal(PRINCIPAL, baseUrl);

    AiCompletion result = provider.complete("프롬프트", new AiTaskLimits(220));

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).contains("빈 응답");
  }

  @Test
  @DisplayName("given Wiki 백엔드에 연결할 수 없음 when complete 호출 then 예외 없이 실패로만 보고한다(현재 값 보존 전제)")
  void complete_handlesConnectionFailureGracefully() {
    // 아무 fake 서버도 띄우지 않은 임의 포트 — connection refused를 흉내낸다.
    WikiRouterAcademyAiProvider provider = providerWithPrincipal(PRINCIPAL, "http://127.0.0.1:1");

    AiCompletion result = provider.complete("프롬프트", new AiTaskLimits(220));

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).isNotBlank();
  }

  @Test
  @DisplayName("given provider when name/isReady 조회 then 원본 키를 절대 노출하지 않는 고정 값만 돌려준다")
  void nameAndReadyExposeNoSecret() {
    WikiRouterAcademyAiProvider provider = providerWithPrincipal(PRINCIPAL, "http://127.0.0.1:1");
    assertThat(provider.name()).isEqualTo("wiki-router");
    assertThat(provider.isReady()).isTrue();
  }

  private record CapturedRequest(String path, String body, String userIdHeader, String signatureHeader) {}
}
