package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightPromptBuilder.Meta;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ExamInsightServiceTest {

  ExamInsightTextExtractor extractor = mock(ExamInsightTextExtractor.class);
  ExamInsightLlmClient llm = mock(ExamInsightLlmClient.class);
  ExamInsightRateLimiter limiter;
  ExamInsightService service;

  @BeforeEach
  void setUp() {
    limiter = new ExamInsightRateLimiter(2, 10);
    service = new ExamInsightService(extractor, limiter, llm, true, 3, 40000, 16000);
  }

  ExamInsightService.Input input(String past, String scope) {
    return new ExamInsightService.Input(null, null, past, scope, new Meta("A고", "고2", "능률", "중간고사"), null);
  }

  @Test
  void 정상_분석은_두_문서와_분석번호를_돌려준다() {
    // given
    when(llm.complete(anyString(), anyString(), anyInt()))
        .thenReturn(NewStartLlmCompletion.success(ExamInsightPromptBuilderTest.valid(), "p", "m"));
    // when
    ExamInsightService.Result r = service.analyze(1L, input("1. Q", "Lesson 1 text"));
    // then
    assertThat(r.analysisMarkdown()).startsWith("# 기출문제 분석");
    assertThat(r.directionMarkdown()).startsWith("# 기출문제 기반 출제방향");
    assertThat(r.analysisId()).isNotBlank();
    assertThat(r.remainingToday()).isEqualTo(1);
  }

  @Test
  void 기출자료가_없으면_AI를_호출하지_않는다() {
    // given only scope text
    // when / then
    assertThatThrownBy(() -> service.analyze(1L, input("", "scope")))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("MISSING_PAST"));
    verify(llm, never()).complete(anyString(), anyString(), anyInt());
    assertThat(limiter.remaining(1L)).isEqualTo(2);
  }

  @Test
  void AI가_실패하면_완료로_표시하지_않고_횟수를_환불한다() {
    // given
    when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(NewStartLlmCompletion.failed("down", true));
    // when / then
    assertThatThrownBy(() -> service.analyze(1L, input("q", "s")))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("LLM_UNAVAILABLE"));
    assertThat(limiter.remaining(1L)).isEqualTo(2);
  }

  @Test
  void 형식이_틀린_AI응답도_환불하고_실패로_처리한다() {
    // given
    when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(NewStartLlmCompletion.success("대충 쓴 글", "p", "m"));
    // when / then
    assertThatThrownBy(() -> service.analyze(1L, input("q", "s")))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_AI_OUTPUT"));
    assertThat(limiter.remaining(1L)).isEqualTo(2);
  }

  @Test
  void 예상치_못한_예외는_INTERNAL로_바꾸고_환불한다() {
    // given
    when(llm.complete(anyString(), anyString(), anyInt())).thenThrow(new IllegalStateException("boom"));
    // when / then
    assertThatThrownBy(() -> service.analyze(1L, input("q", "s")))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    assertThat(limiter.remaining(1L)).isEqualTo(2);
  }

  @Test
  void 일일_한도를_넘으면_429이고_AI를_호출하지_않는다() {
    // given user already used 2
    when(llm.complete(anyString(), anyString(), anyInt()))
        .thenReturn(NewStartLlmCompletion.success(ExamInsightPromptBuilderTest.valid(), "p", "m"));
    service.analyze(1L, input("q", "s"));
    service.analyze(1L, input("q", "s"));
    // when / then
    assertThatThrownBy(() -> service.analyze(1L, input("q", "s")))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
  }

  @Test
  void 비활성화되면_503() {
    // given
    ExamInsightService off = new ExamInsightService(extractor, limiter, llm, false, 3, 40000, 16000);
    // when / then
    assertThatThrownBy(() -> off.analyze(1L, input("q", "s")))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
  }

  @Test
  void 긴_자료는_잘라서_분석하고_알려준다() {
    // given tiny cap
    ExamInsightService small = new ExamInsightService(extractor, limiter, llm, true, 3, 10, 16000);
    when(llm.complete(anyString(), anyString(), anyInt()))
        .thenReturn(NewStartLlmCompletion.success(ExamInsightPromptBuilderTest.valid(), "p", "m"));
    // when
    ExamInsightService.Result r = small.analyze(1L, input("x".repeat(100), "y".repeat(100)));
    // then
    assertThat(r.truncated()).isTrue();
    assertThat(r.notices()).hasSize(2);
  }

  @Test
  void analysisId는_같은_내용이면_같고_다르면_다르다() {
    // given
    var a = new ExamInsightPromptBuilder.Documents("a", "b");
    var b = new ExamInsightPromptBuilder.Documents("a", "c");
    // when / then
    assertThat(ExamInsightService.analysisId(a)).isEqualTo(ExamInsightService.analysisId(a));
    assertThat(ExamInsightService.analysisId(a)).isNotEqualTo(ExamInsightService.analysisId(b));
  }
}
