package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 수업 상품 전용 편집기 페이지.
 *
 * <p>뷰에는 SSR 데이터가 없고 수업 목록은 클라이언트가 {@code /api/tutor/academy/me}로 받는다.
 * 그래도 비강사가 편집기 셸을 열지 못하도록 {@code /builder}와 동일한 진입 가드를 둔다.
 * ADMIN도 차단 대상이다. {@code /api/tutor/**}가 {@code ROLE_TUTOR} 전용이라
 * 페이지만 열어줘도 빈 화면이 되기 때문이다.
 *
 * <p>{@code WebMvcConfig}의 view 등록은 {@code /builder}와 마찬가지로 남겨 둔다.
 * {@code RequestMappingHandlerMapping}(order 0)이 view controller(order 1)보다 먼저 매칭되므로
 * 실제 요청은 항상 이 컨트롤러를 지난다.
 */
@Controller
public class LessonProductEditorPageController {

  static final String VIEW = "tutor/lesson_product_editor";

  @GetMapping("/tutor/academy/lessons/editor")
  public Object editor(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (!hasTutorRole(principal)) {
      RedirectView redirect = new RedirectView("/");
      redirect.setExposeModelAttributes(false);
      return redirect;
    }
    return VIEW;
  }

  private boolean hasTutorRole(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return principal != null && "TUTOR".equals(principal.role());
  }
}
