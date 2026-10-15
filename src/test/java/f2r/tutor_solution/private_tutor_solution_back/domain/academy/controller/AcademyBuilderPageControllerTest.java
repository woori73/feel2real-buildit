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
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAssistantMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantAcademyMode;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantFeatureFlag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("AcademyBuilderPageController — 빌더 초기값 SSR")
class AcademyBuilderPageControllerTest {

  private final AcademyService academyService = mock(AcademyService.class);
  private final AcademyAssistantMessageRepository assistantMessages = mock(AcademyAssistantMessageRepository.class);
  private final AcademyBuilderPageController controller = controllerWith(false, "");

  private AcademyBuilderPageController controllerWith(boolean enabled, String loginIds) {
    return new AcademyBuilderPageController(
        academyService, new AssistantFeatureFlag(enabled, loginIds), new AssistantAcademyMode(assistantMessages));
  }

  private static JwtTokenProvider.AuthenticatedPrincipal tutor(String loginId) {
    return new JwtTokenProvider.AuthenticatedPrincipal(7L, loginId, "TUTOR");
  }

  private static AcademyResponse academy() {
    return new AcademyResponse(1L, "h", "slug", "학원", "소개", AcademyStatus.DRAFT, true, null, null,
        "/academies/slug", List.of(), Set.of(), List.of(), List.of(), List.of(),
        new AcademyPageSettings(null, null, null, java.util.Map.of()));
  }

  @Test
  @DisplayName("builder — given ADMIN when 스위치 꺼짐 then 기존 빌더, 켜짐+허용 계정 then 대화형 화면")
  void builder_admin_access() {
    JwtTokenProvider.AuthenticatedPrincipal admin =
        new JwtTokenProvider.AuthenticatedPrincipal(1L, "admin1", "ADMIN");
    when(academyService.getMine(admin)).thenReturn(Optional.empty());
    assertThat(controller.builder(admin, null, new ConcurrentModel(), new MockHttpServletResponse())).isEqualTo("tutor/academy_builder");
    assertThat(controllerWith(true, "admin1").builder(admin, null, new ConcurrentModel(), new MockHttpServletResponse()))
        .isEqualTo("tutor/assistant_home");
  }

  @Test
  @DisplayName("builder — given STUDENT when 스위치 켜짐 then 제작기가 아닌 안내 화면 (권한 확대 없음)")
  void builder_student_getsNoticeNotBuilder() {
    JwtTokenProvider.AuthenticatedPrincipal student =
        new JwtTokenProvider.AuthenticatedPrincipal(5L, "stu", "STUDENT");
    assertThat(controllerWith(true, "").builder(student, null, new ConcurrentModel(), new MockHttpServletResponse()))
        .isEqualTo("program/intro");
  }

  @Test
  @DisplayName("builder — given 스위치 꺼짐 when 신규 사용자 then 기존 빌더")
  void builder_flagOff_usesClassic() {
    when(academyService.getMine(tutor("a"))).thenReturn(Optional.empty());
    assertThat(controller.builder(tutor("a"), null, new ConcurrentModel(), new MockHttpServletResponse())).isEqualTo("tutor/academy_builder");
  }

  @Test
  @DisplayName("builder — given 스위치 켜짐 when 신규 사용자 then 대화형 화면")
  void builder_flagOn_newUser_usesAssistant() {
    when(academyService.getMine(tutor("a"))).thenReturn(Optional.empty());
    assertThat(controllerWith(true, "").builder(tutor("a"), null, new ConcurrentModel(), new MockHttpServletResponse()))
        .isEqualTo("tutor/assistant_home");
  }

  @Test
  @DisplayName("builder — given 허용 목록 when 목록 밖 계정 then 기존 빌더, 목록 안 계정 then 대화형")
  void builder_allowlist() {
    when(academyService.getMine(tutor("in"))).thenReturn(Optional.empty());
    when(academyService.getMine(tutor("out"))).thenReturn(Optional.empty());
    AcademyBuilderPageController c = controllerWith(true, "in, other");
    assertThat(c.builder(tutor("in"), null, new ConcurrentModel(), new MockHttpServletResponse())).isEqualTo("tutor/assistant_home");
    assertThat(c.builder(tutor("out"), null, new ConcurrentModel(), new MockHttpServletResponse())).isEqualTo("tutor/academy_builder");
  }

  @Test
  @DisplayName("builder — given 스위치 켜짐 when classic=1 then 기존 빌더로 복귀")
  void builder_classicParam_returnsToClassic() {
    when(academyService.getMine(tutor("a"))).thenReturn(Optional.empty());
    assertThat(controllerWith(true, "").builder(tutor("a"), "1", new ConcurrentModel(), new MockHttpServletResponse()))
        .isEqualTo("tutor/academy_builder");
  }

  @Test
  @DisplayName("builder — given 기존 방식 학원(대화 기록 없음) when 스위치 켜짐 then 기존 빌더(공개 데이터 보호)")
  void builder_legacyAcademy_staysClassic() {
    when(academyService.getMine(tutor("a"))).thenReturn(Optional.of(academy()));
    when(assistantMessages.existsByAcademyIdAndKindIn(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyCollection())).thenReturn(false);
    assertThat(controllerWith(true, "").builder(tutor("a"), null, new ConcurrentModel(), new MockHttpServletResponse()))
        .isEqualTo("tutor/academy_builder");
  }

  @Test
  @DisplayName("builder — given 대화로 만든 학원 when 스위치 켜짐 then 대화형 화면")
  void builder_assistantAcademy_usesAssistant() {
    when(academyService.getMine(tutor("a"))).thenReturn(Optional.of(academy()));
    when(assistantMessages.existsByAcademyIdAndKindIn(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyCollection())).thenReturn(true);
    assertThat(controllerWith(true, "").builder(tutor("a"), null, new ConcurrentModel(), new MockHttpServletResponse()))
        .isEqualTo("tutor/assistant_home");
  }

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
    Object view = controller.builder(principal, null, model, new MockHttpServletResponse());

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
  @DisplayName("builder — given 비로그인 직접 접근 when 진입 then 서비스 조회 없이 200 공개 소개 화면")
  void builder_shows_public_intro_to_anonymous_user() {
    // given
    Model model = new ConcurrentModel();
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    Object view = controller.builder(null, null, model, response);

    // then
    assertThat(view).isEqualTo("program/intro");
    assertThat(model.getAttribute("state")).isEqualTo("ANON");
    assertThat(response.getStatus()).isEqualTo(200);
    verifyNoInteractions(academyService);
  }

  @Test
  @DisplayName("builder — given 비강사 로그인 when 진입 then 서비스 조회 없이 403 안내 화면")
  void builder_shows_notice_to_non_tutor() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(9L, "student", "STUDENT");
    Model model = new ConcurrentModel();
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    Object view = controller.builder(principal, null, model, response);

    // then
    assertThat(view).isEqualTo("program/intro");
    assertThat(model.getAttribute("state")).isEqualTo("FORBIDDEN");
    assertThat(response.getStatus()).isEqualTo(403);
    verifyNoInteractions(academyService);
  }

}
