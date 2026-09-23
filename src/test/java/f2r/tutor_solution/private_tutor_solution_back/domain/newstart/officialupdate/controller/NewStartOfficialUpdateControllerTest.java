package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = NewStartOfficialUpdateController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, WebMvcConfig.class, RoleModelInterceptor.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class NewStartOfficialUpdateControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean NewStartOfficialUpdateService service;

  private Cookie cookie(long userId, String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(userId, "user" + userId, role));
  }

  @Test
  void anonymousCanReadPublicFeed() throws Exception {
    when(service.publicUpdates(null)).thenReturn(List.of(new NewStartOfficialUpdateResponse(
        1L, "FSC", "금융위원회", "공지", "요약", "https://fsc.go.kr/a", null,
        null, null, null, "NOTICE", "NORMAL", "PUBLISHED", false, null, null, null)));

    mockMvc.perform(get("/api/newstart/updates"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].reviewStatus").value("PUBLISHED"))
        .andExpect(jsonPath("$[0].originalUrl").value("https://fsc.go.kr/a"));
  }

  @Test
  void anonymousCannotReadAdminFeed() throws Exception {
    mockMvc.perform(get("/api/admin/newstart/updates"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void regularUserCannotReadAdminFeed() throws Exception {
    mockMvc.perform(get("/api/admin/newstart/updates").cookie(cookie(2L, "STUDENT")))
        .andExpect(status().isForbidden());
  }
}
