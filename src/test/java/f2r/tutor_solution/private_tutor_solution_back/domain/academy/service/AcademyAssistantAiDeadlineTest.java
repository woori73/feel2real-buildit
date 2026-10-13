package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import f2r.tutor_solution.private_tutor_solution_back.config.NewStartLlmProperties;
import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantEditService.Interpretation;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Snapshot;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiAiRouterClient;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiIdentitySigner;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 서버의 AI 응답 한도와 폴백(Muse Spark → Wiki Router → Qwen)을 격리 환경(가짜 HTTP 서버)에서 실제 전송 계층으로 검증한다.
 * 실제 Muse Spark 전송, 실제 Wiki Router 클라이언트, 실제 신원 서명을 쓰고 외부 호출은 없다.
 */
@DisplayName("AcademyAssistantEditService — AI 응답 한도·취소·폴백 (격리 전송 계층)")
class AcademyAssistantAiDeadlineTest {

  private static final String GOOD_JSON = "{\"reply\":\"다크 분위기로 바꿨어요.\",\"question\":null,\"ops\":[{\"op\":\"setPreset\",\"value\":\"DARK\"}]}";

  private HttpServer fakeMeta;
  private HttpServer fakeWiki;
  private final AtomicInteger metaHits = new AtomicInteger();
  private final AtomicInteger wikiHits = new AtomicInteger();
  private final AtomicReference<String> wikiLoginHeader = new AtomicReference<>();

  @AfterEach
  void stop() {
    if (fakeMeta != null) fakeMeta.stop(0);
    if (fakeWiki != null) fakeWiki.stop(0);
    SecurityContextHolder.clearContext();
  }

  private static AcademyResponse academy() {
    return new AcademyResponse(1L, "h", "slug", "새봄영어학원", "소개", AcademyStatus.DRAFT, true, null, null,
        "/academies/slug", List.of(new AcademyResponse.SubjectSummary(1, "영어", "English")), Set.of(), List.of(), List.of(),
        List.of(), new AcademyPageSettings(null, null, null, Map.of()));
  }

  private static Snapshot snap() {
    return new Snapshot("소개 문단", Map.of("designPreset", "EXAM", "introHeading", "기존 제목"), Map.of("sec-videos", false), null);
  }

  /** statusOrHangMs: 200/500 등은 즉시 그 상태로, 음수면 그만큼(ms) 응답하지 않는다. */
  private String startMeta(int status, long hangMs) throws IOException {
    fakeMeta = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fakeMeta.createContext("/chat/completions", exchange -> {
      metaHits.incrementAndGet();
      exchange.getRequestBody().readAllBytes();
      if (hangMs > 0) {
        try {
          Thread.sleep(hangMs);
        } catch (InterruptedException ignored) {
          // 서버 종료
        }
        exchange.close();
        return;
      }
      reply(exchange, status, "{\"error\":\"stub\"}");
    });
    fakeMeta.start();
    return "http://127.0.0.1:" + fakeMeta.getAddress().getPort();
  }

  private String startWiki() throws IOException {
    fakeWiki = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fakeWiki.createContext("/api/ai/complete", exchange -> {
      wikiHits.incrementAndGet();
      wikiLoginHeader.set(exchange.getRequestHeaders().getFirst("X-BuildIt-Login-Id"));
      exchange.getRequestBody().readAllBytes();
      String body = "{\"text\":" + new ObjectMapper().valueToTree(GOOD_JSON) + ",\"provider\":\"qwen-stub\",\"model\":\"qwen-stub-1\"}";
      reply(exchange, 200, body);
    });
    fakeWiki.start();
    return "http://127.0.0.1:" + fakeWiki.getAddress().getPort();
  }

  private static void reply(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  private AcademyAssistantEditService service(String metaUrl, String wikiUrl, long limitSeconds) {
    NewStartLlmProperties props = new NewStartLlmProperties();
    props.getMeta().setBaseUrl(metaUrl);
    props.getMeta().setApiKey("never-printed-test-key");
    // 요청 스레드의 로그인 주체를 SecurityContext에서 읽는 실제 동작을 그대로 쓴다(스레드가 바뀌면 비어 있게 됨).
    AuthenticatedUserResolver resolver = mock(AuthenticatedUserResolver.class);
    when(resolver.currentPrincipal()).thenAnswer(inv -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .map(a -> a.getPrincipal())
        .filter(JwtTokenProvider.AuthenticatedPrincipal.class::isInstance)
        .map(JwtTokenProvider.AuthenticatedPrincipal.class::cast));
    WikiAiRouterClient wiki = new WikiAiRouterClient(
        new WikiIdentitySigner("0123456789012345678901234567890123456789"), resolver, wikiUrl);
    MuseSparkAcademyAiProvider provider = new MuseSparkAcademyAiProvider(
        new MetaMuseSparkLlmTransport(props), new WikiRouterAcademyAiProvider(wiki));
    return new AcademyAssistantEditService(
        null, null, null, provider, new ObjectMapper(), mock(PlatformTransactionManager.class), limitSeconds);
  }

  private static void login() {
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new JwtTokenProvider.AuthenticatedPrincipal(7L, "deadline_tutor", "TUTOR"), null, List.of()));
  }

  @Test
  @DisplayName("given Muse Spark가 6초 무응답·한도 1초 when 해석하면 then 한도에서 끝나고(TIMEOUT) 취소된 스레드는 폴백 체인을 새로 호출하지 않는다")
  void timeoutCancelsAndDoesNotStartFallback() throws Exception {
    // given
    String meta = startMeta(200, 6000);
    String wiki = startWiki();
    AcademyAssistantEditService svc = service(meta, wiki, 1L);
    login();

    // when
    long t0 = System.nanoTime();
    Interpretation i = svc.interpret("분위기를 오로라처럼 몽환적으로 만들어줘", academy(), snap());
    long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
    Thread.sleep(2500); // 취소된 스레드가 뒤늦게 폴백을 시도할 시간을 준다

    // then
    assertThat(elapsedMs).isLessThan(3000);
    assertThat(i.aiTimedOut()).isTrue();
    assertThat(i.engine()).isEqualTo("TIMEOUT");
    assertThat(i.ops()).isEmpty();
    assertThat(metaHits.get()).isEqualTo(1);
    assertThat(wikiHits.get()).as("취소 뒤 폴백(Qwen 체인) 호출 0회").isZero();
  }

  @Test
  @DisplayName("given Muse Spark가 500으로 실패 when 해석하면 then Wiki Router(Qwen) 폴백이 로그인 주체로 정확히 1회 불리고 그 답을 쓴다")
  void fallbackToQwenWorksWithPrincipalPropagated() throws Exception {
    // given
    String meta = startMeta(500, 0);
    String wiki = startWiki();
    AcademyAssistantEditService svc = service(meta, wiki, 5L);
    login();

    // when
    Interpretation i = svc.interpret("더 고급스럽게 바꿔 줘", academy(), snap());

    // then — AI 스레드에서도 인증 정보가 전달돼 폴백이 성공한다(전달이 안 되면 "로그인이 필요합니다"로 실패해 규칙으로 떨어진다)
    assertThat(i.aiTimedOut()).isFalse();
    assertThat(i.aiFailed()).isFalse();
    assertThat(i.parseOk()).isTrue();
    assertThat(i.ops()).hasSize(1);
    assertThat(metaHits.get()).isEqualTo(1);
    assertThat(wikiHits.get()).isEqualTo(1);
    assertThat(wikiLoginHeader.get()).isEqualTo("deadline_tutor");
  }

  @Test
  @DisplayName("given 이미 취소(인터럽트)된 스레드 when Muse Spark 전송을 호출하면 then 서버를 6초 기다리지 않고 즉시 실패로 끝난다")
  void interruptedThreadFailsFast() throws Exception {
    // given
    String meta = startMeta(200, 6000);
    NewStartLlmProperties props = new NewStartLlmProperties();
    props.getMeta().setBaseUrl(meta);
    props.getMeta().setApiKey("never-printed-test-key");
    MetaMuseSparkLlmTransport transport = new MetaMuseSparkLlmTransport(props);

    // when
    Thread.currentThread().interrupt();
    long t0 = System.nanoTime();
    var result = transport.complete("system", "prompt", 100);
    long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
    boolean stillInterrupted = Thread.interrupted(); // 다른 테스트에 플래그를 남기지 않는다

    // then
    assertThat(result.ok()).isFalse();
    assertThat(elapsedMs).isLessThan(1500);
    assertThat(stillInterrupted).isTrue();
  }

  @Test
  @DisplayName("given 취소(인터럽트)된 스레드·Muse Spark 실패 when 제공자를 호출하면 then 폴백(Wiki Router→Qwen) 제공자를 호출하지 않고 cancelled로 끝난다")
  void cancelledCallerNeverStartsFallbackProvider() throws Exception {
    // given
    String meta = startMeta(500, 0);
    NewStartLlmProperties props = new NewStartLlmProperties();
    props.getMeta().setBaseUrl(meta);
    props.getMeta().setApiKey("never-printed-test-key");
    WikiRouterAcademyAiProvider wikiProvider = mock(WikiRouterAcademyAiProvider.class);
    MuseSparkAcademyAiProvider provider = new MuseSparkAcademyAiProvider(new MetaMuseSparkLlmTransport(props), wikiProvider);

    // when
    Thread.currentThread().interrupt();
    AiProvider.AiCompletion result = provider.complete("프롬프트", new AiProvider.AiTaskLimits(3000));
    boolean stillInterrupted = Thread.interrupted();

    // then
    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("cancelled");
    assertThat(stillInterrupted).isTrue();
    verify(wikiProvider, never()).complete(any(), any());
  }
}
