package f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.service.LearningMaterialService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@DisplayName("LearningMaterialController.viewProblem — 문제 파일 inline 미리보기")
class LearningMaterialControllerTest {

  private final LearningMaterialService service = mock(LearningMaterialService.class);
  private final AuthenticatedUserResolver authResolver = mock(AuthenticatedUserResolver.class);
  private final LearningMaterialController controller =
      new LearningMaterialController(service, authResolver);

  @Test
  @DisplayName("given 권한 있는 사용자 when /view then 200 + inline + application/pdf + max-age=300 + bytes")
  void view_returns_inline_pdf_when_authorized() {
    // given
    long materialId = 7L;
    byte[] pdf = new byte[] {0x25, 0x50, 0x44, 0x46}; // %PDF
    when(service.getProblemFileBundle(materialId))
        .thenReturn(new LearningMaterialService.ProblemFileBundle(pdf, "application/pdf", "learning-material-7.pdf"));

    // when
    ResponseEntity<byte[]> res = controller.viewProblem(materialId);

    // then
    assertThat(res.getStatusCode().value()).isEqualTo(200);
    assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("inline;")
        .contains("learning-material-7.pdf");
    assertThat(res.getHeaders().getCacheControl()).isEqualTo("private, max-age=300");
    assertThat(res.getBody()).isEqualTo(pdf);
  }

  @Test
  @DisplayName("given 권한 없는 사용자 when /view then service 가드 예외 전파(거부)")
  void view_propagates_guard_rejection_when_unauthorized() {
    // given — ownership 가드 위반 시 service.getProblemFileBundle가 예외를 던진다
    long materialId = 99L;
    when(service.getProblemFileBundle(materialId))
        .thenThrow(new IllegalStateException("권한 없음"));

    // when / then
    assertThatThrownBy(() -> controller.viewProblem(materialId))
        .isInstanceOf(IllegalStateException.class);
  }
}
