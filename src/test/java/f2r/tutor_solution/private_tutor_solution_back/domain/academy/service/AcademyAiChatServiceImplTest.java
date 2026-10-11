package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ChatProposal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.FieldChange;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ValidationResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AcademyAiChatServiceImpl 테스트.
 *
 * <p>given/when/then 구조.
 */
@DisplayName("AcademyAiChatServiceImpl — 자연어 의도 분석 + 수정안 생성 + 검증")
class AcademyAiChatServiceImplTest {

  private FakeAiProvider provider;
  private AcademyAiChatServiceImpl service;
  private final AcademyAiContext fixtureContext = new AcademyAiContext(
      "우리학원", "기존 소개문", List.of("영어"), List.of("MIDDLE"),
      "김원장", "대표원장", "기존 원장 소개", List.of("철학1", "철학2", "", ""), List.of(), null);

  @BeforeEach
  void setUp() {
    provider = new FakeAiProvider();
    AcademyAiContextService contextService =
        org.mockito.Mockito.mock(AcademyAiContextService.class);
    service = new AcademyAiChatServiceImpl(provider, contextService);
  }

  @Test
  @DisplayName("given 수강료 변경 요청 when 제안 생성 then monthlyTuition 필드가 포함된다")
  void proposeChanges_tuitionRequest_mapsToMonthlyTuition() {
    ChatProposal proposal = service.proposeChanges(fixtureContext, "수강료를 15만원으로 바꿔줘");

    assertThat(proposal.changes()).isNotEmpty();
    assertThat(proposal.changes().stream().map(FieldChange::key))
        .contains("monthlyTuition");
  }

  @Test
  @DisplayName("given 여러 항목 요청 when 제안 생성 then 여러 필드가 포함된다")
  void proposeChanges_multiFieldRequest_mapsMultipleFields() {
    ChatProposal proposal = service.proposeChanges(
        fixtureContext, "수강료를 15만원으로 바꾸고 원장 소개도 다듬어줘");

    assertThat(proposal.changes().stream().map(FieldChange::key))
        .contains("monthlyTuition", "principalDesc");
  }

  @Test
  @DisplayName("given 약력 요청 when 제안 생성 then principalCareer 단일 키로 매핑된다")
  void proposeChanges_careerRequest_mapsToSingleCareerKey() {
    ChatProposal proposal = service.proposeChanges(
        fixtureContext, "원장 약력에 수상 경력을 추가해줘");

    assertThat(proposal.changes().stream().map(FieldChange::key))
        .contains("principalCareer");
    assertThat(proposal.changes().stream().map(FieldChange::key))
        .doesNotContain("principalCareer1", "principalCareer2", "principalCareer3");
  }

  @Test
  @DisplayName("given 지원하지 않는 요청 when 제안 생성 then 안내 메시지를 반환한다")
  void proposeChanges_unsupportedRequest_returnsGuidance() {
    ChatProposal proposal = service.proposeChanges(fixtureContext, "날씨 알려줘");

    assertThat(proposal.changes()).isEmpty();
    assertThat(proposal.summary()).contains("예시");
  }

  @Test
  @DisplayName("given 허용되지 않은 필드 when 검증 then 실패한다")
  void validateProposal_forbiddenField_fails() {
    ChatProposal proposal = new ChatProposal(
        List.of(new FieldChange("designPreset", "EXAM", "ARTS")),
        "test", "session1");

    ValidationResult result = service.validateProposal(proposal);

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anyMatch(e -> e.contains("designPreset"));
  }

  @Test
  @DisplayName("given 스크립트 포함 값 when 검증 then 실패한다")
  void validateProposal_scriptInjection_fails() {
    ChatProposal proposal = new ChatProposal(
        List.of(new FieldChange("intro", "기존", "<script>alert('xss')</script>")),
        "test", "session1");

    ValidationResult result = service.validateProposal(proposal);

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anyMatch(e -> e.contains("위험한 콘텐츠"));
  }

  @Test
  @DisplayName("given 잘못된 색상값 when 검증 then 실패한다")
  void validateProposal_invalidColor_fails() {
    // 색상 필드는 allowlist에서 제거됨 (P0 프리셋 시스템 경유)
    // 이제 brandPrimary는 허용되지 않은 필드로 거부됨
    ChatProposal proposal = new ChatProposal(
        List.of(new FieldChange("brandPrimary", "#000000", "not-a-color")),
        "test", "session1");

    ValidationResult result = service.validateProposal(proposal);

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anyMatch(e -> e.contains("brandPrimary"));
  }

  @Test
  @DisplayName("given 빈 수정안 when 검증 then 실패한다")
  void validateProposal_emptyProposal_fails() {
    ChatProposal proposal = new ChatProposal(List.of(), "test", "session1");

    ValidationResult result = service.validateProposal(proposal);

    assertThat(result.valid()).isFalse();
  }

  /** 테스트용 Fake AI Provider. */
  static class FakeAiProvider implements AiProvider {
    private String response = "생성된 텍스트입니다.";

    @Override
    public AiCompletion complete(String prompt, AiTaskLimits limits) {
      return AiCompletion.ok(response);
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public String name() {
      return "fake";
    }
  }
}
