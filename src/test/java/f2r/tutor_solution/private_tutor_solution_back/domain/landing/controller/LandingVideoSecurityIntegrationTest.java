package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@WebMvcTest(controllers = LandingVideoSecurityIntegrationTest.SecurityProbeController.class)
@AutoConfigureMockMvc
@Import({
    SecurityConfig.class,
    JwtTokenProvider.class,
    CookieUtil.class,
    LandingVideoSecurityIntegrationTest.SecurityProbeController.class
})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("랜딩 영상 화면/API security matcher")
class LandingVideoSecurityIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @Autowired SecurityProbeController securityProbeController;

  @Test
  @DisplayName("/admin/landing은 anonymous 401, non-admin 403, ADMIN 200")
  void adminPageRequiresAdmin() throws Exception {
    mockMvc.perform(get("/admin/landing")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/admin/landing").cookie(token("STUDENT")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/admin/landing").cookie(token("TUTOR")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/admin/landing").cookie(token("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string("admin-page"));
  }

  @Test
  @DisplayName("admin API/preview는 ADMIN만 허용하고 ADMIN stream의 ASYNC redispatch도 완료한다")
  void adminApiRequiresAdmin() throws Exception {
    mockMvc.perform(get("/api/admin/landing/videos")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/admin/landing/videos").cookie(token("STUDENT")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/landing/videos/HERO_BACKGROUND/stream"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(
            get("/api/admin/landing/videos/HERO_BACKGROUND/stream").cookie(token("TUTOR")))
        .andExpect(status().isForbidden());

    MvcResult asyncResult =
        mockMvc.perform(
                get("/api/admin/landing/videos/HERO_BACKGROUND/stream").cookie(token("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andReturn();

    clearAuthenticationForAsyncRedispatch(asyncResult);
    mockMvc.perform(asyncDispatch(asyncResult))
        .andExpect(status().isOk())
        .andExpect(content().string("admin-stream"));
  }

  @Test
  @DisplayName("public managed stream은 익명 GET의 ASYNC redispatch만 허용하고 POST는 거부한다")
  void publicMatcherIsGetOnly() throws Exception {
    MvcResult asyncResult =
        mockMvc.perform(get("/api/landing/videos/HERO_BACKGROUND/stream"))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andReturn();

    clearAuthenticationForAsyncRedispatch(asyncResult);
    mockMvc.perform(asyncDispatch(asyncResult))
        .andExpect(status().isOk())
        .andExpect(content().string("public-stream"));

    mockMvc.perform(
            post("/api/landing/videos/HERO_BACKGROUND/stream")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  private Cookie token(String role) {
    return new Cookie(
        "ACCESS_TOKEN", tokenProvider.issue(1L, "test-" + role.toLowerCase(), role));
  }

  private void clearAuthenticationForAsyncRedispatch(MvcResult asyncResult)
      throws InterruptedException {
    asyncResult.getAsyncResult(5_000);
    assertTrue(
        securityProbeController.awaitStreamCompletion(),
        "streaming response body must finish before async redispatch");
    asyncResult
        .getRequest()
        .setAttribute(
            RequestAttributeSecurityContextRepository.DEFAULT_REQUEST_ATTR_NAME,
            SecurityContextHolder.createEmptyContext());
    SecurityContextHolder.clearContext();
  }

  @RestController
  public static class SecurityProbeController {

    private final Semaphore completedStreams = new Semaphore(0);

    @GetMapping("/admin/landing")
    String adminPage() {
      return "admin-page";
    }

    @GetMapping("/api/admin/landing/videos")
    String adminList() {
      return "admin-list";
    }

    @GetMapping("/api/admin/landing/videos/HERO_BACKGROUND/stream")
    StreamingResponseBody adminStream() {
      return outputStream -> writeAndSignal(outputStream, "admin-stream");
    }

    @GetMapping("/api/landing/videos/HERO_BACKGROUND/stream")
    StreamingResponseBody publicStream() {
      return outputStream -> writeAndSignal(outputStream, "public-stream");
    }

    @PostMapping("/api/landing/videos/HERO_BACKGROUND/stream")
    String publicWrite() {
      return "must-authenticate";
    }

    boolean awaitStreamCompletion() throws InterruptedException {
      return completedStreams.tryAcquire(5, TimeUnit.SECONDS);
    }

    private void writeAndSignal(java.io.OutputStream outputStream, String body)
        throws java.io.IOException {
      try {
        outputStream.write(body.getBytes(StandardCharsets.UTF_8));
      } finally {
        completedStreams.release();
      }
    }
  }
}
