package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.OpenRouterQwenLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.WikiRouterLlmTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 기출분석 전용 LLM 호출. 기존 서비스(입시상담·지피지기)의 transport 빈을 그대로 재사용하고, 새 모델 연동을 만들지 않는다.
 * 정상 경로 primary(Wiki Router) 1회, 기술적 실패일 때만 fallback(OpenRouter) 1회 — 그 이상 재시도하지 않는다.
 */
@Component
public class ExamInsightLlmClient {

  private static final Logger log = LoggerFactory.getLogger(ExamInsightLlmClient.class);

  private final NewStartLlmTransport primary;
  private final NewStartLlmTransport fallback;

  @Autowired
  public ExamInsightLlmClient(WikiRouterLlmTransport wikiRouter, OpenRouterQwenLlmTransport openRouter) {
    this((NewStartLlmTransport) wikiRouter, (NewStartLlmTransport) openRouter);
  }

  /** 테스트·수동 조립용. */
  public ExamInsightLlmClient(NewStartLlmTransport primary, NewStartLlmTransport fallback) {
    this.primary = primary;
    this.fallback = fallback;
  }

  public NewStartLlmCompletion complete(String systemPrompt, String prompt, int maxOutputChars) {
    NewStartLlmCompletion first = primary.complete(systemPrompt, prompt, maxOutputChars);
    if (first.ok() || !first.fallbackEligible()) {
      log.info("exam-insight llm attempts=1 ok={} provider={}", first.ok(), first.provider());
      return first;
    }
    NewStartLlmCompletion second = fallback.complete(systemPrompt, prompt, maxOutputChars);
    log.info("exam-insight llm attempts=2 fallbackOk={} provider={}", second.ok(), second.provider());
    return second;
  }
}
