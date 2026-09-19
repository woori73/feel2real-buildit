package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiAiRouterClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * WikiRouterAcademyAiProvider — 공용 {@link WikiAiRouterClient} 위임 어댑터.
 *
 * <p>호출·서명·provider=auto 등 HTTP 계약은 추출된 {@code WikiAiRouterClientTest}가 검증하고,
 * 이 테스트는 Academy 계약(AiProvider result-object, 카피라이터 system prompt, 길이 상한 전달,
 * name/isReady의 비밀 노출 없음)만 검증한다.
 */
@DisplayName("WikiRouterAcademyAiProvider — 공용 WikiAiRouterClient 위임")
class WikiRouterAcademyAiProviderTest {

  /** 네트워크 없이 complete() 인자를 캡처하는 fake client. */
  private static class FakeRouterClient extends WikiAiRouterClient {
    String capturedSystem;
    String capturedPrompt;
    int capturedMaxChars;
    WikiAiRouterClient.Completion stubbed =
        new WikiAiRouterClient.Completion(true, "다듬은 원장 소개문", null, "qwen", "qwen/qwen3.7-flash");

    FakeRouterClient() {
      super(null, null, "http://127.0.0.1:1");
    }

    @Override
    public Completion complete(String systemPrompt, String prompt, int maxOutputChars) {
      this.capturedSystem = systemPrompt;
      this.capturedPrompt = prompt;
      this.capturedMaxChars = maxOutputChars;
      return stubbed;
    }
  }

  @Test
  @DisplayName("given 정상 완성 when complete then AiProvider 계약대로 ok 결과를 돌려준다")
  void complete_returnsOkCompletion() {
    FakeRouterClient client = new FakeRouterClient();
    WikiRouterAcademyAiProvider provider = new WikiRouterAcademyAiProvider(client);

    AiCompletion result = provider.complete("원본 소개문", new AiTaskLimits(220));

    assertThat(result.ok()).isTrue();
    assertThat(result.text()).isEqualTo("다듬은 원장 소개문");
    assertThat(client.capturedPrompt).isEqualTo("원본 소개문");
    assertThat(client.capturedSystem).contains("카피라이터");
    assertThat(client.capturedMaxChars).isEqualTo(220);
  }

  @Test
  @DisplayName("given client 실패 when complete then 예외 없이 failed 결과만 돌려준다")
  void complete_translatesFailure() {
    FakeRouterClient client = new FakeRouterClient();
    client.stubbed = WikiAiRouterClient.Completion.failed("AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    WikiRouterAcademyAiProvider provider = new WikiRouterAcademyAiProvider(client);

    AiCompletion result = provider.complete("프롬프트", new AiTaskLimits(220));

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).isNotBlank();
    assertThat(result.text()).isEmpty();
  }

  @Test
  @DisplayName("given limits null when complete then maxOutputChars 0으로 위임한다")
  void complete_nullLimitsPassesZero() {
    FakeRouterClient client = new FakeRouterClient();
    WikiRouterAcademyAiProvider provider = new WikiRouterAcademyAiProvider(client);

    provider.complete("프롬프트", null);

    assertThat(client.capturedMaxChars).isZero();
  }

  @Test
  @DisplayName("given provider when name/isReady 조회 then 원본 키를 절대 노출하지 않는 고정 값만 돌려준다")
  void nameAndReadyExposeNoSecret() {
    WikiRouterAcademyAiProvider provider = new WikiRouterAcademyAiProvider(new FakeRouterClient());
    assertThat(provider.name()).isEqualTo("wiki-router");
    assertThat(provider.isReady()).isTrue();
  }
}
