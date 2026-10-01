package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.NewStartLlmProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiAiRouterClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 2026-10-01 결정: 지피지기 LLM primary = 기존 내신Wiki AI Router(Qwen). NVIDIA Nemotron은 primary에서 제외하되 보존한다.
 * Router 호출은 근거가 이미 조립된 system prompt를 그대로 전달할 뿐이며 어떤 secret도 다루지 않는다.
 */
@DisplayName("지피지기 LLM primary — 기존 Wiki AI Router(Qwen), NVIDIA 보존")
class WikiRouterPrimaryTest {

  private static NewStartLlmTransport named(String name) {
    return new NewStartLlmTransport() {
      @Override public String name() { return name; }
      @Override public NewStartLlmCompletion complete(String s, String p, int m) { return NewStartLlmCompletion.success("x", name, name); }
    };
  }

  @Test
  @DisplayName("기본 설정은 wiki-router이고 nvidia는 설정으로만 선택된다(대소문자 무관, 미설정/이상값은 router)")
  void primaryDefaultsToWikiRouter_nvidiaIsOptIn() {
    NewStartLlmTransport router = named("wiki-router");
    NewStartLlmTransport nvidia = named("nvidia");

    assertThat(new NewStartLlmProperties().getPrimary()).isEqualTo("wiki-router");
    assertThat(NewStartLlmClient.selectPrimary(new NewStartLlmProperties().getPrimary(), router, nvidia)).isSameAs(router);
    assertThat(NewStartLlmClient.selectPrimary(null, router, nvidia)).isSameAs(router);
    assertThat(NewStartLlmClient.selectPrimary("  ", router, nvidia)).isSameAs(router);
    assertThat(NewStartLlmClient.selectPrimary("something-else", router, nvidia)).isSameAs(router);
    assertThat(NewStartLlmClient.selectPrimary("nvidia", router, nvidia)).isSameAs(nvidia);
    assertThat(NewStartLlmClient.selectPrimary(" NVIDIA ", router, nvidia)).isSameAs(nvidia);
  }

  @Test
  @DisplayName("NVIDIA transport와 설정은 삭제되지 않고 보존된다(비교·재사용용)")
  void nvidiaIsPreserved() {
    assertThat(new NewStartLlmProperties().getNvidia().getModel()).contains("nemotron");
    assertThat(NvidiaNimLlmTransport.class).isNotNull();
  }

  @Test
  @DisplayName("Router 성공 → NewStart 성공 결과(text/provider/model 유지), system/prompt/maxOutputChars를 그대로 전달한다")
  void routerSuccess_passesPromptThrough() {
    WikiAiRouterClient client = mock(WikiAiRouterClient.class);
    when(client.complete("시스템+근거", "질문", 4000))
        .thenReturn(new WikiAiRouterClient.Completion(true, "전자책에서는 이렇게 진행됐습니다.", null, "openrouter", "qwen/qwen3.7-flash"));

    NewStartLlmCompletion c = new WikiRouterLlmTransport(client).complete("시스템+근거", "질문", 4000);

    assertThat(c.ok()).isTrue();
    assertThat(c.text()).isEqualTo("전자책에서는 이렇게 진행됐습니다.");
    assertThat(c.model()).isEqualTo("qwen/qwen3.7-flash");
    verify(client).complete("시스템+근거", "질문", 4000);
  }

  @Test
  @DisplayName("Router 실패 → 내부 상세 없이 통제된 문구만 내려가고, fallback 후보다. 로그인 부재만 fallback 비대상")
  void routerFailure_isControlled() {
    WikiAiRouterClient client = mock(WikiAiRouterClient.class);
    when(client.complete("s", "p", 100))
        .thenReturn(WikiAiRouterClient.Completion.failed("upstream openrouter 502 secret-detail"));
    NewStartLlmCompletion failed = new WikiRouterLlmTransport(client).complete("s", "p", 100);
    assertThat(failed.ok()).isFalse();
    assertThat(failed.errorMessage()).doesNotContain("openrouter").doesNotContain("secret-detail").contains("AI 서비스 연결에 실패");
    assertThat(failed.fallbackEligible()).isTrue();

    when(client.complete("s", "p", 101)).thenReturn(WikiAiRouterClient.Completion.failed("로그인이 필요합니다."));
    assertThat(new WikiRouterLlmTransport(client).complete("s", "p", 101).fallbackEligible()).isFalse();
  }

  @Test
  @DisplayName("router primary가 실패해도 NewStartLlmClient 계약은 그대로 — fallback 1회, loop 없음")
  void clientContractUnchangedWithRouterPrimary() {
    WikiAiRouterClient client = mock(WikiAiRouterClient.class);
    when(client.complete("s", "p", 100)).thenReturn(WikiAiRouterClient.Completion.failed("x"));
    int[] fallbackCalls = {0};
    NewStartLlmTransport fallback = new NewStartLlmTransport() {
      @Override public String name() { return "fb"; }
      @Override public NewStartLlmCompletion complete(String s, String p, int m) {
        fallbackCalls[0]++;
        return NewStartLlmCompletion.success("fallback 답변", "openrouter", "qwen");
      }
    };

    NewStartLlmCompletion c = new NewStartLlmClient(new WikiRouterLlmTransport(client), fallback).complete("s", "p", 100);

    assertThat(c.ok()).isTrue();
    assertThat(c.text()).isEqualTo("fallback 답변");
    assertThat(fallbackCalls[0]).isEqualTo(1);
  }
}
