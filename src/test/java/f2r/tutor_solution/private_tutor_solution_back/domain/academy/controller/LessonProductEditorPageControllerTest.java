package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.view.RedirectView;

@DisplayName("LessonProductEditorPageController — 편집기 진입 가드")
class LessonProductEditorPageControllerTest {

  private final LessonProductEditorPageController controller = new LessonProductEditorPageController();

  @Test
  @DisplayName("given TUTOR 로그인 when 진입 then 전용 편집기 뷰를 렌더한다")
  void editor_renders_for_tutor() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(7L, "tutor1", "TUTOR");

    // when
    Object view = controller.editor(principal);

    // then
    assertThat(view).isEqualTo("tutor/lesson_product_editor");
  }

  @Test
  @DisplayName("given 비로그인 직접 접근 when 진입 then 랜딩으로 리다이렉트")
  void editor_redirects_anonymous_user_to_landing() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal principal = null;

    // when
    Object view = controller.editor(principal);

    // then
    assertThat(view).isInstanceOfSatisfying(RedirectView.class, redirect ->
        assertThat(redirect.getUrl()).isEqualTo("/"));
  }

  @Test
  @DisplayName("given 학생 로그인 when 진입 then 랜딩으로 리다이렉트")
  void editor_redirects_student_to_landing() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(9L, "student", "STUDENT");

    // when
    Object view = controller.editor(principal);

    // then
    assertThat(view).isInstanceOfSatisfying(RedirectView.class, redirect ->
        assertThat(redirect.getUrl()).isEqualTo("/"));
  }

  @Test
  @DisplayName("given 어드민 로그인 when 진입 then /builder와 동일하게 랜딩으로 리다이렉트")
  void editor_redirects_admin_to_landing() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(1L, "admin", "ADMIN");

    // when
    Object view = controller.editor(principal);

    // then
    assertThat(view).isInstanceOfSatisfying(RedirectView.class, redirect ->
        assertThat(redirect.getUrl()).isEqualTo("/"));
  }
}
