package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiCreateDtos.CreateDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AcademyAiCreateService 테스트.
 *
 * <p>given/when/then 구조.
 */
@DisplayName("AcademyAiCreateService — 자연어 홈페이지 초안 생성")
class AcademyAiCreateServiceTest {

  private FakeAiProvider provider;
  private AcademyAiCreateService service;

  @BeforeEach
  void setUp() {
    provider = new FakeAiProvider();
    service = new AcademyAiCreateService(provider, new AcademyRequirementAnalyzer(),
        org.mockito.Mockito.mock(f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository.class));
  }

  @Test
  @DisplayName("given 영어학원 요청 when 초안 생성 then ENGLISH 프리셋이 선택된다")
  void generateDraft_englishRequest_selectsEnglishPreset() {
    CreateDraft draft = service.generateDraft(
        "중학생 대상 영어학원 홈페이지 만들어 줘. 파란색 계열이고 상담 신청 버튼을 넣어 줘.",
        "테스트학원");

    assertThat(draft.designPreset()).isEqualTo("ENGLISH");
    // 색상은 P0 프리셋 시스템을 경유하므로 draft에 brandPrimary가 없음
    // 대신 warnings에 안내 메시지가 포함됨
    assertThat(draft.builderDraft()).doesNotContainKey("brandPrimary");
    assertThat(draft.warnings()).anyMatch(w -> w.contains("프리셋의 기본 색상"));
    assertThat(draft.builderDraft()).containsKey("ctaEnabled");
  }

  @Test
  @DisplayName("given 입시학원 요청 when 초안 생성 then EXAM 프리셋이 선택된다")
  void generateDraft_examRequest_selectsExamPreset() {
    CreateDraft draft = service.generateDraft(
        "고등학생 내신 대비 입시학원 홈페이지 만들어줘",
        "입시학원");

    assertThat(draft.designPreset()).isEqualTo("EXAM");
  }

  @Test
  @DisplayName("given 온라인학원 요청 when 초안 생성 then ONLINE 프리셋이 선택된다")
  void generateDraft_onlineRequest_selectsOnlinePreset() {
    CreateDraft draft = service.generateDraft(
        "온라인 인강 홈페이지 만들어줘",
        "온라인학원");

    assertThat(draft.designPreset()).isEqualTo("ONLINE");
  }

  @Test
  @DisplayName("given 빈 요청 when 초안 생성 then 예외가 발생한다")
  void generateDraft_emptyMessage_throwsException() {
    assertThatThrownBy(() -> service.generateDraft("", "학원"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 색상 미지정 when 초안 생성 then 기본 프리셋 색상을 사용한다")
  void generateDraft_noColor_usesDefaultPresetColor() {
    CreateDraft draft = service.generateDraft("미술학원 홈페이지 만들어줘", "미술학원");

    assertThat(draft.designPreset()).isEqualTo("ARTS");
    // 색상 미지정 시 brandPrimary 키가 없음 (프리셋 기본값 사용)
    assertThat(draft.builderDraft()).doesNotContainKey("brandPrimary");
  }

  /** 테스트용 Fake AI Provider. */
  static class FakeAiProvider implements AiProvider {
    @Override
    public AiCompletion complete(String prompt, AiTaskLimits limits) {
      return AiCompletion.ok("생성된 소개문입니다.");
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
