package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

@DisplayName("시각 편집기 진입 — 홈페이지가 없으면 빌더로")
class AcademyEditorControllerTest {

  private final AcademyService academyService = mock(AcademyService.class);
  private final AcademyEditorController controller = new AcademyEditorController(academyService);
  private final AuthenticatedPrincipal principal = new AuthenticatedPrincipal(10L, "tutor", "TUTOR");

  @Test
  @DisplayName("given 홈페이지가 없는 사용자 when 편집기 진입 then /builder로 redirect")
  void editor_noAcademy_redirectsToBuilder() {
    // given
    when(academyService.getMine(principal)).thenReturn(Optional.empty());
    Model model = new ExtendedModelMap();

    // when
    String view = controller.editor(principal, null, model);

    // then
    assertThat(view).isEqualTo("redirect:/builder");
    assertThat(model.asMap()).isEmpty();
  }

  @Test
  @DisplayName("given 홈페이지가 있는 사용자 when 편집기 진입 then 기존대로 편집기 화면과 학원 정보")
  void editor_withAcademy_rendersEditor() {
    // given
    AcademyResponse academy = new AcademyResponse(
        1L, "my-academy", "academy-abc123", "테스트학원", "소개", AcademyStatus.PUBLISHED, true,
        "/api/academies/my-academy/logo", null, "/academies/my-academy",
        List.of(), Set.of(), List.of(), List.of(), List.of(),
        new AcademyPageSettings(null, null, null, java.util.Map.of()));
    when(academyService.getMine(principal)).thenReturn(Optional.of(academy));
    Model model = new ExtendedModelMap();

    // when
    String view = controller.editor(principal, null, model);

    // then
    assertThat(view).isEqualTo("tutor/academy_editor");
    assertThat(model.getAttribute("academyName")).isEqualTo("테스트학원");
    assertThat(model.getAttribute("academySlug")).isEqualTo("my-academy");
  }
}
