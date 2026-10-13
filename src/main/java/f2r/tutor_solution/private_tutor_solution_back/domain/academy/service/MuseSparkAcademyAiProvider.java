package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * BuildIt Academy용 {@link AiProvider} — Muse Spark를 1순위로 사용한다 (2026-10-12).
 *
 * <p><b>라우팅 정책</b>:
 * <ol>
 *   <li>1순위: Muse Spark ({@code muse-spark-1.3-contributor}) —
 *       NewStart에서 검증된 {@link MetaMuseSparkLlmTransport}를 재사용한다.
 *       연결 방식·인증 설정은 NewStart와 동일하며, NewStart 코드는 수정하지 않는다.</li>
 *   <li>폴백: {@link WikiRouterAcademyAiProvider} —
 *       기존 Qwen 체인 ({@code qwen/qwen3.7-flash} → {@code ox-alpha} → {@code openrouter/free}).
 *       Muse Spark의 기술적 실패({@code fallbackEligible=true}) 시에만 탄다.</li>
 * </ol>
 *
 * <p><b>영향 범위</b>: {@code domain/academy}의 {@code AiProvider} 주입처만
 * 이 빈을 사용한다 ({@code @Primary}). Wiki AI Router를 직접 쓰는
 * 문제생성기({@code WikiAiRouterClient})와 NewStart는 영향받지 않는다.
 *
 * <p><b>로깅</b>: 호출 성공·실패·타임아웃·폴백 여부를 기록한다.
 * API 키·프롬프트 내용·개인정보는 로그에 남기지 않는다.
 */
@Component
@Primary
public class MuseSparkAcademyAiProvider implements AiProvider {

  private static final Logger log =
      LoggerFactory.getLogger(MuseSparkAcademyAiProvider.class);

  // WikiRouterAcademyAiProvider와 동일한 카피라이터 프롬프트 (일관성 유지)
  private static final String SYSTEM_PROMPT =
      "너는 학원 홈페이지 문구를 다듬는 한국어 카피라이터다. 제공된 정보 밖의 사실을 새로 지어내지"
          + " 않고, 과장 없이 자연스럽고 신뢰가 가는 문장으로만 다듬는다.";

  private final MetaMuseSparkLlmTransport museSparkTransport;
  private final WikiRouterAcademyAiProvider wikiRouterProvider;

  public MuseSparkAcademyAiProvider(
      MetaMuseSparkLlmTransport museSparkTransport,
      WikiRouterAcademyAiProvider wikiRouterProvider) {
    this.museSparkTransport = museSparkTransport;
    this.wikiRouterProvider = wikiRouterProvider;
  }

  @Override
  public AiCompletion complete(String prompt, AiTaskLimits limits) {
    int maxOutputChars = limits == null ? 0 : limits.maxOutputChars();
    long started = System.nanoTime();

    // 1순위: Muse Spark
    NewStartLlmCompletion sparkResult =
        museSparkTransport.complete(SYSTEM_PROMPT, prompt, maxOutputChars);
    long sparkMs = (System.nanoTime() - started) / 1_000_000;

    if (sparkResult.ok()) {
      log.info("buildit ai muse-spark ok model={} ms={} promptChars={}",
          sparkResult.model(), sparkMs,
          prompt == null ? 0 : prompt.length());
      return AiCompletion.ok(sparkResult.text());
    }

    // Muse Spark 실패 — 기술적 실패일 때만 폴백
    log.warn("buildit ai muse-spark failed fallbackEligible={} ms={} error={}",
        sparkResult.fallbackEligible(), sparkMs,
        truncate(sparkResult.errorMessage(), 200));

    if (!sparkResult.fallbackEligible()) {
      // 설정 오류 등 폴백 대상이 아님 — 그대로 실패 반환
      return AiCompletion.failed(sparkResult.errorMessage());
    }

    if (Thread.currentThread().isInterrupted()) {
      // 호출부가 취소했다(서버 응답 한도 초과 등). 아무도 기다리지 않는 폴백(Wiki Router → Qwen 체인)을 새로 시작하면
      // 의미 없는 호출과 비용만 늘므로 건너뛴다.
      log.info("buildit ai fallback skipped (caller cancelled) museSparkMs={}", sparkMs);
      return AiCompletion.failed("cancelled");
    }

    // 폴백: 기존 Qwen 체인
    long fallbackStarted = System.nanoTime();
    AiCompletion fallbackResult = wikiRouterProvider.complete(prompt, limits);
    long fallbackMs = (System.nanoTime() - fallbackStarted) / 1_000_000;

    log.info("buildit ai fallback provider={} ok={} ms={}",
        wikiRouterProvider.name(), fallbackResult.ok(), fallbackMs);
    return fallbackResult;
  }

  @Override
  public boolean isReady() {
    // 실제 준비 상태는 complete() 호출 시 드러난다 (WikiRouter와 동일한 정책).
    return true;
  }

  @Override
  public String name() {
    return "muse-spark";
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return "";
    }
    return s.length() <= max ? s : s.substring(0, max) + "...";
  }
}
