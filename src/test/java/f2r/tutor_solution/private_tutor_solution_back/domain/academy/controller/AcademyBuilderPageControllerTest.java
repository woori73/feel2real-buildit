package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.view.RedirectView;

@DisplayName("AcademyBuilderPageController — 빌더 초기값 SSR")
class AcademyBuilderPageControllerTest {

  private final AcademyService academyService = mock(AcademyService.class);
  private final AcademyBuilderPageController controller = new AcademyBuilderPageController(academyService);

  @Test
  @DisplayName("builder — given 보유 Academy when 렌더 then 모델에 이름·slug 기반 표시 URL·공개 경로를 채운다")
  void builder_sets_existing_academy_model() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(7L, "test1234", "TUTOR");
    AcademyResponse academy = new AcademyResponse(
        1L,
        "이게되나",
        "academy-abc123",
        "테스트학원",
        "소개",
        AcademyStatus.PUBLISHED,
        true,
        "/api/academies/이게되나/logo",
        null,
        "/academies/이게되나",
        List.of(),
        Set.of(),
        List.of(),
        List.of(),
        List.of(),
        new AcademyPageSettings(null, null, null, java.util.Map.of("sec-payment", false)));
    when(academyService.getMine(principal)).thenReturn(Optional.of(academy));
    Model model = new ConcurrentModel();

    // when
    Object view = controller.builder(principal, model);

    // then
    assertThat(view).isEqualTo("tutor/academy_builder");
    assertThat(model.getAttribute("academyName")).isEqualTo("테스트학원");
    assertThat(model.getAttribute("academySlug")).isEqualTo("이게되나");
    assertThat(model.getAttribute("academyPathUrl")).isEqualTo("/academies/이게되나");
    assertThat(model.getAttribute("academyDisplayUrl")).isEqualTo("academy-abc123.feel2real.co.kr");
    assertThat(model.getAttribute("academySectionVisibilityJson")).isEqualTo("{\"sec-payment\":false}");
    assertThat((String) model.getAttribute("academyJson"))
        .contains("\"name\":\"테스트학원\"")
        .contains("\"intro\":\"소개\"")
        .contains("\"sectionVisibility\":{\"sec-payment\":false}");
  }

  @Test
  @DisplayName("builder — given 비로그인 직접 접근 when 진입 then 서비스 조회 없이 랜딩으로 리다이렉트")
  void builder_redirects_anonymous_user_to_landing() {
    // given
    Model model = new ConcurrentModel();

    // when
    Object view = controller.builder(null, model);

    // then
    assertThat(view).isInstanceOfSatisfying(RedirectView.class, redirect -> {
      assertThat(redirect.getUrl()).isEqualTo("/");
    });
    verifyNoInteractions(academyService);
  }

  @Test
  @DisplayName("builder — given 비강사 로그인 when 진입 then 서비스 조회 없이 랜딩으로 리다이렉트")
  void builder_redirects_non_tutor_to_landing() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(9L, "student", "STUDENT");
    Model model = new ConcurrentModel();

    // when
    Object view = controller.builder(principal, model);

    // then
    assertThat(view).isInstanceOfSatisfying(RedirectView.class, redirect -> {
      assertThat(redirect.getUrl()).isEqualTo("/");
    });
    verifyNoInteractions(academyService);
  }

}
