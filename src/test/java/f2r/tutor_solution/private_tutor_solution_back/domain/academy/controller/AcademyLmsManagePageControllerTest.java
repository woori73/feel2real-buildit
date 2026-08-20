package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 학원 LMS 운영자 화면 2개.
 *
 * <p>이 클래스의 <b>클래스 불변식</b>(모든 handler가 소유권 가드로 시작한다)을 테스트로 못 박는다.
 * 필터는 꺼져 있으므로({@code addFilters = false}) 여기서 통과/차단을 결정하는 건 컨트롤러의
 * {@code requireOwnedAcademyByHandle} 호출 하나뿐이다 — 그 한 줄을 빠뜨리면 여기가 red가 된다.
 */
@WebMvcTest(controllers = AcademyLmsManagePageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AcademyLmsShellModel.class)
@DisplayName("AcademyLmsManagePageController")
class AcademyLmsManagePageControllerTest {

  private static final String HANDLE = "edu-holic";
  private static final String BASE = "/lms/" + HANDLE;

  @Autowired MockMvc mockMvc;
  @MockitoBean AcademyService academyService;
  @MockitoBean AuthenticatedUserResolver authResolver;

  @BeforeEach
  void setUp() {
    when(academyService.requireOwnedAcademyByHandle(HANDLE)).thenReturn(academy());
    when(authResolver.currentPrincipal()).thenReturn(Optional.empty());
  }

  @Test
  @DisplayName("강좌관리: 소유권 가드를 통과하면 관리 뷰를 렌더한다")
  void manage_rendersManageViewForOwner() throws Exception {
    // given: 소유자
    // when & then
    mockMvc.perform(get(BASE + "/manage"))
        .andExpect(status().isOk())
        .andExpect(view().name("lms/lms_manage"));
    verify(academyService).requireOwnedAcademyByHandle(HANDLE);
  }

  @Test
  @DisplayName("콘텐츠 관리: 소유권 가드를 통과하면 콘텐츠 뷰를 렌더한다")
  void contents_rendersContentsViewForOwner() throws Exception {
    // given: 소유자
    // when & then
    mockMvc.perform(get(BASE + "/contents"))
        .andExpect(status().isOk())
        .andExpect(view().name("lms/lms_contents"));
    verify(academyService).requireOwnedAcademyByHandle(HANDLE);
  }

  @Test
  @DisplayName("소유자가 아니면 403이고 화면은 렌더되지 않는다")
  void manage_isForbiddenForNonOwner() throws Exception {
    // given: 다른 학원의 TUTOR
    when(academyService.requireOwnedAcademyByHandle(HANDLE))
        .thenThrow(new AccessDeniedException("자기 Academy만 수정할 수 있습니다."));

    // when & then
    mockMvc.perform(get(BASE + "/manage")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("존재하지 않는 handle이면 404")
  void manage_isNotFoundForUnknownHandle() throws Exception {
    // given
    when(academyService.requireOwnedAcademyByHandle("ghost"))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Academy를 찾을 수 없습니다."));

    // when & then
    mockMvc.perform(get("/lms/ghost/manage")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("운영자 화면은 공개 조회를 쓰지 않는다 — 미발행 학원도 소유자는 관리해야 한다")
  void ownerScreens_doNotUsePublishedLookup() throws Exception {
    // given / when
    mockMvc.perform(get(BASE + "/manage"));

    // then
    verify(academyService, never()).getPublishedEntityByHandle(HANDLE);
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
