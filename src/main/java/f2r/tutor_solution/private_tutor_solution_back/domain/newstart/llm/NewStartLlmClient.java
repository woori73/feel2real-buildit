package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * NewStart 지피지기 전용 LLM 클라이언트 — NVIDIA NIM Lightning DIRECT primary,
 * OpenRouter Qwen DIRECT fallback. 공용 Wiki AI Router는 이 경로에서 사용하지 않는다.
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

  private final NewStartLlmTransport primary;
  private final NewStartLlmTransport fallback;

  public NewStartLlmClient(
      @Qualifier("nvidiaNimLlmTransport") NewStartLlmTransport primary,
      @Qualifier("openRouterQwenLlmTransport") NewStartLlmTransport fallback) {
    this.primary = primary;
    this.fallback = fallback;
  }

  public NewStartLlmCompletion complete(String systemPrompt, String prompt, int maxOutputChars) {
    NewStartLlmCompletion first = primary.complete(systemPrompt, prompt, maxOutputChars);
    if (first.ok()) {
      return cap(first, maxOutputChars);
    }
    if (!first.fallbackEligible()) {
      return first;
    }
    NewStartLlmCompletion second = fallback.complete(systemPrompt, prompt, maxOutputChars);
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
}
