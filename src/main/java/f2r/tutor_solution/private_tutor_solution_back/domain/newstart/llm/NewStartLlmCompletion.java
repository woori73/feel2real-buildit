package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

/**
 * NewStart DIRECT LLM 호출 결과. {@code text}에는 assistant content(최종 답변)만 들어 있고,
 * provider의 내부 사고(reasoning_content)는 어떤 경로에도 실리지 않는다.
 *
 * <p>{@code fallbackEligible}는 §4의 기술적 실패 판정이다 — true일 때만 fallback transport를
 * 타며, 답변 품질·스타일 차이로는 절대 true가 되지 않는다.
 */
public record NewStartLlmCompletion(
    boolean ok, String text, String errorMessage, String provider, String model, boolean fallbackEligible) {

  public static NewStartLlmCompletion success(String text, String provider, String model) {
    return new NewStartLlmCompletion(true, text, null, provider, model, false);
  }

  public static NewStartLlmCompletion failed(String errorMessage, boolean fallbackEligible) {
    return new NewStartLlmCompletion(false, "", errorMessage, null, null, fallbackEligible);
  }
}
