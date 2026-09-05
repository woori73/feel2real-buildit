package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartGeminiClient;
import org.springframework.stereotype.Component;

/**
 * {@link AiProvider}를 이미 있는 {@link ChartGeminiClient}(Gemini/OpenAI 겸용, BuildIt Chart
 * 기능이 먼저 만들어 둔 서버사이드 클라이언트)로 구현한다.
 *
 * <p>새 provider를 만들지 않는다 — API key(GEMINI_API_KEY/OPENAI_API_KEY)도, HTTP client도,
 * 에러 처리도 전부 기존 것을 그대로 재사용한다. 이 어댑터가 하는 일은 딱 하나, "grounding(웹
 * 검색) 기능이 포함된 응답 모델"을 {@link AiProvider}의 provider-neutral 결과 모델로 좁히는
 * 것뿐이다 — Academy 문구 다듬기에는 웹 검색 근거가 필요 없으므로 {@code groundingChunks}/
 * {@code webSearchQueries}는 버린다.
 *
 * <p><b>주의(공유 상태)</b>: {@code ChartGeminiClient}는 싱글턴 Bean이고 admin이 Chart 화면에서
 * 설정하는 runtime API key override가 전역으로 적용된다. 즉 이 provider는 Chart 기능과 같은
 * 활성 provider/키를 공유한다 — Academy Builder AI 전용 키 분리가 필요해지면 별도 Bean이
 * 필요하다(이번 Phase 범위 아님).
 */
@Component
public class ChartGeminiAcademyAiProvider implements AiProvider {

  private final ChartGeminiClient chartGeminiClient;

  public ChartGeminiAcademyAiProvider(ChartGeminiClient chartGeminiClient) {
    this.chartGeminiClient = chartGeminiClient;
  }

  @Override
  public AiCompletion complete(String prompt, AiTaskLimits limits) {
    if (!chartGeminiClient.isReady()) {
      return AiCompletion.failed("AI provider가 구성되어 있지 않습니다(서버 관리자에게 문의해 주세요).");
    }
    ChartGeminiClient.GroundedResponse response = chartGeminiClient.generateGrounded(prompt);
    if (!response.ok()) {
      return AiCompletion.failed(response.errorMessage());
    }
    String text = response.text() == null ? "" : response.text().trim();
    if (text.isBlank()) {
      return AiCompletion.failed("AI가 빈 응답을 반환했습니다.");
    }
    if (limits != null && limits.maxOutputChars() > 0 && text.length() > limits.maxOutputChars()) {
      return AiCompletion.failed("AI 응답이 허용 길이(" + limits.maxOutputChars() + "자)를 초과했습니다.");
    }
    return AiCompletion.ok(text);
  }

  @Override
  public boolean isReady() {
    return chartGeminiClient.isReady();
  }

  @Override
  public String name() {
    return chartGeminiClient.isReady() ? chartGeminiClient.provider() : "none";
  }
}
