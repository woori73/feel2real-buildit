package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmTransport;
import org.junit.jupiter.api.Test;

class ExamInsightLlmClientTest {

  NewStartLlmTransport primary = mock(NewStartLlmTransport.class);
  NewStartLlmTransport fallback = mock(NewStartLlmTransport.class);
  ExamInsightLlmClient client = new ExamInsightLlmClient(primary, fallback);

  @Test
  void primary가_성공하면_fallback은_호출하지_않는다() {
    // given
    when(primary.complete(anyString(), anyString(), anyInt())).thenReturn(NewStartLlmCompletion.success("ok", "p", "m"));
    // when
    NewStartLlmCompletion c = client.complete("s", "u", 100);
    // then
    assertThat(c.ok()).isTrue();
    verify(fallback, never()).complete(anyString(), anyString(), anyInt());
  }

  @Test
  void 기술적_실패면_fallback을_정확히_한번_호출한다() {
    // given
    when(primary.complete(anyString(), anyString(), anyInt())).thenReturn(NewStartLlmCompletion.failed("x", true));
    when(fallback.complete(anyString(), anyString(), anyInt())).thenReturn(NewStartLlmCompletion.success("ok", "f", "m"));
    // when
    NewStartLlmCompletion c = client.complete("s", "u", 100);
    // then
    assertThat(c.provider()).isEqualTo("f");
  }

  @Test
  void fallback_대상이_아닌_실패는_그대로_돌려준다() {
    // given
    when(primary.complete(anyString(), anyString(), anyInt())).thenReturn(NewStartLlmCompletion.failed("x", false));
    // when
    NewStartLlmCompletion c = client.complete("s", "u", 100);
    // then
    assertThat(c.ok()).isFalse();
    verify(fallback, never()).complete(anyString(), anyString(), anyInt());
  }
}
