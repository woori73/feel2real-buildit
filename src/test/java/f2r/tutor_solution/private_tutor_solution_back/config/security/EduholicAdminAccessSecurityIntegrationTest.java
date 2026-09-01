package f2r.tutor_solution.private_tutor_solution_back.config.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller.AcademyController;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyPageLayoutService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVisitStatisticsService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.LessonCollectionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.LessonProductService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Eduholic(공개 학원 홈페이지)이 방문자 자기소유 여부를 판별하는
 * {@code GET /api/tutor/academy/me}에 실제 인증된 ADMIN principal도 401/403 없이 접근할 수 있는지
 * 못 박는다.
 *
 * <p>재현 배경: {@code academy_public.js}의 {@code isOwnAcademy()}가 이 엔드포인트를 호출하면,
 * 이 경로가 {@code /api/tutor/**}의 일부로 {@code hasRole("TUTOR")}에 걸려 있어 실제 로그인한
 * ADMIN 쿠키가 매번 403을 받았다 — 프론트가 이를 "접근 권한이 없습니다" alert + 대시보드 강제
 * 이동으로 처리해 admin이 Eduholic 공개 페이지 자체를 볼 수 없었다.
 *
 * <p>권한 판정은 전부 {@link JwtTokenProvider}가 서명 검증한 쿠키에서 나온 role만 쓴다 — 쿼리
 * 파라미터·헤더·바디의 role/loginId는 이 경로 어디에서도 읽지 않는다({@code ?role=ADMIN} 우회 불가).
 */
@WebMvcTest(controllers = AcademyController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("GET /api/tutor/academy/me — Eduholic admin 접근")
class EduholicAdminAccessSecurityIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean AcademyService academyService;
  @MockitoBean LessonProductService lessonProductService;
  @MockitoBean LessonCollectionService lessonCollectionService;
  @MockitoBean AcademyPageLayoutService academyPageLayoutService;
  @MockitoBean AcademyVisitStatisticsService academyVisitStatisticsService;

  @Test
  @DisplayName("서명된 ADMIN 쿠키는 200 — 소유 학원이 없으면 본문은 비어 있다(204)")
  void adminPrincipalIsNotBlocked() throws Exception {
    // given: 실제 authenticated principal(Spring이 서명 검증한 JWT)의 role=ADMIN.
    //        AcademyService.getMine()은 이 principal이 소유한 학원이 없으면 Optional.empty()를
    //        반환할 뿐이다 — admin에게 새 데이터나 쓰기 권한을 주지 않는다.
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "admin", "ADMIN"));
    when(academyService.getMine(any())).thenReturn(java.util.Optional.empty());

    // when & then
    mockMvc.perform(get("/api/tutor/academy/me").cookie(admin))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("기존 학원 소유 TUTOR 접근은 그대로 유지된다 — 회귀 없음")
  void tutorOwnerAccessIsUnaffected() throws Exception {
    // given
    Cookie tutor = new Cookie("ACCESS_TOKEN", tokenProvider.issue(2L, "edu-holic", "TUTOR"));
    AcademyResponse mine = new AcademyResponse(
        10L, "edu-holic-academy", "edu-holic", "압구정에듀홀릭학원", "소개",
        AcademyStatus.PUBLISHED, true, null, null, null,
        List.of(), Set.of(TargetGroup.MIDDLE), List.of(), List.of(), List.of(), null);
    when(academyService.getMine(any())).thenReturn(java.util.Optional.of(mine));

    // when & then
    mockMvc.perform(get("/api/tutor/academy/me").cookie(tutor))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("STUDENT는 여전히 이 엔드포인트에서 차단된다 — 다른 role로 확대하지 않았다")
  void studentIsStillForbidden() throws Exception {
    // given
    Cookie student = new Cookie("ACCESS_TOKEN", tokenProvider.issue(3L, "student1", "STUDENT"));

    // when & then
    mockMvc.perform(get("/api/tutor/academy/me").cookie(student))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("비로그인은 여전히 401")
  void anonymousIsStillUnauthorized() throws Exception {
    mockMvc.perform(get("/api/tutor/academy/me"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("?role=ADMIN 쿼리 파라미터는 STUDENT 쿠키를 승격시키지 못한다")
  void roleQueryParameterCannotEscalateStudent() throws Exception {
    // given: 서명된 쿠키의 실제 role은 STUDENT — 쿼리 파라미터는 서버 인가에 전혀 읽히지 않는다.
    Cookie student = new Cookie("ACCESS_TOKEN", tokenProvider.issue(4L, "student2", "STUDENT"));

    // when & then
    mockMvc.perform(get("/api/tutor/academy/me?role=ADMIN").cookie(student))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("다른 tutor 전용 쓰기 엔드포인트는 여전히 TUTOR 전용이다 — ADMIN까지 넓히지 않았다")
  void adminCannotWriteAcademyThroughTutorOnlyEndpoint() throws Exception {
    // given: /api/tutor/academy/me(GET) 한 경로만 열었다. 나머지 /api/tutor/**(쓰기 포함)는
    //        여전히 hasRole("TUTOR")만 통과한다 — admin 권한 확대 없음.
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "admin", "ADMIN"));

    // when & then
    mockMvc.perform(post("/api/tutor/academy")
            .cookie(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden());
  }
}
