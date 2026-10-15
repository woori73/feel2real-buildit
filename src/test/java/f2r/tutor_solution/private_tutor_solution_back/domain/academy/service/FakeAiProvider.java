package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import java.util.function.Function;

/**
 * 테스트 전용 {@link AiProvider} — 실제 유료 API를 호출하지 않고 Suggestion lifecycle
 * (generate/apply/cancel/regenerate, provider 장애 등)을 검증하기 위한 fake다.
 *
 * <p>기본 동작은 프롬프트를 결정적으로 변환("[AI] " 접두어)해서 돌려준다. {@link #respondWith}로
 * 응답을, {@link #failWith}로 실패를 강제할 수 있다.
 */
public class FakeAiProvider implements AiProvider {

  private Function<String, AiCompletion> behavior = prompt -> AiCompletion.ok("[AI] " + prompt);
  private boolean ready = true;
  public int callCount = 0;
  public String lastPrompt;

  @Override
  public AiCompletion complete(String prompt, AiTaskLimits limits) {
    callCount++;
    lastPrompt = prompt;
    if (!ready) {
      return AiCompletion.failed("AI provider가 구성되어 있지 않습니다.");
    }
    return behavior.apply(prompt);
  }

  @Override
  public boolean isReady() {
    return ready;
  }

  @Override
  public String name() {
    return "fake";
  }

  public void respondWith(String text) {
    this.behavior = prompt -> AiCompletion.ok(text);
  }

  public void failWith(String message) {
    this.behavior = prompt -> AiCompletion.failed(message);
  }

  public void setReady(boolean ready) {
    this.ready = ready;
  }
}
