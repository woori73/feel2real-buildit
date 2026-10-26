package f2r.tutor_solution.private_tutor_solution_back.config.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioAccessGuard;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = StudioAccessSecurityIntegrationTest.ProbeController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class, StudioAccessGuard.class,
    StudioAccessSecurityIntegrationTest.ProbeController.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("AI AGENT 제작소 서버 접근 경계 — ADMIN·modu만")
class StudioAccessSecurityIntegrationTest {

  private static final long MODU_ID = 42L;

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean AcademyRepository academyRepository;
  @MockitoBean UserRepository userRepository;

  @RestController
  static class ProbeController {
    private final StudioAccessGuard guard;

    ProbeController(StudioAccessGuard guard) {
      this.guard = guard;
    }

    @RequestMapping({"/ai-agent-studio", "/studio-projects", "/api/studio/**"})
    String studio() {
      return "studio";
    }

    // StudioController.access와 같은 판정(가드 그대로 위임)
    @GetMapping("/api/studio-access")
    Map<String, Object> access(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal p) {
      return Map.of("allowed", guard.isAllowed(p));
    }
  }

  /** 제작소 컨트롤러의 실제 경로 전부(화면·조회·파일 다운로드·프로젝트·MCP 이미지). */
  static Stream<String> studioPaths() {
    return Stream.of(
        "/ai-agent-studio", "/studio-projects",
        "/api/studio/project", "/api/studio/projects", "/api/studio/project-capabilities",
        "/api/studio/project-state", "/api/studio/source-import", "/api/studio/cuts",
        "/api/studio/revisions", "/api/studio/revisions/r1", "/api/studio/files",
        "/api/studio/files/f1", "/api/studio/files/f1/content", "/api/studio/files/f1/document",
        "/api/studio/files/f1/preview", "/api/studio/files/f1/text", "/api/studio/files/f1/entries",
        "/api/studio/files/f1/mcp-image", "/api/studio/compare", "/api/studio/events",
        "/api/studio/memory", "/api/studio/members", "/api/studio/users", "/api/studio/pending",
        "/api/studio/invitations", "/api/studio/notifications");
  }

  @BeforeEach
  void setUp() {
    User modu = User.builder().loginId("modu").password("x").name("modu").nickname("modu").role(Role.TUTOR).build();
    ReflectionTestUtils.setField(modu, "userId", MODU_ID);
    when(userRepository.findByLoginId("modu")).thenReturn(Optional.of(modu));
  }

  private Cookie token(long userId, String loginId, Role role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(userId, loginId, role.name()));
  }

  @ParameterizedTest
  @MethodSource("studioPaths")
  @DisplayName("ADMIN·modu는 모든 제작소 경로를 통과한다")
  void givenAdminOrModu_whenRequestingStudio_thenAllowed(String path) throws Exception {
    // given
    Cookie admin = token(1L, "admin", Role.ADMIN);
    Cookie modu = token(MODU_ID, "modu", Role.TUTOR);
    // when / then
    mockMvc.perform(get(path).cookie(admin)).andExpect(status().isOk()).andExpect(content().string("studio"));
    mockMvc.perform(get(path).cookie(modu)).andExpect(status().isOk()).andExpect(content().string("studio"));
  }

  @ParameterizedTest
  @MethodSource("studioPaths")
  @DisplayName("일반 회원·다른 학원 강사·CREATOR·loginId 위장은 URL을 직접 입력해도 403")
  void givenOtherMembers_whenRequestingStudio_thenForbidden(String path) throws Exception {
    // given
    Cookie[] others = {
      token(10L, "student", Role.STUDENT),
      token(11L, "other-academy-tutor", Role.TUTOR),
      token(12L, "creator", Role.CREATOR),
      token(77L, "modu", Role.TUTOR)
    };
    // when / then
    for (Cookie c : others) mockMvc.perform(get(path).cookie(c)).andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @MethodSource("studioPaths")
  @DisplayName("비로그인 API는 401, 화면은 로그인 안내로 이동")
  void givenGuest_whenRequestingStudio_thenUnauthenticated(String path) throws Exception {
    // when / then
    if (path.startsWith("/api/")) {
      mockMvc.perform(get(path)).andExpect(status().isUnauthorized())
          .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
    } else {
      mockMvc.perform(get(path).accept(MediaType.TEXT_HTML))
          .andExpect(status().is3xxRedirection())
          .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
          .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/?login=required")));
    }
  }

  @Test
  @DisplayName("쓰기 요청도 일반 회원은 보안 단계에서 403")
  void givenStudent_whenPostingStudioWrites_thenForbidden() throws Exception {
    // given
    Cookie student = token(10L, "student", Role.STUDENT);
    // when / then
    for (String path : new String[] {"/api/studio/project", "/api/studio/files", "/api/studio/revisions/r1/review",
        "/api/studio/projects", "/api/studio/imports/ep01", "/api/studio/members"})
      mockMvc.perform(post(path).cookie(student)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("링크 표시용 /api/studio-access는 본인 여부만 200으로 알려 준다")
  void givenEachUser_whenAskingAccess_thenOwnFlagOnly() throws Exception {
    // when / then
    mockMvc.perform(get("/api/studio-access").cookie(token(1L, "admin", Role.ADMIN)))
        .andExpect(status().isOk()).andExpect(content().json("{\"allowed\":true}"));
    mockMvc.perform(get("/api/studio-access").cookie(token(MODU_ID, "modu", Role.TUTOR)))
        .andExpect(status().isOk()).andExpect(content().json("{\"allowed\":true}"));
    mockMvc.perform(get("/api/studio-access").cookie(token(10L, "student", Role.STUDENT)))
        .andExpect(status().isOk()).andExpect(content().json("{\"allowed\":false}"));
    mockMvc.perform(get("/api/studio-access")).andExpect(status().isUnauthorized());
  }
}
