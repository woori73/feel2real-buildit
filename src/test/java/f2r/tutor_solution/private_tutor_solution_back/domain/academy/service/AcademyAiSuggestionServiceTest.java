package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiSuggestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiTaskType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("AcademyAiSuggestionService — Context/Task/Provider 조립 + allowlist + 응답 검증")
class AcademyAiSuggestionServiceTest {

  private AcademyAiContextService contextService;
  private AcademyAiPromptBuilder promptBuilder;
  private FakeAiProvider provider;
  private AcademyAiSuggestionService service;
  private JwtTokenProvider.AuthenticatedPrincipal principal;
  private final AcademyAiContext fixtureContext = new AcademyAiContext(
      "우리학원", "소개", List.of("영어"), List.of("MIDDLE"),
      "김원장", "대표원장", "기존 소개문", List.of("철학1", "", "", ""), List.of());

  @BeforeEach
  void setUp() {
    contextService = mock(AcademyAiContextService.class);
    promptBuilder = mock(AcademyAiPromptBuilder.class);
    provider = new FakeAiProvider();
    service = new AcademyAiSuggestionService(contextService, promptBuilder, provider);
    principal = mock(JwtTokenProvider.AuthenticatedPrincipal.class);
    when(contextService.buildContext(principal)).thenReturn(fixtureContext);
    when(promptBuilder.build(any(), any(), any(), any())).thenReturn("PROMPT");
  }

  @Test
  @DisplayName("given 정상 요청 when 생성 then Context→Prompt→Provider 파이프라인을 거쳐 정제된 suggestion을 돌려준다")
  void generate_returns_sanitized_suggestion() {
    provider.respondWith("```\n\"다듬어진 소개문입니다\"\n```");

    AcademyAiSuggestion suggestion = service.generate(
        principal, AcademyAiTaskType.PRINCIPAL_INTRO, "principalDesc", "기존 소개문");

    assertThat(suggestion.task()).isEqualTo(AcademyAiTaskType.PRINCIPAL_INTRO);
    assertThat(suggestion.target()).isEqualTo("principalDesc");
    assertThat(suggestion.currentValue()).isEqualTo("기존 소개문");
    // 코드펜스 + 감싸는 따옴표가 제거되어야 한다
    assertThat(suggestion.suggestedValue()).isEqualTo("다듬어진 소개문입니다");
    assertThat(provider.lastPrompt).isEqualTo("PROMPT");
  }

  @Test
  @DisplayName("given task에 허용되지 않은 target when 생성 then 400 성격의 예외 + provider를 호출하지 않는다")
  void generate_rejects_target_not_in_allowlist_for_task() {
    assertThatThrownBy(() -> service.generate(
        principal, AcademyAiTaskType.PRINCIPAL_INTRO, "philosophyItem1", "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("허용되지 않는 target");
    assertThat(provider.callCount).isZero();
  }

  @Test
  @DisplayName("given 완전히 임의의 target(academyId 등) when 생성 then 거부된다")
  void generate_rejects_arbitrary_target() {
    assertThatThrownBy(() -> service.generate(
        principal, AcademyAiTaskType.PRINCIPAL_INTRO, "academyId", "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.generate(
        principal, AcademyAiTaskType.PHILOSOPHY_ITEM, "published", "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(provider.callCount).isZero();
  }

  @Test
  @DisplayName("given task가 null when 생성 then 거부된다")
  void generate_rejects_null_task() {
    assertThatThrownBy(() -> service.generate(principal, null, "principalDesc", "x"))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(contextService);
  }

  @Test
  @DisplayName("given provider가 준비되지 않음 when 생성 then AcademyAiGenerationException + Academy는 건드리지 않는다")
  void generate_surfaces_provider_not_ready() {
    provider.setReady(false);
    assertThatThrownBy(() -> service.generate(
        principal, AcademyAiTaskType.PRINCIPAL_INTRO, "principalDesc", "x"))
        .isInstanceOf(AcademyAiGenerationException.class);
  }

  @Test
  @DisplayName("given provider가 오류를 반환 when 생성 then 오류 메시지를 그대로 전달한다")
  void generate_surfaces_provider_error_message() {
    provider.failWith("REQUEST_PROCESSING_ERROR: 네트워크 오류");
    assertThatThrownBy(() -> service.generate(
        principal, AcademyAiTaskType.PRINCIPAL_INTRO, "principalDesc", "x"))
        .isInstanceOf(AcademyAiGenerationException.class)
        .hasMessageContaining("네트워크 오류");
  }

  @Test
  @DisplayName("given provider가 빈 응답을 반환 when 생성 then 거부한다(빈 문자열을 suggestion으로 만들지 않는다)")
  void generate_rejects_blank_response() {
    provider.respondWith("   ");
    assertThatThrownBy(() -> service.generate(
        principal, AcademyAiTaskType.PRINCIPAL_INTRO, "principalDesc", "x"))
        .isInstanceOf(AcademyAiGenerationException.class);
  }

  @Test
  @DisplayName("given provider 응답이 허용 길이를 초과 when 생성 then 거부한다(oversized 응답 방어)")
  void generate_rejects_oversized_response() {
    provider.respondWith("가".repeat(1000));
    assertThatThrownBy(() -> service.generate(
        principal, AcademyAiTaskType.PRINCIPAL_INTRO, "principalDesc", "x"))
        .isInstanceOf(AcademyAiGenerationException.class)
        .hasMessageContaining("허용 길이");
  }

  @Test
  @DisplayName("given currentValue가 지나치게 김 when 생성 then provider 호출 전에 거부한다")
  void generate_rejects_oversized_current_value() {
    String huge = "가".repeat(5000);
    assertThatThrownBy(() -> service.generate(
        principal, AcademyAiTaskType.PRINCIPAL_INTRO, "principalDesc", huge))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(provider.callCount).isZero();
  }

  @Test
  @DisplayName("given currentValue가 null when 생성 then 빈 문자열로 안전하게 처리한다(NPE 없음)")
  void generate_handles_null_current_value() {
    provider.respondWith("새로 작성된 소개문");
    AcademyAiSuggestion suggestion = service.generate(
        principal, AcademyAiTaskType.PRINCIPAL_INTRO, "principalDesc", null);
    assertThat(suggestion.currentValue()).isEmpty();
  }

  @Test
  @DisplayName("given 같은 Task를 다시 호출(다시 생성) when 매번 provider를 새로 호출한다 then 매번 다른 응답을 받을 수 있다")
  void regenerate_calls_provider_again() {
    provider.respondWith("첫 번째 제안");
    AcademyAiSuggestion first = service.generate(
        principal, AcademyAiTaskType.PRINCIPAL_INTRO, "principalDesc", "x");
    provider.respondWith("두 번째 제안");
    AcademyAiSuggestion second = service.generate(
        principal, AcademyAiTaskType.PRINCIPAL_INTRO, "principalDesc", "x");

    assertThat(first.suggestedValue()).isEqualTo("첫 번째 제안");
    assertThat(second.suggestedValue()).isEqualTo("두 번째 제안");
    assertThat(provider.callCount).isEqualTo(2);
  }

  @Test
  @DisplayName("given PHILOSOPHY_ITEM Task when 4개 target 모두 허용되는지 확인")
  void generate_allows_all_four_philosophy_targets() {
    provider.respondWith("다듬은 철학 문구");
    for (String target : List.of("philosophyItem1", "philosophyItem2", "philosophyItem3", "philosophyItem4")) {
      AcademyAiSuggestion suggestion = service.generate(
          principal, AcademyAiTaskType.PHILOSOPHY_ITEM, target, "x");
      assertThat(suggestion.target()).isEqualTo(target);
    }
  }
}
