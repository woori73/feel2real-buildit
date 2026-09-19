package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiAiRouterClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NewStartAiChatService — given/when/then 단위테스트. 실제 Wiki 백엔드 대신 {@link FakeRouterClient}
 * (complete만 재정의한 {@link WikiAiRouterClient})로 router 위임과 입력 가드만 검증한다.
 */
@DisplayName("NewStartAiChatService — Wiki AI Router 위임과 질문 가드")
class NewStartAiChatServiceTest {

  /** 네트워크 없이 complete() 인자만 캡처하는 fake. */
  private static class FakeRouterClient extends WikiAiRouterClient {
    boolean wasCalled;
    String capturedSystem;
    String capturedPrompt;
    int capturedMaxChars;
    WikiAiRouterClient.Completion stubbed =
        new WikiAiRouterClient.Completion(true, "답변입니다.", null, "qwen", "qwen/qwen3.7-flash");

    FakeRouterClient() {
      super(null, null, "http://127.0.0.1:1");
    }

    @Override
    public Completion complete(String systemPrompt, String prompt, int maxOutputChars) {
      this.wasCalled = true;
      this.capturedSystem = systemPrompt;
      this.capturedPrompt = prompt;
      this.capturedMaxChars = maxOutputChars;
      return stubbed;
    }
  }

  @Test
  @DisplayName("given 정상 질문 when ask then router에 system prompt와 함께 위임하고 답변을 돌려준다")
  void ask_delegatesToRouter() {
    FakeRouterClient client = new FakeRouterClient();
    NewStartAiChatService service = new NewStartAiChatService(client);

    NewStartAiChatService.NewStartAiAnswer answer = service.ask("새출발기금이 무엇인가요?");

    assertThat(answer.ok()).isTrue();
    assertThat(answer.answer()).isEqualTo("답변입니다.");
    assertThat(answer.model()).isEqualTo("qwen/qwen3.7-flash");
    assertThat(client.wasCalled).isTrue();
    assertThat(client.capturedPrompt).isEqualTo("새출발기금이 무엇인가요?");
    assertThat(client.capturedSystem).contains("새출발기금");
    assertThat(client.capturedMaxChars).isPositive();
  }

  @Test
  @DisplayName("given 빈 질문 when ask then router를 타지 않고 실패를 돌려준다")
  void ask_rejectsBlankQuestion() {
    FakeRouterClient client = new FakeRouterClient();
    NewStartAiChatService service = new NewStartAiChatService(client);

    NewStartAiChatService.NewStartAiAnswer answer = service.ask("   ");

    assertThat(answer.ok()).isFalse();
    assertThat(answer.errorMessage()).contains("질문");
    assertThat(client.wasCalled).isFalse();
  }

  @Test
  @DisplayName("given 길이 초과 질문 when ask then router를 타지 않고 실패를 돌려준다")
  void ask_rejectsOverlongQuestion() {
    FakeRouterClient client = new FakeRouterClient();
    NewStartAiChatService service = new NewStartAiChatService(client);

    NewStartAiChatService.NewStartAiAnswer answer = service.ask("가".repeat(2001));

    assertThat(answer.ok()).isFalse();
    assertThat(answer.errorMessage()).contains("너무 깁니다");
    assertThat(client.wasCalled).isFalse();
  }

  @Test
  @DisplayName("given router 실패 when ask then router의 사용자용 안내 문구를 그대로 돌려준다")
  void ask_propagatesRouterFailureMessage() {
    FakeRouterClient client = new FakeRouterClient();
    client.stubbed = WikiAiRouterClient.Completion.failed("AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    NewStartAiChatService service = new NewStartAiChatService(client);

    NewStartAiChatService.NewStartAiAnswer answer = service.ask("질문");

    assertThat(answer.ok()).isFalse();
    assertThat(answer.errorMessage()).contains("잠시 후 다시 시도");
    assertThat(answer.answer()).isEmpty();
  }

  @Test
  @DisplayName("given 앞뒤 공백 질문 when ask then strip된 질문이 전달된다")
  void ask_stripsQuestion() {
    FakeRouterClient client = new FakeRouterClient();
    NewStartAiChatService service = new NewStartAiChatService(client);

    service.ask("  질문  ");

    assertThat(client.capturedPrompt).isEqualTo("질문");
  }
}
