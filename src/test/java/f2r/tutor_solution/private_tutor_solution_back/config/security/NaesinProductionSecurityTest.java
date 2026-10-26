package f2r.tutor_solution.private_tutor_solution_back.config.security;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.naesin.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioAccessGuard;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NaesinProductionController.class)
@Import({SecurityConfig.class,JwtTokenProvider.class,CookieUtil.class,StudioAccessGuard.class})
@TestPropertySource(properties={"app.jwt.secret=12345678901234567890123456789012","app.jwt.expiration-seconds=86400","app.jwt.cookie-name=ACCESS_TOKEN","app.jwt.secure-cookie=false"})
class NaesinProductionSecurityTest {
  @Autowired MockMvc mvc; @Autowired JwtTokenProvider tokens;
  @MockitoBean AcademyRepository academies; @MockitoBean UserRepository users;
  @MockitoBean NaesinProductionService service;
  Cookie cookie(long id,String login,String role){return new Cookie("ACCESS_TOKEN",tokens.issue(id,login,role));}
  @BeforeEach void operator(){var u=User.builder().loginId("modu").role(Role.TUTOR).build();ReflectionTestUtils.setField(u,"userId",42L);when(users.findByLoginId("modu")).thenReturn(Optional.of(u));}

  @Test void givenPrivateRoutes_whenStudentOrSpoofedOperatorOrGuest_thenDenied() throws Exception {
    for(String path:new String[]{"/api/naesin-production/projects","/api/naesin-production/state?projectId=naesin-test","/api/naesin-production/files/test/download?projectId=naesin-test"}){
      mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().string("X-Robots-Tag","noindex, nofollow"));
      mvc.perform(get(path).cookie(cookie(3L,"student","STUDENT"))).andExpect(status().isForbidden());
      mvc.perform(get(path).cookie(cookie(77L,"modu","TUTOR"))).andExpect(status().isForbidden());
    }
    verifyNoInteractions(service);
  }
  @Test void givenRealAdminOrOperator_whenListing_thenAllowed() throws Exception {
    mvc.perform(get("/api/naesin-production/projects").cookie(cookie(1L,"admin","ADMIN"))).andExpect(status().isOk());
    mvc.perform(get("/api/naesin-production/projects").cookie(cookie(42L,"modu","TUTOR"))).andExpect(status().isOk());
    verify(service,times(2)).list(any());
  }
  @Test void givenStudent_whenWikiPublicationRead_thenAllowedButPrivateWritesForbidden() throws Exception {
    var student=cookie(3L,"student","STUDENT");
    mvc.perform(get("/api/naesin-wiki/publications").cookie(student)).andExpect(status().isOk());
    mvc.perform(post("/api/naesin-production/revisions/r1/publish?projectId=naesin-test").cookie(student)
        .header("Origin","https://feel2real.co.kr").contentType(MediaType.APPLICATION_JSON).content("{\"expectedApprovedId\":\"r1\"}")).andExpect(status().isForbidden());
    mvc.perform(get("/api/naesin-wiki/publications")).andExpect(status().isUnauthorized());
  }
  @Test void givenAdminCookie_whenCrossOriginWrite_thenDeniedBeforeMutation() throws Exception {
    mvc.perform(post("/api/naesin-production/projects").cookie(cookie(1L,"admin","ADMIN"))
        .header("Origin","https://untrusted.example").contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"중2\",\"school\":\"신구중\",\"grade\":\"중2\",\"examRange\":\"기말\"}"))
        .andExpect(status().isForbidden());verifyNoInteractions(service);
  }
  @Test void givenGuestHtml_whenOpeningWorkspace_thenLoginReturnUrlPreserved() throws Exception {
    mvc.perform(get("/naesin-production-studio").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location",org.hamcrest.Matchers.containsString("returnUrl=")));
  }
}
