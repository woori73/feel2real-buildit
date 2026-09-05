package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiGuideSuggestion;
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

/**
 * AcademyAiGuideService(Phase 5B) — 기존 Context/Prompt/Provider 파이프라인 재사용 + 후보 배열
 * 파싱 + target allowlist/길이 검증. 이 서비스는 절대 Academy/builderDraft를 수정하지 않는다.
 */
@DisplayName("AcademyAiGuideService — Guide 문구 후보 파이프라인 + allowlist + 응답 검증")
class AcademyAiGuideServiceTest {

  private AcademyAiContextService contextService;
  private AcademyAiPromptBuilder promptBuilder;
  private FakeAiProvider provider;
  private AcademyAiGuideService service;
  private JwtTokenProvider.AuthenticatedPrincipal principal;
  private final AcademyAiContext fixtureContext = new AcademyAiContext(
      "우리학원", "히어로 소개", List.of("영어"), List.of("MIDDLE"),
      "김원장", "대표원장", "기존 소개문", List.of("철학1", "", "", ""), List.of(), null);

  @BeforeEach
  void setUp() {
    contextService = mock(AcademyAiContextService.class);
    promptBuilder = mock(AcademyAiPromptBuilder.class);
    provider = new FakeAiProvider();
    service = new AcademyAiGuideService(contextService, promptBuilder, provider);
    principal = mock(JwtTokenProvider.AuthenticatedPrincipal.class);
    when(contextService.buildContext(principal)).thenReturn(fixtureContext);
    when(promptBuilder.build(any(), any(), any(), any(), any())).thenReturn("PROMPT");
  }

  @Test
  @DisplayName("given 정상 요청 when 생성 then JSON 배열을 파싱해 후보 목록을 돌려준다")
  void generate_parses_candidate_array() {
    provider.respondWith("[\"학교별 시험, 분석부터 다릅니다\",\"출제 패턴 분석으로 내신의 차이를\",\"내신의 시작은 학교별 시험 분석\"]");

    AcademyAiGuideSuggestion result = service.generate(
        principal, "philosophyItem1", "학교별 내신 시험 분석", "전문적이지만 친근하게");

    assertThat(result.target()).isEqualTo("philosophyItem1");
    assertThat(result.candidates()).hasSize(3);
    assertThat(result.candidates().get(0)).isEqualTo("학교별 시험, 분석부터 다릅니다");
    assertThat(provider.lastPrompt).isEqualTo("PROMPT");
  }

  @Test
  @DisplayName("given 코드펜스로 감싼 응답 when 생성 then 펜스를 제거하고 파싱한다")
  void generate_strips_code_fence() {
    provider.respondWith("```json\n[\"후보1\",\"후보2\"]\n```");
    AcademyAiGuideSuggestion result = service.generate(principal, "introHeading", "몰입", "");
    assertThat(result.candidates()).containsExactly("후보1", "후보2");
  }

  @Test
  @DisplayName("given top-level intro target when 생성 then 허용되고 context.intro()가 현재 값으로 프롬프트에 전달된다")
  void generate_allows_hero_intro_target() {
    provider.respondWith("[\"새 히어로 문장\"]");
    AcademyAiGuideSuggestion result = service.generate(principal, "intro", "학교별 내신 분석", "");
    assertThat(result.target()).isEqualTo("intro");

    org.mockito.ArgumentCaptor<String> current = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(promptBuilder)
        .build(any(), any(), org.mockito.ArgumentMatchers.eq("intro"), current.capture(), any());
    assertThat(current.getValue()).isEqualTo("히어로 소개");
  }

  @Test
  @DisplayName("given allowlist 밖 target(학원명/가격/연락처 등) when 생성 then provider 호출 전에 거부한다")
  void generate_rejects_target_outside_allowlist() {
    for (String target : List.of("name", "monthlyTuition", "principalName", "academyId", "published")) {
      assertThatThrownBy(() -> service.generate(principal, target, "answer", ""))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(provider.callCount).isZero();
  }

  @Test
  @DisplayName("given target이 null when 생성 then 거부하고 context를 조회하지 않는다")
  void generate_rejects_null_target() {
    assertThatThrownBy(() -> service.generate(principal, null, "answer", ""))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(contextService);
  }

  @Test
  @DisplayName("given 답변이 지나치게 김 when 생성 then provider 호출 전에 거부한다")
  void generate_rejects_oversized_answer() {
    assertThatThrownBy(() -> service.generate(principal, "principalDesc", "가".repeat(500), ""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(provider.callCount).isZero();
  }

  @Test
  @DisplayName("given provider 오류 when 생성 then AcademyAiGenerationException + Academy는 건드리지 않는다")
  void generate_surfaces_provider_error() {
    provider.failWith("네트워크 오류");
    assertThatThrownBy(() -> service.generate(principal, "principalDesc", "answer", ""))
        .isInstanceOf(AcademyAiGenerationException.class)
        .hasMessageContaining("네트워크 오류");
  }

  @Test
  @DisplayName("given 응답이 JSON 배열이 아님(깨진 응답) when 생성 then 후보 0개 → AcademyAiGenerationException")
  void generate_rejects_malformed_response() {
    provider.respondWith("죄송합니다. 추천을 만들 수 없습니다.");
    assertThatThrownBy(() -> service.generate(principal, "principalDesc", "answer", ""))
        .isInstanceOf(AcademyAiGenerationException.class);
  }

  @Test
  @DisplayName("given 빈 배열 응답 when 생성 then AcademyAiGenerationException(빈 결과를 성공으로 돌려주지 않는다)")
  void generate_rejects_empty_array() {
    provider.respondWith("[]");
    assertThatThrownBy(() -> service.generate(principal, "principalDesc", "answer", ""))
        .isInstanceOf(AcademyAiGenerationException.class);
  }

  @Test
  @DisplayName("given 카드형 target에 지나치게 긴 후보가 섞임 when 생성 then 긴 후보만 drop하고 짧은 후보만 남긴다")
  void generate_drops_oversized_short_candidates() {
    provider.respondWith("[\"짧은 슬로건\",\"" + "가".repeat(120) + "\"]");
    AcademyAiGuideSuggestion result = service.generate(principal, "philosophyItem1", "answer", "");
    assertThat(result.candidates()).containsExactly("짧은 슬로건");
  }

  @Test
  @DisplayName("given 문단형 target(principalDesc) when 생성 then 카드형보다 긴 후보도 허용한다")
  void generate_allows_longer_paragraph_for_long_targets() {
    String paragraph = "가".repeat(180);
    provider.respondWith("[\"" + paragraph + "\"]");
    AcademyAiGuideSuggestion result = service.generate(principal, "principalDesc", "answer", "");
    assertThat(result.candidates()).containsExactly(paragraph);
  }

  @Test
  @DisplayName("given 중복 후보 when 생성 then 대소문자 무시 중복은 제거한다")
  void generate_dedupes_candidates() {
    provider.respondWith("[\"몰입하는 수업\",\"몰입하는 수업\",\"성장하는 수업\"]");
    AcademyAiGuideSuggestion result = service.generate(principal, "introHeading", "answer", "");
    assertThat(result.candidates()).containsExactly("몰입하는 수업", "성장하는 수업");
  }

  @Test
  @DisplayName("given 답변/스타일 when 생성 then 두 값이 promptBuilder instructions 인자에 함께 실린다")
  void generate_passes_answer_and_style_to_prompt() {
    provider.respondWith("[\"후보1\"]");
    service.generate(principal, "principalDesc", "학교별 내신 분석", "전문적이지만 친근하게");

    org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(promptBuilder).build(any(), any(), any(), any(), captor.capture());
    assertThat(captor.getValue()).contains("학교별 내신 분석").contains("전문적이지만 친근하게");
  }
}
