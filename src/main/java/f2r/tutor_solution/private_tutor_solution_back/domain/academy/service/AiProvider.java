package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

/**
 * Task와 실제 AI 완성(completion) 호출을 분리하는 경계.
 *
 * <p>이 인터페이스 하나만 구현하면 provider를 교체할 수 있다 — {@link AcademyAiSuggestionService}는
 * 이 인터페이스만 알고, 실제로 Gemini/OpenAI/다른 provider 중 무엇이 뒤에 있는지 모른다.
 * 새 provider가 생기면 구현체 하나만 추가하면 되고 Task 정의·Builder UI는 바뀌지 않는다
 * (`docs/academy/ACADEMY_BUILDER_AI_ROADMAP.md` §7 설계를 그대로 따른다).
 *
 * <p>에러를 예외로 던지지 않는다 — {@code ChartGeminiClient.GroundedResponse}와 같은
 * result-object 패턴({@link AiCompletion#ok()}가 false일 수 있음)을 따른다. Provider 장애가
 * 호출부의 제어 흐름을 깨뜨리지 않게 하기 위해서다.
 */
public interface AiProvider {

  /** 프롬프트를 완성한다. 실패해도 예외를 던지지 않고 {@code ok=false}인 결과를 돌려준다. */
  AiCompletion complete(String prompt, AiTaskLimits limits);

  /** API key 등 필요한 자격 증명이 구성되어 있는지. 실제 값은 절대 노출하지 않는다. */
  boolean isReady();

  /** 상태 표시용 provider 식별자(예: "gemini", "openai", "fake"). */
  String name();

  /** 출력 길이 상한. Task별로 다를 수 있어 호출부가 값을 정해 넘긴다. */
  record AiTaskLimits(int maxOutputChars) {}

  /**
   * Provider 호출 결과. {@code ok=false}면 {@code text}는 항상 빈 문자열이고
   * {@code errorMessage}만 의미 있다 — 호출부가 실수로 실패 응답의 빈 텍스트를 그대로 쓰는
   * 사고를 구조적으로 막는다.
   */
  record AiCompletion(boolean ok, String text, String errorMessage) {
    public static AiCompletion ok(String text) {
      return new AiCompletion(true, text, null);
    }

    public static AiCompletion failed(String errorMessage) {
      return new AiCompletion(false, "", errorMessage);
    }
  }
}
