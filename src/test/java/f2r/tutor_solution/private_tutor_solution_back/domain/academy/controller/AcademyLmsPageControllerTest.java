package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 학원 LMS 공개·학생 화면 6개의 view/model 계약.
 *
 * <p>인가는 {@code AcademyLmsSecurityIntegrationTest}가 담당하므로 여기선 필터를 끄고
 * ({@code addFilters = false}) 렌더 계약만 본다.
 */
@WebMvcTest(controllers = AcademyLmsPageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AcademyLmsShellModel.class)
@DisplayName("AcademyLmsPageController")
class AcademyLmsPageControllerTest {

  private static final String HANDLE = "edu-holic";
  private static final String BASE = "/lms/" + HANDLE;

  @Autowired MockMvc mockMvc;
  @MockitoBean AcademyService academyService;
  @MockitoBean AuthenticatedUserResolver authResolver;

  @BeforeEach
  void setUp() {
    when(academyService.getPublishedEntityByHandle(HANDLE)).thenReturn(academy());
    when(authResolver.currentPrincipal()).thenReturn(Optional.empty());
  }

  @Test
  @DisplayName("강좌목록: 공개 조회로 학원을 찾고 목록 뷰를 렌더한다")
  void courses_rendersCourseListView() throws Exception {
    // given: 공개 학원
    // when & then
    mockMvc.perform(get(BASE))
        .andExpect(status().isOk())
        .andExpect(view().name("lms/lms_courses"));
    verify(academyService).getPublishedEntityByHandle(HANDLE);
  }

  @Test
  @DisplayName("강좌상세: courseId를 모델에 그대로 통과시킨다 — 1단계는 존재 검증을 하지 않는다")
  void courseDetail_passesCourseIdThrough() throws Exception {
    // given: 데이터가 MOCK이라 서버는 courseId를 해석하지 않는다
    // when & then
    mockMvc.perform(get(BASE + "/courses/7"))
        .andExpect(status().isOk())
        .andExpect(view().name("lms/lms_course_detail"))
        .andExpect(model().attribute("courseId", 7L));
  }

  @Test
  @DisplayName("장바구니/결제/내 강의실/강의보기가 각자 뷰를 렌더한다")
  void authenticatedScreens_renderTheirOwnViews() throws Exception {
    // given / when & then
    mockMvc.perform(get(BASE + "/cart")).andExpect(view().name("lms/lms_cart"));
    mockMvc.perform(get(BASE + "/checkout")).andExpect(view().name("lms/lms_checkout"));
    mockMvc.perform(get(BASE + "/my")).andExpect(view().name("lms/lms_my"));
    mockMvc.perform(get(BASE + "/lecture/3"))
        .andExpect(view().name("lms/lms_lecture"))
        .andExpect(model().attribute("courseId", 3L));
  }

  @Test
  @DisplayName("셸 모델: 학원명·base path·인증상태·userKey를 내려준다")
  void shellModel_isPopulated() throws Exception {
    // given: 비로그인
    // when & then
    mockMvc.perform(get(BASE))
        .andExpect(model().attribute("academyName", "압구정에듀홀릭학원"))
        .andExpect(model().attribute("academyHandle", HANDLE))
        .andExpect(model().attribute("lmsBase", BASE))
        .andExpect(model().attribute("lmsAuthenticated", false))
        .andExpect(model().attribute("lmsUserKey", "anon"));
  }

  @Test
  @DisplayName("셸 모델: 로그인하면 userKey가 사용자별로 갈린다 — 장바구니 교차 노출 방지")
  void shellModel_userKeyIsPerUser() throws Exception {
    // given: 학생 로그인
    when(authResolver.currentPrincipal())
        .thenReturn(Optional.of(new JwtTokenProvider.AuthenticatedPrincipal(42L, "s1", "STUDENT")));

    // when & then
    mockMvc.perform(get(BASE))
        .andExpect(model().attribute("lmsAuthenticated", true))
        .andExpect(model().attribute("lmsUserKey", "u42"));
  }

  @Test
  @DisplayName("AC-14 비로그인에게는 사이드바에 강좌관리·콘텐츠 관리가 렌더되지 않는다")
  void sidebar_hidesOwnerMenuForAnonymous() throws Exception {
    // given: 비로그인
    // when & then
    mockMvc.perform(get(BASE))
        .andExpect(model().attribute("lmsIsOwner", false))
        .andExpect(content().string(not(containsString(BASE + "/manage"))))
        .andExpect(content().string(not(containsString(BASE + "/contents"))));
  }

  @Test
  @DisplayName("AC-14 다른 학원 강사에게도 관리 메뉴가 렌더되지 않는다 — role이 아니라 소유권으로 판정")
  void sidebar_hidesOwnerMenuForOtherAcademyTutor() throws Exception {
    // given: TUTOR지만 이 학원 소유자가 아니다
    when(authResolver.currentPrincipal())
        .thenReturn(Optional.of(new JwtTokenProvider.AuthenticatedPrincipal(99L, "other", "TUTOR")));

    // when & then
    mockMvc.perform(get(BASE))
        .andExpect(model().attribute("lmsIsOwner", false))
        .andExpect(content().string(not(containsString(BASE + "/manage"))));
  }

  @Test
  @DisplayName("소유 강사에게는 사이드바에 관리 메뉴가 렌더된다")
  void sidebar_showsOwnerMenuForOwner() throws Exception {
    // given: academy.tutor.userId == principal.userId
    when(authResolver.currentPrincipal())
        .thenReturn(Optional.of(new JwtTokenProvider.AuthenticatedPrincipal(10L, "edu-holic", "TUTOR")));

    // when & then
    mockMvc.perform(get(BASE))
        .andExpect(model().attribute("lmsIsOwner", true))
        .andExpect(content().string(containsString(BASE + "/manage")))
        .andExpect(content().string(containsString(BASE + "/contents")));
  }

  @Test
  @DisplayName("AC-19 헤더 학원명은 목업 하드코딩이 아니라 조회한 Academy.name이다")
  void header_rendersAcademyNameFromDatabase() throws Exception {
    // given / when & then
    mockMvc.perform(get(BASE))
        .andExpect(content().string(containsString("압구정에듀홀릭학원")))
        .andExpect(content().string(not(containsString("압구정 <span>에듀홀릭</span>"))));
  }

  private static Academy academy() {
    User tutor = User.builder()
        .loginId("edu-holic").password("x").name("원장").role(Role.TUTOR).build();
    ReflectionTestUtils.setField(tutor, "userId", 10L);
    return Academy.builder()
        .tutor(tutor)
        .slug("edu-holic-academy")
        .handle(HANDLE)
        .name("압구정에듀홀릭학원")
        .logoStorageKey("logo")
        .intro("소개")
        .status(AcademyStatus.PUBLISHED)
        .build();
  }
}
