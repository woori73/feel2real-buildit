package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("AcademyAdminBuilderPageController — 관리자 홈페이지 관리 화면")
class AcademyAdminBuilderPageControllerTest {

  private final AcademyRepository academies = mock(AcademyRepository.class);
  private final AcademyAdminBuilderPageController controller = new AcademyAdminBuilderPageController(academies);

  @Test
  @DisplayName("given 존재하는 학원, when 화면을 열면, then 대상 academyId를 모델에 담고 캐시·색인을 막는다")
  void page_existingAcademy() {
    when(academies.existsById(7L)).thenReturn(true);
    ConcurrentModel model = new ConcurrentModel();
    MockHttpServletResponse response = new MockHttpServletResponse();

    String view = controller.page(7L, response, model);

    assertThat(view).isEqualTo("admin/academy_manage");
    assertThat(model.getAttribute("academyId")).isEqualTo(7L);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(response.getHeader("X-Robots-Tag")).contains("noindex");
  }

  @Test
  @DisplayName("given 없는 학원, when 화면을 열면, then 404")
  void page_missingAcademy() {
    when(academies.existsById(9L)).thenReturn(false);

    assertThatThrownBy(() -> controller.page(9L, new MockHttpServletResponse(), new ConcurrentModel()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
}
