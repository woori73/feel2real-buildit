package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** /admin/student-roster 화면과 /api/admin/student-roster/** API는 ADMIN만, 쓰기는 같은 사이트 Origin만. */
@WebMvcTest(controllers = StudentRosterController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, WebMvcConfig.class, RoleModelInterceptor.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "app.studio.public-base-url=https://feel2real.co.kr"
})
class StudentRosterSecurityTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean StudioService studio;
  @MockitoBean JdbcTemplate db;

  private Cookie cookie(long userId, String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(userId, "user" + userId, role));
  }

  @Test
  @DisplayName("given 비로그인·학생·강사 when 화면·API then 막힘, ADMIN만 200 + no-store")
  void givenNonAdmin_whenPageOrApi_thenForbidden() throws Exception {
    // given
    when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
    // when / then
    mockMvc.perform(get("/admin/student-roster")).andExpect(status().is4xxClientError());
    for (String role : List.of("STUDENT", "TUTOR")) {
      mockMvc.perform(get("/admin/student-roster").cookie(cookie(2L, role))).andExpect(status().isForbidden());
      mockMvc.perform(get("/api/admin/student-roster/entries").cookie(cookie(2L, role))).andExpect(status().isForbidden());
      mockMvc.perform(get("/api/admin/student-roster/history").param("year", "2026").cookie(cookie(2L, role))).andExpect(status().isForbidden());
      mockMvc.perform(post("/api/admin/student-roster/entries").cookie(cookie(2L, role)).header("Origin", "https://feel2real.co.kr")
          .contentType(MediaType.APPLICATION_JSON).content("{\"year\":\"2026\",\"pdfFileId\":\"p\",\"wordFileId\":\"w\"}")).andExpect(status().isForbidden());
      mockMvc.perform(multipart("/api/admin/student-roster/files").file(new MockMultipartFile("file", "2026.pdf", "application/pdf", "%PDF-".getBytes()))
          .cookie(cookie(2L, role)).header("Origin", "https://feel2real.co.kr")).andExpect(status().isForbidden());
    }
    mockMvc.perform(get("/admin/student-roster").cookie(cookie(1L, "ADMIN"))).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "private, no-store"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("학생명단(시간표)")));
    mockMvc.perform(get("/api/admin/student-roster/entries").cookie(cookie(1L, "ADMIN"))).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "private, no-store"));
    verifyNoInteractions(studio);
  }

  @Test
  @DisplayName("given ADMIN이라도 다른 사이트 Origin when POST then 403, 저장 안 함")
  void givenForeignOrigin_whenPost_thenForbidden() throws Exception {
    // given / when / then
    mockMvc.perform(post("/api/admin/student-roster/entries").cookie(cookie(1L, "ADMIN")).header("Origin", "https://evil.example")
        .contentType(MediaType.APPLICATION_JSON).content("{\"year\":\"2026\",\"pdfFileId\":\"p\",\"wordFileId\":\"w\"}")).andExpect(status().isForbidden());
    mockMvc.perform(multipart("/api/admin/student-roster/files").file(new MockMultipartFile("file", "2026.pdf", "application/pdf", "%PDF-".getBytes()))
        .cookie(cookie(1L, "ADMIN"))).andExpect(status().isForbidden());
    verifyNoInteractions(studio);
  }

  @Test
  @DisplayName("given ADMIN·같은 사이트 when 연도 3자리 저장 then 400 안내 문구")
  void givenBadYear_whenSave_thenBadRequestWithPlainMessage() throws Exception {
    // given / when / then
    mockMvc.perform(post("/api/admin/student-roster/entries").cookie(cookie(1L, "ADMIN")).header("Origin", "https://feel2real.co.kr")
            .contentType(MediaType.APPLICATION_JSON).content("{\"year\":\"202\",\"pdfFileId\":\"p\",\"wordFileId\":\"w\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("연도를 4자리로 적어 주세요."));
    verifyNoInteractions(studio);
  }
}
