package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import f2r.tutor_solution.private_tutor_solution_back.config.NewStartLlmProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * NewStart 지피지기 전용 LLM 클라이언트. primary는 {@code app.newstart.llm.primary}로 고른다:
 * {@code wiki-router}(기본: 기존 내신Wiki AI Router=Qwen, 2026-10-01 결정),
 * {@code meta}(Meta Muse Spark DIRECT, 2026-10-05 추가),
 * {@code nvidia}(NVIDIA NIM Nemotron, 보존).
 * fallback은 OpenRouter Qwen DIRECT(앱 자신의 OPENROUTER_API_KEY가 있을 때만 동작).
 *
 * <p>호출 횟수 계약: 정상 경로에서는 primary 1회뿐이고(Qwen 0회), primary가 §4의 기술적
 * 실패(연결 실패·timeout·429·5xx·응답 해석 실패·빈 content·interrupted)로 떨어질 때만
 * **같은 system prompt·같은 질문·같은 근거 컨텍스트**로 fallback 1회를 탄다. fallback까지
 * 실패하면 추가 재시도 없이 통제된 오류를 돌려준다(loop 0).
 *
 * <p>retrieval은 여기서 하지 않는다 — 근거 조립은 호출 전 {@code JipijigiResearchService}가
 * 1회만 수행하고, fallback 과정에서 재검색하지 않는다.
 */
@Component
public class NewStartLlmClient {

  private static final Logger log = LoggerFactory.getLogger(NewStartLlmClient.class);

  private final NewStartLlmTransport primary;
  private final NewStartLlmTransport fallback;

  /** 테스트·수동 조립용. */
  public NewStartLlmClient(NewStartLlmTransport primary, NewStartLlmTransport fallback) {
    this.primary = primary;
    this.fallback = fallback;
  }

  /** Spring 조립: primary는 설정으로 선택하고 선택되지 않은 transport는 보존된다. */
  @Autowired
  public NewStartLlmClient(
      NewStartLlmProperties properties,
      WikiRouterLlmTransport wikiRouter,
      NvidiaNimLlmTransport nvidia,
      MetaMuseSparkLlmTransport metaMuseSpark,
      OpenRouterQwenLlmTransport openRouter) {
    this(selectPrimary(properties == null ? null : properties.getPrimary(), wikiRouter, nvidia, metaMuseSpark), openRouter);
    log.info("newstart llm primary={} fallback={}", this.primary.name(), openRouter.name());
  }

  static NewStartLlmTransport selectPrimary(String configured, NewStartLlmTransport wikiRouter,
      NewStartLlmTransport nvidia, NewStartLlmTransport metaMuseSpark) {
    String value = configured == null ? "" : configured.trim();
    if ("meta".equalsIgnoreCase(value)) {
      return metaMuseSpark;
    }
    return "nvidia".equalsIgnoreCase(value) ? nvidia : wikiRouter;
  }

  public NewStartLlmCompletion complete(String systemPrompt, String prompt, int maxOutputChars) {
    long tPrimary = System.nanoTime();
    NewStartLlmCompletion first = primary.complete(systemPrompt, prompt, maxOutputChars);
    if (first.ok()) {
      log.info("newstart llm attempts=1 fallbackUsed=false primaryMs={} model={}",
          ms(System.nanoTime() - tPrimary), first.model());
      return cap(first, maxOutputChars);
    }
    if (!first.fallbackEligible()) {
      log.info("newstart llm attempts=1 fallbackUsed=false nonRetryablePrimaryFailure primaryMs={}",
          ms(System.nanoTime() - tPrimary));
      return first;
    }
    long tFallback = System.nanoTime();
    NewStartLlmCompletion second = fallback.complete(systemPrompt, prompt, maxOutputChars);
    log.info("newstart llm attempts=2 fallbackUsed=true primaryMs={} fallbackMs={} fallbackOk={} model={}",
        ms(tFallback - tPrimary), ms(System.nanoTime() - tFallback), second.ok(), second.model());
    if (second.ok()) {
      return cap(second, maxOutputChars);
    }
    return second;
  }

  /** 답변 출력 상한. 기존 Router의 max_output_chars가 하던 역할(과대 출력 차단)을 여기서 한다. */
  private static NewStartLlmCompletion cap(NewStartLlmCompletion completion, int maxOutputChars) {
    if (maxOutputChars <= 0 || completion.text().length() <= maxOutputChars) {
      return completion;
    }
    return NewStartLlmCompletion.success(
        completion.text().substring(0, maxOutputChars), completion.provider(), completion.model());
  }

  private static long ms(long nanos) {
    return Math.round(nanos / 1_000_000.0);
  }
}
