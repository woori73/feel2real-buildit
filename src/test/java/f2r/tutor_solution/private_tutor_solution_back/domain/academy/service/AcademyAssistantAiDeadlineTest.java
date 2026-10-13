package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import f2r.tutor_solution.private_tutor_solution_back.config.NewStartLlmProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantEditService.Interpretation;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Snapshot;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 서버의 AI 응답 한도가 실제 Muse Spark 전송 계층(HttpClient)에서도 동작하는지 검증한다.
 * 가짜 Meta 서버가 6초 동안 응답하지 않는 상황에서 한도(1초)를 넘기면 해석이 즉시 규칙으로 넘어가고,
 * 진행 중이던 호출은 취소(인터럽트)되어 6초를 기다리지 않고 끝난다.
 */
@DisplayName("AcademyAssistantEditService — 실제 Muse Spark 전송 계층에서의 AI 응답 한도")
class AcademyAssistantAiDeadlineTest {

  private HttpServer fakeMeta;

  @AfterEach
  void stop() {
    if (fakeMeta != null) fakeMeta.stop(0);
  }

  private static AcademyResponse academy() {
    return new AcademyResponse(1L, "h", "slug", "새봄영어학원", "소개", AcademyStatus.DRAFT, true, null, null,
        "/academies/slug", List.of(new AcademyResponse.SubjectSummary(1, "영어", "English")), Set.of(), List.of(), List.of(),
        List.of(), new AcademyPageSettings(null, null, null, Map.of()));
  }

  private static Snapshot snap() {
    return new Snapshot("소개 문단", Map.of("designPreset", "EXAM", "introHeading", "기존 제목"), Map.of("sec-videos", false), null);
  }

  @Test
  @DisplayName("given 6초 동안 응답하지 않는 Muse Spark·한도 1초 when 해석하면 then 한도에서 규칙으로 넘어가고 진행 중이던 호출은 취소된다")
  void slowMuseSparkIsCutAtDeadlineAndCancelled() throws Exception {
    // given
    AtomicInteger hits = new AtomicInteger();
    fakeMeta = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fakeMeta.createContext("/chat/completions", exchange -> {
      hits.incrementAndGet();
      exchange.getRequestBody().readAllBytes();
      try {
        Thread.sleep(6000);
      } catch (InterruptedException ignored) {
        // 서버 종료
      }
      exchange.close();
    });
    fakeMeta.start();
    NewStartLlmProperties props = new NewStartLlmProperties();
    props.getMeta().setBaseUrl("http://127.0.0.1:" + fakeMeta.getAddress().getPort());
    props.getMeta().setApiKey("never-printed-test-key");
    WikiRouterAcademyAiProvider wikiRouter = mock(WikiRouterAcademyAiProvider.class);
    when(wikiRouter.complete(any(), any())).thenReturn(AiProvider.AiCompletion.failed("fallback should fail fast"));
    MuseSparkAcademyAiProvider provider = new MuseSparkAcademyAiProvider(new MetaMuseSparkLlmTransport(props), wikiRouter);
    AcademyAssistantEditService service = new AcademyAssistantEditService(
        null, null, null, provider, new ObjectMapper(), mock(PlatformTransactionManager.class), 1L);

    // when
    long t0 = System.nanoTime();
    Interpretation i = service.interpret("분위기를 오로라처럼 몽환적으로 만들어줘", academy(), snap());
    long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

    // then — 한도(1초)에서 끝나고, 호출은 취소되어 폴백 단계까지 즉시 흘러간다(6초를 기다리지 않는다)
    assertThat(elapsedMs).isLessThan(3000);
    assertThat(i.aiTimedOut()).isTrue();
    assertThat(i.aiFailed()).isTrue();
    assertThat(i.engine()).isEqualTo("RULE");
    assertThat(hits.get()).isEqualTo(1);
    verify(wikiRouter, timeout(2500)).complete(any(), any());
  }

  @Test
  @DisplayName("given 이미 취소(인터럽트)된 스레드 when Muse Spark 전송을 호출하면 then 서버를 6초 기다리지 않고 즉시 실패로 끝난다")
  void interruptedThreadFailsFast() throws Exception {
    // given — 폴백 체인이 취소된 스레드에서 이어서 호출될 때와 같은 상태
    fakeMeta = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fakeMeta.createContext("/chat/completions", exchange -> {
      exchange.getRequestBody().readAllBytes();
      try {
        Thread.sleep(6000);
      } catch (InterruptedException ignored) {
        // 서버 종료
      }
      exchange.close();
    });
    fakeMeta.start();
    NewStartLlmProperties props = new NewStartLlmProperties();
    props.getMeta().setBaseUrl("http://127.0.0.1:" + fakeMeta.getAddress().getPort());
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
}
