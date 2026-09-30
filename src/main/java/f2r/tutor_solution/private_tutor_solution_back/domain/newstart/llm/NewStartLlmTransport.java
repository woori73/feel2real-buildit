package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

/**
 * NewStart LLM transport. 한 번 호출로 최종 답변(assistant content)만 돌려준다.
 *
 * <p>실패는 예외가 아니라 {@link NewStartLlmCompletion#ok()}로 보고하고, 재시도·fallback
 * 판단은 호출자({@link NewStartLlmClient})가 §4 규칙으로만 한다.
 */
public interface NewStartLlmTransport {

  /** 로그·응답의 provider 식별자. */
  String name();

  NewStartLlmCompletion complete(String systemPrompt, String prompt, int maxOutputChars);
}
