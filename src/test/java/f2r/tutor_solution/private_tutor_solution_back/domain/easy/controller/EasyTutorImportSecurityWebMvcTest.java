package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorImportService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = EasyTutorImportController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class EasyTutorImportSecurityWebMvcTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean EasyTutorImportService importService;
  @MockitoBean AcademyRepository academyRepository;

  @Test
  void tutorCanReachOwnImportRoute() throws Exception {
    Cookie tutor = new Cookie("ACCESS_TOKEN", tokenProvider.issue(10L, "owner-a", "TUTOR"));
    mockMvc.perform(get("/api/tutor/easy/imports").param("businessId", "1").cookie(tutor))
        .andExpect(status().isOk());
  }

  @Test
  void adminCanUseLedgerImportRoute() throws Exception {
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "admin", "ADMIN"));
    mockMvc.perform(get("/api/tutor/easy/imports").param("businessId", "1").cookie(admin))
        .andExpect(status().isOk());
  }
}
