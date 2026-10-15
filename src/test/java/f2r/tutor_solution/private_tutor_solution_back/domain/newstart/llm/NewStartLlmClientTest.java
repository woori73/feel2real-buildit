package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NewStartLlmClient — primary 1회 성공 시 fallback 0회, §4 기술적 실패 시에만 fallback 1회,
 * 양쪽 실패 시 통제된 오류(loop 0)라는 호출 횟수 계약을 fake transport로 측정한다.
 * fallback은 primary와 완전히 같은 system prompt·question·근거 컨텍스트를 받는다(재검색 없음).
 */
@DisplayName("NewStartLlmClient — Lightning primary 1회 / Qwen fallback 1회 / loop 0 호출 계약")
class NewStartLlmClientTest {

  /** 결과를 순서대로 반환하고 호출 인자·횟수를 센다. */
  private static class RecordingTransport implements NewStartLlmTransport {
    private final String name;
    private final Deque<NewStartLlmCompletion> results = new ArrayDeque<>();
    int calls;
    String lastSystem;
    String lastPrompt;
    int lastMaxChars;

    RecordingTransport(String name, NewStartLlmCompletion... outcomes) {
      this.name = name;
      results.addAll(List.of(outcomes));
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public NewStartLlmCompletion complete(String systemPrompt, String prompt, int maxOutputChars) {
      calls++;
      lastSystem = systemPrompt;
      lastPrompt = prompt;
      lastMaxChars = maxOutputChars;
      return results.isEmpty()
          ? NewStartLlmCompletion.failed("고갈: 추가 호출은 금지", false)
          : results.pop();
    }
  }

  private static final NewStartLlmCompletion LIGHTNING_OK =
      NewStartLlmCompletion.success("라이트닝 답변", "nvidia", "nvidia/nemotron-3.5-lightning-30b-a3b");

  private static final NewStartLlmCompletion QWEN_OK =
      NewStartLlmCompletion.success("Qwen 답변", "openrouter", "qwen/qwen3.7-flash");

  private static NewStartLlmCompletion technicalFailure() {
    return NewStartLlmCompletion.failed("AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.", true);
  }

  @Test
  @DisplayName("given Lightning 성공 when complete then Qwen 호출은 0회이고 답변·모델을 그대로 돌려준다")
  void complete_lightningSuccessNeverCallsQwen() {
    RecordingTransport primary = new RecordingTransport("nvidia-nim", LIGHTNING_OK);
    RecordingTransport fallback = new RecordingTransport("openrouter", QWEN_OK);

    NewStartLlmCompletion result = new NewStartLlmClient(primary, fallback)
        .complete("시스템", "부실차주 자격이 뭔가요?", 4000);

    assertThat(result.ok()).isTrue();
    assertThat(result.text()).isEqualTo("라이트닝 답변");
    assertThat(result.model()).isEqualTo("nvidia/nemotron-3.5-lightning-30b-a3b");
    assertThat(primary.calls).isEqualTo(1);
    assertThat(fallback.calls).isZero();
  }

  @Test
  @DisplayName("given Lightning 기술적 싌(timeout·429·5xx·빈 응답) when complete then Qwen 1회로 성공하고 동일 컨텍스트를 넘긴다")
  void complete_technicalFailuresFallBackOnce() {
    for (String reason : List.of("connection failure", "timeout", "429", "5xx", "empty assistant content")) {
      RecordingTransport primary = new RecordingTransport("nvidia-nim",
          NewStartLlmCompletion.failed(reason, true));
      RecordingTransport fallback = new RecordingTransport("openrouter", QWEN_OK);

      NewStartLlmCompletion result = new NewStartLlmClient(primary, fallback)
          .complete("시스템\n[NewStart 지식베이스 근거]\n- 공식 자료", "질문", 4000);

      assertThat(result.ok()).as(reason).isTrue();
      assertThat(result.text()).as(reason).isEqualTo("Qwen 답변");
      assertThat(primary.calls).as(reason).isEqualTo(1);
      assertThat(fallback.calls).as(reason).isEqualTo(1);
      // 재검색 없이 같은 system prompt·같은 질문·같은 상한으로 모델만 교체된다.
      assertThat(fallback.lastSystem).isEqualTo("시스템\n[NewStart 지식베이스 근거]\n- 공식 자료");
      assertThat(fallback.lastPrompt).isEqualTo("질문");
      assertThat(fallback.lastMaxChars).isEqualTo(4000);
    }
  }

  @Test
  @DisplayName("given interrupted 응답 when complete then 기술적 실패로 Qwen 1회")
  void complete_interruptedFallsBackOnce() {
    RecordingTransport primary = new RecordingTransport("nvidia-nim",
        NewStartLlmCompletion.failed("interrupted", true));
    RecordingTransport fallback = new RecordingTransport("openrouter", QWEN_OK);

    NewStartLlmCompletion result = new NewStartLlmClient(primary, fallback).complete("시스템", "질문", 4000);

    assertThat(result.ok()).isTrue();
    assertThat(primary.calls).isEqualTo(1);
    assertThat(fallback.calls).isEqualTo(1);
  }

  @Test
  @DisplayName("given Lightning 401(기술적 실패 아님) when complete then Qwen으로 조용히 교체하지 않고 통제된 실패를 돌려준다")
  void complete_nonTechnicalFailureDoesNotFallBack() {
    RecordingTransport primary = new RecordingTransport("nvidia-nim",
        NewStartLlmCompletion.failed("Invalid API key", false));
    RecordingTransport fallback = new RecordingTransport("openrouter", QWEN_OK);

    NewStartLlmCompletion result = new NewStartLlmClient(primary, fallback).complete("시스템", "질문", 4000);

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("Invalid API key");
    assertThat(primary.calls).isEqualTo(1);
    assertThat(fallback.calls).isZero();
  }

  @Test
  @DisplayName("given Lightning 실패 + Qwen 실패 when complete then 추가 재시도 0회, 마지막 실패 메시지를 돌려준다")
  void complete_bothFailStopsWithoutLoop() {
    RecordingTransport primary = new RecordingTransport("nvidia-nim", technicalFailure());
    RecordingTransport fallback = new RecordingTransport("openrouter",
        NewStartLlmCompletion.failed("OpenRouter 응답 없음", false));

    NewStartLlmCompletion result = new NewStartLlmClient(primary, fallback).complete("시스템", "질문", 4000);

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("OpenRouter 응답 없음");
    assertThat(primary.calls).isEqualTo(1);
    assertThat(fallback.calls).isEqualTo(1);
  }

  @Test
  @DisplayName("given 상한을 넘는 답변 when complete then 답변 출력 상한으로 자른다")
  void complete_capsAnswerAtMaxChars() {
    RecordingTransport primary = new RecordingTransport("nvidia-nim",
        NewStartLlmCompletion.success("가".repeat(50), "nvidia", "model"));

    NewStartLlmCompletion result = new NewStartLlmClient(primary,
        new RecordingTransport("openrouter", QWEN_OK)).complete("시스템", "질문", 20);

    assertThat(result.text()).hasSize(20);
  }
}
