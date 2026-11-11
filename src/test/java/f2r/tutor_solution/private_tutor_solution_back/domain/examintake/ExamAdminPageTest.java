package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

class ExamAdminPageTest {
  private final ExamIntakePageController controller = new ExamIntakePageController();

  @Test
  void givenAdmin_whenOpeningIntakes_thenUseDedicatedWorkspace() {
    var admin = mock(AuthenticatedPrincipal.class);
    when(admin.userId()).thenReturn(1L);
    when(admin.role()).thenReturn("ADMIN");
    Model model = mock(Model.class);

    assertThat(controller.adminPage(admin, model)).isEqualTo("admin/exam_requests");
    verify(model).addAttribute("adminView", true);
  }

  @Test
  void givenNonAdmin_whenOpeningIntakes_thenRejectAccess() {
    var student = mock(AuthenticatedPrincipal.class);
    when(student.userId()).thenReturn(2L);
    when(student.role()).thenReturn("STUDENT");

    assertThatThrownBy(() -> controller.adminPage(student, new ExtendedModelMap()))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
    assertThatThrownBy(() -> controller.adminPage(null, new ExtendedModelMap()))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
  }

  @Test
  void givenPublicSample_whenAnonymous_thenKeepExistingLoginReturnUrl() {
    assertThat(controller.page(null, new ExtendedModelMap(), "1"))
        .isEqualTo("redirect:/?login=required&returnUrl=%2Fexam-service%3Fsample%3D1");
  }
}
