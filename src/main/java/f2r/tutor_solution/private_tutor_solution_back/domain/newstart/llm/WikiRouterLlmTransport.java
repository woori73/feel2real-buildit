package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiAiRouterClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * NewStart LLM transport — 기존 내신Wiki AI Router({@link WikiAiRouterClient}, {@code provider=auto})를 그대로 재사용한다.
 *
 * <p>NewStart가 먼저 전자책·실제 사례·공식자료 근거를 검색해 system prompt에 조립하면, 이 transport는 그 프롬프트를
 * Router에 전달해 답변만 받는다(Router/Qwen이 RAG를 하지 않는다). Router 설정·모델·API key는 건드리지도 복사하지도 않는다 —
 * key는 Wiki 서비스 환경에만 있고 이 클래스는 어떤 secret도 읽지 않는다.
 *
 * <p>NVIDIA NIM({@link NvidiaNimLlmTransport})은 응답시간이 크게 변동해(2026-09-30~10-01 실측 29초~180초 초과) primary에서
 * 제외했지만 삭제하지 않고 {@code app.newstart.llm.primary=nvidia}로 비교·재사용할 수 있게 보존한다.
 */
@Component
public class WikiRouterLlmTransport implements NewStartLlmTransport {

  private static final Logger log = LoggerFactory.getLogger(WikiRouterLlmTransport.class);
  static final String NAME = "wiki-router";
  private static final String LOGIN_REQUIRED = "로그인이 필요합니다.";
  private static final String GENERIC_ERROR = "AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.";

  private final WikiAiRouterClient routerClient;

  public WikiRouterLlmTransport(WikiAiRouterClient routerClient) {
    this.routerClient = routerClient;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public NewStartLlmCompletion complete(String systemPrompt, String prompt, int maxOutputChars) {
    long t = System.nanoTime();
    WikiAiRouterClient.Completion c = routerClient.complete(systemPrompt, prompt, maxOutputChars);
    long ms = Math.round((System.nanoTime() - t) / 1_000_000.0);
    log.info("newstart wiki-router call ok={} routerMs={} provider={} model={} requestChars={} contentChars={}",
        c.ok(), ms, c.provider(), c.model(),
        (systemPrompt == null ? 0 : systemPrompt.length()) + (prompt == null ? 0 : prompt.length()),
        c.text() == null ? 0 : c.text().length());
    if (c.ok()) {
      return NewStartLlmCompletion.success(c.text(), c.provider() == null ? NAME : c.provider(), c.model());
    }
    // Router 오류 문구(내부 provider/예외 상세 가능성)는 회원 응답으로 내리지 않는다 — 통제된 일반 문구만 쓴다.
    // 로그인 부재만 fallback 비대상이고, 나머지는 기술적 실패로 fallback 후보다.
    boolean eligible = !LOGIN_REQUIRED.equals(c.errorMessage());
    return NewStartLlmCompletion.failed(GENERIC_ERROR, eligible);
  }
}
