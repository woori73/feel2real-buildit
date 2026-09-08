package f2r.tutor_solution.private_tutor_solution_back.config.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = WikiMemberOnlySecurityIntegrationTest.ProbeController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("Wiki 실제 콘텐츠 회원 전용 보안 경계")
class WikiMemberOnlySecurityIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean AcademyRepository academyRepository;

  static Stream<Arguments> protectedApis() {
    return Stream.of(
        Arguments.of("/api/wiki/originals", "original-list"),
        Arguments.of("/api/wiki/originals/1/download", "original-file"),
        Arguments.of("/api/wiki/analysis-materials", "analysis-list"),
        Arguments.of("/api/wiki/analysis-materials/1/download", "analysis-file"),
        Arguments.of("/api/wiki/learning-materials", "learning-list"),
        Arguments.of("/api/wiki/learning-materials/1", "learning-detail"),
        Arguments.of("/api/wiki/learning-materials/1/preview", "learning-preview"),
        Arguments.of("/api/wiki/learning-materials/1/download", "learning-file"),
        Arguments.of("/api/wiki/questions", "question-list"),
        Arguments.of("/api/wiki/questions/1", "question-detail"),
        Arguments.of("/api/wiki/questions/1/context", "thread-context"),
        Arguments.of("/api/wiki/questions/attachments/1/file", "thread-file"),
        Arguments.of("/api/wiki/questions/materials/1/preview", "question-material"),
        Arguments.of("/api/wiki/live-messages", "live-thread"),
        Arguments.of("/api/open-community/images/1", "wiki-shared-image"),
        Arguments.of("/api/open-community/attachments/1/file", "wiki-shared-file"),
        Arguments.of("/problem-generator/wiki/api/questions", "wiki-ai"));
  }

  private Cookie token(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "test-" + role.toLowerCase(), role));
  }

  @Test
  @DisplayName("GUEST /wiki 랜딩과 소개 메타 API만 접근 가능")
  void guest_canAccessLandingMetadataOnly() throws Exception {
    mockMvc.perform(get("/wiki")).andExpect(status().isOk()).andExpect(content().string("landing"));
    mockMvc.perform(get("/api/wiki/home-feed")).andExpect(status().isOk());
    mockMvc.perform(get("/api/wiki/popular-downloads")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("GUEST shared community API의 Wiki scope 목록은 401")
  void guest_wikiScopedCommunityListReturns401() throws Exception {
    mockMvc.perform(get("/api/open-community/posts").param("scope", "wiki"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GUEST 자료/문항 직접 URL은 본문 없이 로그인 안내로 이동")
  void guest_htmlContentRedirectsWithoutRenderingBody() throws Exception {
    mockMvc.perform(get("/wiki/questions/1")
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
        .andExpect(status().isFound())
        .andExpect(header().string("Location",
            "/?login=required&returnUrl=%2Fwiki%2Fquestions%2F1"))
        .andExpect(content().string(""));
  }

  @ParameterizedTest(name = "{0} is 401")
  @MethodSource("protectedApis")
  @DisplayName("GUEST 실제 자료 API/다운로드/Thread/AI 직접 호출은 401")
  void guest_protectedApisReturn401(String path, String ignoredBody) throws Exception {
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(""));
  }

  @ParameterizedTest(name = "{0} remains available to members")
  @MethodSource("protectedApis")
  @DisplayName("MEMBER 실제 Wiki API는 기존처럼 접근 가능")
  void member_canAccessProtectedApis(String path, String body) throws Exception {
    mockMvc.perform(get(path).cookie(token("STUDENT")).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(body));
  }

  @Test
  @DisplayName("STUDENT/TUTOR/ADMIN 자료 화면 접근 유지")
  void allAuthenticatedRolesKeepWikiPages() throws Exception {
    for (String role : new String[] {"STUDENT", "TUTOR", "ADMIN"}) {
      mockMvc.perform(get("/wiki/questions/1").cookie(token(role)))
          .andExpect(status().isOk())
          .andExpect(content().string("question"));
    }
  }

  @RestController
  public static class ProbeController {
    @GetMapping("/wiki") ResponseEntity<String> landing() { return ResponseEntity.ok("landing"); }

    @GetMapping({"/wiki/originals", "/wiki/questions/1"})
    public ResponseEntity<String> page() { return ResponseEntity.ok("question"); }

    @GetMapping("/api/wiki/home-feed")
    public ResponseEntity<String> homeFeed() { return ResponseEntity.ok("metadata"); }

    @GetMapping("/api/wiki/popular-downloads")
    public ResponseEntity<String> popular() { return ResponseEntity.ok("metadata"); }

    @GetMapping("/api/wiki/originals")
    public ResponseEntity<String> originals() { return ResponseEntity.ok("original-list"); }

    @GetMapping("/api/wiki/originals/1/download")
    public ResponseEntity<String> originalFile() { return ResponseEntity.ok("original-file"); }

    @GetMapping("/api/wiki/analysis-materials")
    public ResponseEntity<String> analyses() { return ResponseEntity.ok("analysis-list"); }

    @GetMapping("/api/wiki/analysis-materials/1/download")
    public ResponseEntity<String> analysisFile() { return ResponseEntity.ok("analysis-file"); }

    @GetMapping("/api/wiki/learning-materials")
    public ResponseEntity<String> learning() { return ResponseEntity.ok("learning-list"); }

    @GetMapping("/api/wiki/learning-materials/1")
    public ResponseEntity<String> learningDetail() { return ResponseEntity.ok("learning-detail"); }

    @GetMapping("/api/wiki/learning-materials/1/preview")
    public ResponseEntity<String> learningPreview() { return ResponseEntity.ok("learning-preview"); }

    @GetMapping("/api/wiki/learning-materials/1/download")
    public ResponseEntity<String> learningFile() { return ResponseEntity.ok("learning-file"); }

    @GetMapping("/api/wiki/questions")
    public ResponseEntity<String> questions() { return ResponseEntity.ok("question-list"); }

    @GetMapping("/api/wiki/questions/1")
    public ResponseEntity<String> question() { return ResponseEntity.ok("question-detail"); }

    @GetMapping("/api/wiki/questions/1/context")
    public ResponseEntity<String> context() { return ResponseEntity.ok("thread-context"); }

    @GetMapping("/api/wiki/questions/attachments/1/file")
    public ResponseEntity<String> threadFile() { return ResponseEntity.ok("thread-file"); }

    @GetMapping("/api/wiki/questions/materials/1/preview")
    public ResponseEntity<String> questionMaterial() { return ResponseEntity.ok("question-material"); }

    @GetMapping("/api/wiki/live-messages")
    public ResponseEntity<String> live() { return ResponseEntity.ok("live-thread"); }

    @GetMapping("/api/open-community/posts")
    public ResponseEntity<String> communityPosts() { return ResponseEntity.ok("community"); }

    @GetMapping("/api/open-community/images/1")
    public ResponseEntity<String> sharedImage() { return ResponseEntity.ok("wiki-shared-image"); }

    @GetMapping("/api/open-community/attachments/1/file")
    public ResponseEntity<String> sharedFile() { return ResponseEntity.ok("wiki-shared-file"); }

    @GetMapping("/problem-generator/wiki/api/questions")
    public ResponseEntity<String> ai() { return ResponseEntity.ok("wiki-ai"); }
  }
}
