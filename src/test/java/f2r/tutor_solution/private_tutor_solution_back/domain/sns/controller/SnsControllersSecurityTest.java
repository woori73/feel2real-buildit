package f2r.tutor_solution.private_tutor_solution_back.domain.sns.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.dto.SnsDtos;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.instagram.InstagramApi;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsPostRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsAdminService;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsGenerationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsMediaService;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsPublishService;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsTokenCipher;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** /admin/sns·/api/admin/sns 는 ADMIN만, 작업자 API는 작업자 토큰, 공개 이미지는 토큰 주소만. */
@WebMvcTest(controllers = {SnsAdminPageController.class, SnsAdminApiController.class, SnsWorkerController.class, SnsMediaController.class})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, WebMvcConfig.class, RoleModelInterceptor.class, JwtTokenProvider.class, CookieUtil.class})
@EnableConfigurationProperties(SnsProperties.class)
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "app.sns.worker-token=worker-secret-token"
})
class SnsControllersSecurityTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean SnsAdminService admin;
  @MockitoBean SnsGenerationService generation;
  @MockitoBean SnsPublishService publish;
  @MockitoBean SnsMediaService media;
  @MockitoBean InstagramApi instagram;
  @MockitoBean SnsTokenCipher cipher;
  @MockitoBean SnsPostRepository posts;

  private Cookie cookie(long userId, String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(userId, "user" + userId, role));
  }

  @Test
  @DisplayName("given 비로그인·학생·강사 when /admin/sns then 막힘, ADMIN만 200")
  void adminPage_onlyAdmin() throws Exception {
    // given / when / then
    mockMvc.perform(get("/admin/sns")).andExpect(status().is4xxClientError());
    mockMvc.perform(get("/admin/sns/rules").cookie(cookie(2L, "STUDENT"))).andExpect(status().isForbidden());
    mockMvc.perform(get("/admin/sns/posts").cookie(cookie(3L, "TUTOR"))).andExpect(status().isForbidden());
    mockMvc.perform(get("/admin/sns/rules").cookie(cookie(1L, "ADMIN"))).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "private, no-store"));
  }

  @Test
  @DisplayName("given 학생 when 관리자 API then 403, ADMIN이면 요약 응답(비밀값 없음)")
  void adminApi_onlyAdmin() throws Exception {
    // given
    when(admin.summary()).thenReturn(new SnsDtos.Summary(1, 0, 0, 0, 0, 0, 0, 30000, true, true, true, false, false, List.of()));
    // when / then
    mockMvc.perform(get("/api/admin/sns/summary").cookie(cookie(2L, "STUDENT"))).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/sns/summary").cookie(cookie(1L, "ADMIN"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.monthlyBudgetKrw").value(30000))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("worker-secret-token"))));
  }

  @Test
  @DisplayName("given 작업자 토큰 없음·틀림 when 작업 가져가기 then 401, 맞으면 작업 없을 때 204")
  void worker_requiresToken() throws Exception {
    // given
    when(generation.claimNextImageJob()).thenReturn(Optional.empty());
    // when / then
    mockMvc.perform(post("/api/sns/worker/jobs/next")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/sns/worker/jobs/next").header("Authorization", "Bearer wrong")).andExpect(status().isUnauthorized());
    verify(generation, never()).claimNextImageJob();
    mockMvc.perform(post("/api/sns/worker/jobs/next").header("Authorization", "Bearer worker-secret-token")).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("given 작업 있음 when 가져가기 then 작업 id·프롬프트·참고 이미지 주소")
  void worker_returnsJob() throws Exception {
    // given
    when(generation.claimNextImageJob()).thenReturn(Optional.of(new SnsGenerationService.ImageJob(9L, 1L, "a man", "1:1", 3L, 1)));
    // when / then
    mockMvc.perform(post("/api/sns/worker/jobs/next").header("Authorization", "Bearer worker-secret-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobId").value(9))
        .andExpect(jsonPath("$.prompt").value("a man"))
        .andExpect(jsonPath("$.referenceImageUrl").value("/api/sns/worker/jobs/9/reference"));
  }

  @Test
  @DisplayName("given 비로그인 when 공개 이미지 then 토큰이 맞으면 JPEG, 형식이 이상하면 404")
  void media_publicByToken() throws Exception {
    // given
    when(media.postImageByToken("tok_abcdefghijklmnopqrstu")).thenReturn(Optional.of(new SnsMediaService.Media(new byte[] {(byte) 0xFF, (byte) 0xD8}, "image/jpeg")));
    // when / then
    mockMvc.perform(get("/api/sns/media/tok_abcdefghijklmnopqrstu.jpg")).andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/jpeg"))
        .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
    mockMvc.perform(get("/api/sns/media/short.jpg")).andExpect(status().isNotFound());
    verify(media, never()).postImageByToken("short");
  }

  @Test
  @DisplayName("given state 쿠키 불일치 when 인스타그램 콜백 then 연결하지 않고 오류 안내로 돌려보냄")
  void callback_rejectsStateMismatch() throws Exception {
    // given / when / then
    mockMvc.perform(get("/admin/sns/accounts/callback").param("code", "C").param("state", "A")
            .cookie(cookie(1L, "ADMIN"), new Cookie("sns_oauth_state", "B")))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/admin/sns/accounts?error=")));
    verify(publish, never()).connect(any());
  }
}
