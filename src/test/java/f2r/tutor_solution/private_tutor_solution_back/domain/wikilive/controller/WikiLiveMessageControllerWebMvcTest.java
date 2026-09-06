package f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.dto.WikiLiveMessageResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.service.WikiLiveMessageService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("WIKI LIVE API — 조회는 공개, 작성은 회원, 삭제는 ADMIN")
@WebMvcTest(controllers = WikiLiveMessageController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class WikiLiveMessageControllerWebMvcTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean WikiLiveMessageService service;
  @MockitoBean AcademyRepository academyRepository;

  @Test
  @DisplayName("GET /api/wiki/live-messages — 익명 200")
  void list_anonymous_ok() throws Exception {
    when(service.recent()).thenReturn(List.of());
    mockMvc.perform(get("/api/wiki/live-messages")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET ?sinceId= — 폴링도 익명 200")
  void listSince_anonymous_ok() throws Exception {
    when(service.since(eq(3L))).thenReturn(List.of());
    mockMvc.perform(get("/api/wiki/live-messages").param("sinceId", "3")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("POST /api/wiki/live-messages — 익명 401")
  void create_anonymous_unauthorized() throws Exception {
    mockMvc.perform(post("/api/wiki/live-messages")
            .contentType("application/json").content("{\"message\":\"안녕하세요\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST — 로그인 회원 201, 응답에 개인정보 없음")
  void create_member_created() throws Exception {
    when(service.create(eq(1L), any())).thenReturn(
        new WikiLiveMessageResponse(7L, "김OO", "안녕하세요", LocalDateTime.now()));

    mockMvc.perform(post("/api/wiki/live-messages").cookie(token("STUDENT"))
            .contentType("application/json").content("{\"message\":\"안녕하세요\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.displayName").value("김OO"))
        .andExpect(jsonPath("$.userId").doesNotExist())
        .andExpect(jsonPath("$.role").doesNotExist());
  }

  @Test
  @DisplayName("DELETE /api/admin/wiki/live-messages/{id} — 익명 401 / 회원 403 / ADMIN 204")
  void adminHide_adminOnly() throws Exception {
    mockMvc.perform(delete("/api/admin/wiki/live-messages/1")).andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/api/admin/wiki/live-messages/1").cookie(token("STUDENT")))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/admin/wiki/live-messages/1").cookie(token("ADMIN")))
        .andExpect(status().isNoContent());
  }

  private Cookie token(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "test-" + role.toLowerCase(), role));
  }
}
