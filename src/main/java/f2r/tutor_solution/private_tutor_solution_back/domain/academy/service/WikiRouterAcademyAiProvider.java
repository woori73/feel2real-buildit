package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiAiRouterClient;
import org.springframework.stereotype.Component;

/**
 * Academy Builder "AI로 다듬기"용 {@link AiProvider} — 공용 {@link WikiAiRouterClient}가
 * BuildIt Wiki AI Router(POST /api/ai/complete, provider=auto)를 서버-서버로 호출하게 위임한다.
 *
 * <p>호출 로직은 공용 client로 이동했고 이 클래스는 Academy의 {@code AiProvider} 계약(카피라이터
 * system prompt + Task별 출력 길이 상한)만 유지한다. Academy 전용 호출 코드가 중복 생기지
 * 않게 하기 위한 추출이며, 기존 동작(X-BuildIt-* 서명, provider=auto, 결과 result-object)은
 * 그대로다.
 */
@Component
public class WikiRouterAcademyAiProvider implements AiProvider {
  private static final String SYSTEM_PROMPT =
      "너는 학원 홈페이지 문구를 다듬는 한국어 카피라이터다. 제공된 정보 밖의 사실을 새로 지어내지"
          + " 않고, 과장 없이 자연스럽고 신뢰가 가는 문장으로만 다듬는다.";

  private final WikiAiRouterClient wikiAiRouterClient;

  public WikiRouterAcademyAiProvider(WikiAiRouterClient wikiAiRouterClient) {
    this.wikiAiRouterClient = wikiAiRouterClient;
  }

  @Override
  public AiCompletion complete(String prompt, AiTaskLimits limits) {
    WikiAiRouterClient.Completion completion =
        wikiAiRouterClient.complete(SYSTEM_PROMPT, prompt, limits == null ? 0 : limits.maxOutputChars());
    if (!completion.ok()) {
      return AiCompletion.failed(completion.errorMessage());
    }
    return AiCompletion.ok(completion.text());
  }

  @Override
  public boolean isReady() {
    // Gemini/OpenRouter 키 보유 여부는 Wiki 백엔드(Python) 프로세스만 안다 — Java 쪽에는
    // 절대 노출하지 않는다는 요구사항 때문에 여기서 미리 알 수 없다. 실제 준비 상태는
    // complete() 호출 시 성공/실패로만 드러난다.
    return true;
  }

  @Override
  public String name() {
    return "wiki-router";
  }
}
