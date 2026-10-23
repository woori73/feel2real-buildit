package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(UnifiedAssistantController.class) @AutoConfigureMockMvc
@Import({SecurityConfig.class,JwtTokenProvider.class,CookieUtil.class})
@TestPropertySource(properties={"app.jwt.secret=12345678901234567890123456789012","app.jwt.expiration-seconds=86400","app.jwt.cookie-name=ACCESS_TOKEN","app.jwt.secure-cookie=false","buildit.unified-assistant.allowed-origins=https://stg.example.test"})

class UnifiedAssistantOriginConfigTest {
 @Autowired MockMvc mvc; @Autowired JwtTokenProvider tokens;
 @MockitoBean UnifiedAssistantService service; @MockitoBean AssistantCommandParser parser; @MockitoBean AcademyRepository academies;
 @MockitoBean AssistantConversationService conversation;
 Cookie cookie() {return new Cookie("ACCESS_TOKEN",tokens.issue(1L,"tester","TUTOR"));}
 @Test void givenConfiguredStagingOrigin_whenCommanding_thenAllowed() throws Exception {
  mvc.perform(post("/api/dashboard/assistant/v1/command").cookie(cookie()).header("Origin","https://stg.example.test").contentType("application/json").content("{\"message\":\"학생 등록\"}")).andExpect(status().isOk());
 }
 @Test void givenOriginOutsideConfiguredList_whenCommanding_thenForbidden() throws Exception {
  mvc.perform(post("/api/dashboard/assistant/v1/command").cookie(cookie()).header("Origin","http://localhost:8080").contentType("application/json").content("{\"message\":\"학생 등록\"}")).andExpect(status().isForbidden());
 }
}
