package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartKnowledgeContextService.RagContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartKnowledgeContextService.SourceInfo;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiAiRouterClient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * NewStartAiChatService — given/when/then 단위테스트. 실제 Wiki 백엔드 대신 {@link FakeRouterClient}
 * (complete만 재정의한 {@link WikiAiRouterClient})로 router 위임과 입력 가드만 검증하고,
 * {@link NewStartKnowledgeContextService}는 mock으로 대체해 RAG context 조립 결과가 prompt에
 * 실제로 섞이는지, sources가 응답에 그대로 실리는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NewStartAiChatService — RAG context 조립 + Wiki AI Router 위임")
class NewStartAiChatServiceTest {

  @Mock private NewStartKnowledgeContextService contextService;

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

  private static final RagContext EMPTY_CONTEXT = new RagContext(List.of(), List.of(), List.of(), false);

  @Test
  @DisplayName("given 정상 질문 when ask then RAG context를 조립해 router에 붙여 위임하고 답변+출처를 돌려준다")
  void ask_delegatesToRouterWithAssembledContext() {
    FakeRouterClient client = new FakeRouterClient();
    SourceInfo source =
        new SourceInfo(1L, "OFFICIAL_CURRENT", "새출발기금 개요", "https://www.newstartfund.or.kr", null, "새출발기금", "개요");
    RagContext context = new RagContext(List.of(), List.of(), List.of(), false);
    when(contextService.retrieve("새출발기금이 무엇인가요?")).thenReturn(context);
    when(contextService.toPromptBlock(context)).thenReturn("[OFFICIAL]\n- 새출발기금 개요\n요약\n");
    when(contextService.toSources(context)).thenReturn(List.of(source));

    NewStartAiChatService service = new NewStartAiChatService(client, contextService);
    NewStartAiChatService.NewStartAiAnswer answer = service.ask("새출발기금이 무엇인가요?");

    assertThat(answer.ok()).isTrue();
    assertThat(answer.answer()).isEqualTo("답변입니다.");
    assertThat(answer.model()).isEqualTo("qwen/qwen3.7-flash");
    assertThat(answer.sources()).containsExactly(source);
    assertThat(client.wasCalled).isTrue();
    assertThat(client.capturedPrompt).contains("[OFFICIAL]");
    assertThat(client.capturedPrompt).contains("새출발기금 개요");
    assertThat(client.capturedPrompt).contains("[사용자 질문]\n새출발기금이 무엇인가요?");
    assertThat(client.capturedSystem).contains("새출발기금");
    assertThat(client.capturedSystem).contains("OFFICIAL");
    assertThat(client.capturedSystem).contains("EBOOK");
    assertThat(client.capturedSystem).contains("CASE");
    assertThat(client.capturedMaxChars).isPositive();
  }

  @Test
  @DisplayName("given ACTIVE 근거가 하나도 없을 때 when ask then 근거 없음을 명시한 block으로 grounded fallback한다")
  void ask_gracefulFallbackWhenNoActiveKnowledge() {
    FakeRouterClient client = new FakeRouterClient();
    when(contextService.retrieve(anyString())).thenReturn(EMPTY_CONTEXT);
    when(contextService.toPromptBlock(EMPTY_CONTEXT))
        .thenReturn("[근거 자료]\n현재 이 질문과 일치하는 승인된(ACTIVE) 자료가 없습니다. 일반적인 안내만 하고"
            + " 구체적인 수치·조건·결과는 만들어내지 마세요.");
    when(contextService.toSources(EMPTY_CONTEXT)).thenReturn(List.of());

    NewStartAiChatService service = new NewStartAiChatService(client, contextService);
    NewStartAiChatService.NewStartAiAnswer answer = service.ask("연체 10일이 지나면 어떻게 되나요?");

    assertThat(answer.ok()).isTrue();
    assertThat(answer.sources()).isEmpty();
    assertThat(client.capturedPrompt).contains("현재 이 질문과 일치하는 승인된(ACTIVE) 자료가 없습니다");
  }

  @Test
  @DisplayName("given 빈 질문 when ask then retrieval/router를 타지 않고 실패를 돌려준다")
  void ask_rejectsBlankQuestion() {
    FakeRouterClient client = new FakeRouterClient();
    NewStartAiChatService service = new NewStartAiChatService(client, contextService);

    NewStartAiChatService.NewStartAiAnswer answer = service.ask("   ");

    assertThat(answer.ok()).isFalse();
    assertThat(answer.errorMessage()).contains("질문");
    assertThat(answer.sources()).isEmpty();
    assertThat(client.wasCalled).isFalse();
    verifyNoInteractions(contextService);
  }

  @Test
  @DisplayName("given 길이 초과 질문 when ask then retrieval/router를 타지 않고 실패를 돌려준다")
  void ask_rejectsOverlongQuestion() {
    FakeRouterClient client = new FakeRouterClient();
    NewStartAiChatService service = new NewStartAiChatService(client, contextService);

    NewStartAiChatService.NewStartAiAnswer answer = service.ask("가".repeat(2001));

    assertThat(answer.ok()).isFalse();
    assertThat(answer.errorMessage()).contains("너무 깁니다");
    assertThat(client.wasCalled).isFalse();
    verifyNoInteractions(contextService);
  }

  @Test
  @DisplayName("given router 실패 when ask then router의 사용자용 안내 문구를 그대로 돌려준다")
  void ask_propagatesRouterFailureMessage() {
    FakeRouterClient client = new FakeRouterClient();
    client.stubbed = WikiAiRouterClient.Completion.failed("AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    when(contextService.retrieve(anyString())).thenReturn(EMPTY_CONTEXT);
    when(contextService.toPromptBlock(EMPTY_CONTEXT)).thenReturn("[근거 자료]\n근거 없음");

    NewStartAiChatService service = new NewStartAiChatService(client, contextService);
    NewStartAiChatService.NewStartAiAnswer answer = service.ask("질문");

    assertThat(answer.ok()).isFalse();
    assertThat(answer.errorMessage()).contains("잠시 후 다시 시도");
    assertThat(answer.answer()).isEmpty();
    assertThat(answer.sources()).isEmpty();
  }

  @Test
  @DisplayName("given 앞뒤 공백 질문 when ask then strip된 질문으로 retrieval하고 prompt 끝에도 strip된 질문이 실린다")
  void ask_stripsQuestion() {
    FakeRouterClient client = new FakeRouterClient();
    when(contextService.retrieve("질문")).thenReturn(EMPTY_CONTEXT);
    when(contextService.toPromptBlock(EMPTY_CONTEXT)).thenReturn("[근거 자료]\n근거 없음");

    NewStartAiChatService service = new NewStartAiChatService(client, contextService);
    service.ask("  질문  ");

    verify(contextService).retrieve("질문");
    assertThat(client.capturedPrompt).endsWith("[사용자 질문]\n질문");
  }
}
