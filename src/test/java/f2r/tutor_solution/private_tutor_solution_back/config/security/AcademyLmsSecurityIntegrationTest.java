package f2r.tutor_solution.private_tutor_solution_back.config.security;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller.AcademyLmsManagePageController;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller.AcademyLmsManageApiController;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller.AcademyLmsPageController;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller.AcademyLmsShellModel;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 학원 LMS 권한 매트릭스 8행 + fail-closed 회귀 방지.
 *
 * <p>경로는 정규화된 로컬 형태({@code /lms/{handle}/...})다. prd 서브도메인 형태는
 * {@code SubdomainLmsPathSecurityIntegrationTest}가 따로 검증한다.
 */
@WebMvcTest(controllers = {
    AcademyLmsPageController.class, AcademyLmsManagePageController.class,
    AcademyLmsManageApiController.class
})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, AcademyLmsShellModel.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("학원 LMS 화면 권한 매트릭스")
class AcademyLmsSecurityIntegrationTest {

  private static final String HANDLE = "edu-holic";
  private static final String BASE = "/lms/" + HANDLE;

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean AcademyService academyService;
  @MockitoBean AuthenticatedUserResolver authResolver;
  @MockitoBean AcademyRepository academyRepository;
  // LMS 셸 모델이 요구하는 의존성(이 슬라이스 테스트가 컨텍스트를 올리려면 필요)
  @MockitoBean f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository midtermCampaignRepository;

  @BeforeEach
  void setUp() {
    when(academyService.getPublishedEntityByHandle(HANDLE)).thenReturn(academy());
    when(academyService.requireOwnedAcademyByHandle(HANDLE)).thenReturn(academy());
    when(authResolver.currentPrincipal()).thenReturn(Optional.empty());
  }

  /* ── 1~2행: 공개 화면 ─────────────────────────────────────────── */

  @Test
  @DisplayName("AC-1 익명도 강좌목록을 볼 수 있다")
  void anonymousCanOpenCourseList() throws Exception {
    // given: 공개 화면 — permitAll
    // when & then
    mockMvc.perform(get(BASE)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("AC-1 익명도 강좌상세를 볼 수 있다")
  void anonymousCanOpenCourseDetail() throws Exception {
    // given / when & then
    mockMvc.perform(get(BASE + "/courses/1")).andExpect(status().isOk());
  }

  /* ── 3~6행: 인증 필요 화면 ────────────────────────────────────── */

  @Test
  @DisplayName("AC-2 비로그인 브라우저 요청은 401이 아니라 랜딩(\"/\")으로 302된다")
  void anonymousHtmlRequestRedirectsToLanding() throws Exception {
    // given: 주소창 top-level GET — 빈 401을 주면 흰 에러 페이지를 보게 된다
    //        목적지는 로그아웃과 같은 "/"다(비로그인 목적지 통일)
    // when & then
    mockMvc.perform(get(BASE + "/my").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/?login=required&returnUrl=%2Flms%2F")));
  }

  @Test
  @DisplayName("AC-3 비로그인 JSON 요청은 기존 계약대로 401 — entry point 교체가 API를 오염시키지 않는다")
  void anonymousJsonRequestStays401() throws Exception {
    // given: fetch/XHR
    // when & then
    mockMvc.perform(get(BASE + "/my").header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("학생은 장바구니·결제·내 강의실·강의보기에 접근한다")
  void studentCanOpenAuthenticatedScreens() throws Exception {
    // given
    Cookie token = token("STUDENT");

    // when & then
    mockMvc.perform(get(BASE + "/cart").cookie(token)).andExpect(status().isOk());
    mockMvc.perform(get(BASE + "/checkout").cookie(token)).andExpect(status().isOk());
    mockMvc.perform(get(BASE + "/my").cookie(token)).andExpect(status().isOk());
    mockMvc.perform(get(BASE + "/lecture/1").cookie(token)).andExpect(status().isOk());
  }

  /* ── 7~8행: 운영자 전용 화면 ──────────────────────────────────── */

  @Test
  @DisplayName("AC-4 학생은 강좌관리·콘텐츠 관리에서 403 — 필터체인에서 끊긴다")
  void studentIsForbiddenFromOwnerScreens() throws Exception {
    // given
    Cookie token = token("STUDENT");

    // when & then
    mockMvc.perform(get(BASE + "/manage").cookie(token)).andExpect(status().isForbidden());
    mockMvc.perform(get(BASE + "/contents").cookie(token)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("AC-5 소유 강사는 강좌관리·콘텐츠 관리를 볼 수 있다")
  void ownerTutorCanOpenOwnerScreens() throws Exception {
    // given: requireOwnedAcademyByHandle가 통과시키는 상태
    Cookie token = token("TUTOR");

    // when & then
    mockMvc.perform(get(BASE + "/manage").cookie(token)).andExpect(status().isOk());
    mockMvc.perform(get(BASE + "/contents").cookie(token)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("AC-6 다른 학원의 강사는 403 — 이번 작업의 핵심 회귀 방지선")
  void otherAcademyTutorIsForbidden() throws Exception {
    // given: role은 TUTOR라 필터체인은 통과한다. 막는 건 컨트롤러의 소유권 가드뿐이다.
    when(academyService.requireOwnedAcademyByHandle(HANDLE))
        .thenThrow(new AccessDeniedException("자기 Academy만 수정할 수 있습니다."));
    Cookie token = token("TUTOR");

    // when & then
    mockMvc.perform(get(BASE + "/manage").cookie(token)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("AC-7 ADMIN도 운영자 화면에 접근할 수 없다")
  void adminIsForbiddenFromOwnerScreens() throws Exception {
    // given: hasRole("TUTOR") — ADMIN 우회 없음
    Cookie token = token("ADMIN");

    // when & then
    mockMvc.perform(get(BASE + "/manage").cookie(token)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Stage 2a manage API role matrix: anonymous/student/admin denied and owner tutor allowed")
  void manageApiRoleMatrix() throws Exception {
    // given
    when(academyService.listOwnedLmsLessonsByHandle(HANDLE)).thenReturn(java.util.List.of());

    // when & then
    mockMvc.perform(get("/api/tutor/lms/" + HANDLE + "/lessons"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/tutor/lms/" + HANDLE + "/lessons").cookie(token("STUDENT")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/tutor/lms/" + HANDLE + "/lessons").cookie(token("ADMIN")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/tutor/lms/" + HANDLE + "/lessons").cookie(token("TUTOR")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Stage 2a manage API keeps owner guard 403 and unknown handle 404")
  void manageApiOwnerAndHandleBoundary() throws Exception {
    // given
    when(academyService.listOwnedLmsLessonsByHandle("other"))
        .thenThrow(new AccessDeniedException("자기 Academy만 수정할 수 있습니다."));
    when(academyService.listOwnedLmsLessonsByHandle("ghost"))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Academy를 찾을 수 없습니다."));
    Cookie tutor = token("TUTOR");

    // when & then
    mockMvc.perform(get("/api/tutor/lms/other/lessons").cookie(tutor))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/tutor/lms/ghost/lessons").cookie(tutor))
        .andExpect(status().isNotFound());
  }

  /* ── 학원 상태 / 존재 여부 ────────────────────────────────────── */

  @Test
  @DisplayName("AC-8 존재하지 않는 handle의 공개 화면은 404")
  void unknownHandleReturnsNotFoundOnPublicScreen() throws Exception {
    // given
    when(academyService.getPublishedEntityByHandle("ghost"))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 Academy를 찾을 수 없습니다."));

    // when & then
    mockMvc.perform(get("/lms/ghost")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("AC-9 미발행 학원의 공개 화면은 404 — 발행 전 LMS가 공개로 새지 않는다")
  void unpublishedAcademyIsNotFoundOnPublicScreen() throws Exception {
    // given: 공개 조회가 미발행을 걸러낸다
    when(academyService.getPublishedEntityByHandle(HANDLE))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 Academy를 찾을 수 없습니다."));

    // when & then
    mockMvc.perform(get(BASE)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("AC-9 미발행 학원이어도 소유자는 운영 화면을 볼 수 있다")
  void unpublishedAcademyIsStillManageableByOwner() throws Exception {
    // given: owner 조회는 공개 필터를 쓰지 않는다
    Cookie token = token("TUTOR");

    // when & then
    mockMvc.perform(get(BASE + "/manage").cookie(token)).andExpect(status().isOk());
  }

  /* ── fail-closed ──────────────────────────────────────────────── */

  @Test
  @DisplayName("AC-11 매처에 없는 LMS 하위 경로는 fail-closed로 인증을 요구한다")
  void unlistedLmsPathIsFailClosed() throws Exception {
    // given: 매핑조차 없는 경로. permitAll catch-all로 새면 안 된다.
    // when & then: 익명 브라우저 요청은 302(=인증 요구)이지 200이 아니다
    mockMvc.perform(get(BASE + "/zzz").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/?login=required&returnUrl=%2Flms%2F")));
  }

  @Test
  @DisplayName("LMS 밖 경로의 익명 요청은 브라우저 Accept여도 401을 유지한다")
  void nonLmsPathKeeps401ForHtmlRequest() throws Exception {
    // given: entry point 분기가 LMS 경로에만 걸리는지 확인 (기존 API 계약 무회귀)
    // when & then
    mockMvc.perform(get("/api/conversations").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
        .andExpect(status().isUnauthorized());
  }

  private Cookie token(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "test-" + role.toLowerCase(), role));
  }

  private static Academy academy() {
    return Academy.builder()
        .tutor(User.builder().loginId("edu-holic").password("x").name("원장").role(Role.TUTOR).build())
        .slug("edu-holic-academy")
        .handle(HANDLE)
        .name("압구정에듀홀릭학원")
        .logoStorageKey("logo")
        .intro("소개")
        .status(AcademyStatus.PUBLISHED)
        .build();
  }
}
