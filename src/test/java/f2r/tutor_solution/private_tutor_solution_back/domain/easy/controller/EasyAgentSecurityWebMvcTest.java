package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAgentService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(controllers=EasyAgentController.class) @AutoConfigureMockMvc
@Import({SecurityConfig.class,JwtTokenProvider.class,CookieUtil.class})
@TestPropertySource(properties={"app.jwt.secret=12345678901234567890123456789012","app.jwt.expiration-seconds=86400","app.jwt.cookie-name=ACCESS_TOKEN","app.jwt.secure-cookie=false"})
class EasyAgentSecurityWebMvcTest {
 @Autowired MockMvc mvc;@Autowired JwtTokenProvider tokens;
 @MockitoBean EasyAgentService service;@MockitoBean AcademyRepository academyRepository;
 @Test void adminCannotUseTutorAgentRoute() throws Exception{
  Cookie admin=new Cookie("ACCESS_TOKEN",tokens.issue(1L,"admin","ADMIN"));
  mvc.perform(post("/api/tutor/easy/agent/sessions").param("businessId","1").cookie(admin)).andExpect(status().isForbidden());
 }
 @Test void unauthenticatedCannotUseAgentRoute() throws Exception{
  mvc.perform(post("/api/tutor/easy/agent/sessions").param("businessId","1")).andExpect(status().isUnauthorized());
 }
}
