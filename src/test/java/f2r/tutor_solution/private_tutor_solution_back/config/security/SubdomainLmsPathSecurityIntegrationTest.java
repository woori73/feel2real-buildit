package f2r.tutor_solution.private_tutor_solution_back.config.security;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller.AcademyLmsManagePageController;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * prd 서브도메인({@code {handle}.{base-domain}/lms/...})의 경로 정규화가 <b>인가보다 먼저</b>
 * 적용되는지 못 박는다.
 *
 * <p>이 스위트가 존재하는 이유: {@code SubdomainAcademyFilter}가 경로를 바꾸는 방식이 잘못되면
 * (forward를 쓰거나, {@code ServletRequestPathUtils} 캐시를 갱신하지 않으면) <b>인가가 조용히
 * 원본 경로 기준으로 평가된다.</b> 에러도 로그도 남지 않고 화면이 그냥 열린다.
 * {@code lmsAnonymousMyClassIsNotOpen}(T-SUB1)이 이 계획 전체에서 가장 중요한 단일 테스트다.
 */
@WebMvcTest(controllers = {AcademyLmsPageController.class, AcademyLmsManagePageController.class})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, AcademyLmsShellModel.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.public.base-domain=feel2real.co.kr",
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("서브도메인 LMS 경로 정규화 + 인가")
class SubdomainLmsPathSecurityIntegrationTest {

  private static final String HANDLE = "edu-holic";
  private static final String SUBDOMAIN_HOST = HANDLE + ".feel2real.co.kr";
  private static final String APEX_HOST = "feel2real.co.kr";

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

  @Test
  @DisplayName("T-SUB1 서브도메인 익명 /lms/my는 열리지 않는다 — 200이면 인가 우회 재발")
  void lmsAnonymousMyClassIsNotOpen() throws Exception {
    // given: 정규화가 인가 전에 안 걸리면 매처가 /lms/my를 못 잡고 catch-all permitAll로 샌다
    // when & then
    mockMvc.perform(get("/lms/my").with(host(SUBDOMAIN_HOST))
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/?login=required&returnUrl=%2Flms%2F")));
  }

  @Test
  @DisplayName("T-SUB2 서브도메인 익명 /lms/cart도 열리지 않는다")
  void lmsAnonymousCartIsNotOpen() throws Exception {
    // given / when & then
    mockMvc.perform(get("/lms/cart").with(host(SUBDOMAIN_HOST))
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/?login=required&returnUrl=%2Flms%2F")));
  }

  @Test
  @DisplayName("T-SUB3 서브도메인 학생 /lms/manage는 403 — owner 가드가 정규화 경로에서도 동작한다")
  void lmsStudentManageIsForbidden() throws Exception {
    // given
    Cookie token = new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "student", "STUDENT"));

    // when & then
    mockMvc.perform(get("/lms/manage").with(host(SUBDOMAIN_HOST)).cookie(token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("T-SUB4 서브도메인 익명 /lms(강좌목록)는 열려 있어야 한다")
  void lmsAnonymousCourseListIsOpen() throws Exception {
    // given: 정규화 결과 /lms/edu-holic → permitAll → 컨트롤러 도달
    // when & then
    mockMvc.perform(get("/lms").with(host(SUBDOMAIN_HOST)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("T-SUB5 서브도메인 /lms/*는 apex로 301되지 않는다 — 301이면 LMS가 한 화면도 안 뜬다")
  void lmsPathIsNotRedirectedToApex() throws Exception {
    // given: LMS를 정규화 대상으로 인식하지 못하면 필터가 공통 경로로 보고 apex로 301한다
    //        (그 목적지 /lms/my는 apex에 존재하지 않아 결국 아무 화면도 안 뜬다)
    // when & then
    mockMvc.perform(get("/lms").with(host(SUBDOMAIN_HOST)))
        .andExpect(result -> assertThat(result.getResponse().getStatus())
            .as("apex 301이면 LMS가 통째로 깨진다")
            .isNotEqualTo(301));
  }

  @Test
  @DisplayName("T-SUB6 apex에서도 로컬 형태(/lms/{handle}/my)가 동일하게 동작한다")
  void apexLocalFormBehavesTheSame() throws Exception {
    // given: apex는 정규화 대상이 아니다(handle이 경로에 이미 있다)
    // when & then
    mockMvc.perform(get("/lms/" + HANDLE + "/my").with(host(APEX_HOST))
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/?login=required&returnUrl=%2Flms%2F")));
  }

  @Test
  @DisplayName("T-SUB7 서브도메인의 비-LMS API는 302가 아니라 401을 유지한다")
  void nonLmsApiKeeps401OnSubdomain() throws Exception {
    // given: entry point 분기가 LMS 밖 경로를 오염시키지 않아야 한다
    // when & then
    mockMvc.perform(get("/api/conversations").with(host(SUBDOMAIN_HOST))
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
        .andExpect(status().isUnauthorized());
  }

  private static RequestPostProcessor host(String serverName) {
    return request -> {
      request.setServerName(serverName);
      return request;
    };
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
