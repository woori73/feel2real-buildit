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
@TestPropertySource(properties={"app.jwt.secret=12345678901234567890123456789012","app.jwt.expiration-seconds=86400","app.jwt.cookie-name=ACCESS_TOKEN","app.jwt.secure-cookie=false"})
class UnifiedAssistantSecurityTest {
 @Autowired MockMvc mvc; @Autowired JwtTokenProvider tokens;
 @MockitoBean UnifiedAssistantService service; @MockitoBean AssistantCommandParser parser; @MockitoBean AcademyRepository academies;
 @MockitoBean AssistantConversationService conversation;
 Cookie cookie(String role) {return new Cookie("ACCESS_TOKEN",tokens.issue(1L,"tester",role));}
 @Test void givenAnonymous_whenAccessingState_thenUnauthorized() throws Exception {mvc.perform(get("/api/dashboard/assistant/v1/progress")).andExpect(status().isUnauthorized());}
 @Test void givenStudent_whenAccessingState_thenForbidden() throws Exception {mvc.perform(get("/api/dashboard/assistant/v1/progress").cookie(cookie("STUDENT"))).andExpect(status().isForbidden());}
 @Test void givenAdminOrTutor_whenReading_thenAllowed() throws Exception {for(String role:new String[]{"ADMIN","TUTOR"})mvc.perform(get("/api/dashboard/assistant/v1/progress").cookie(cookie(role))).andExpect(status().isOk());}
 @Test void givenCrossOrigin_whenMakingDraft_thenForbiddenBeforeService() throws Exception {mvc.perform(post("/api/dashboard/assistant/v1/drafts").cookie(cookie("TUTOR")).header("Origin","https://evil.example").contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(service);}
 @Test void givenNoOrigin_whenApproving_thenForbidden() throws Exception {mvc.perform(post("/api/dashboard/assistant/v1/drafts/123/approve").cookie(cookie("TUTOR")).contentType("application/json").content("{\"planHash\":\"x\"}")).andExpect(status().isForbidden());verifyNoInteractions(service);}
 @Test void givenJsonRequest_whenDrafted_thenJacksonBoundaryUsesPlainObjects() throws Exception {
  var d=new AssistantDraft(1L,"TUTOR",7L,null,"STUDENT_CREATE","{\"name\":\"하늘\"}","{\"name\":\"하늘\"}","snapshot");
  when(service.draft(any(),eq(7L),isNull(),eq("STUDENT_CREATE"),any())).thenReturn(d);
  mvc.perform(post("/api/dashboard/assistant/v1/drafts").cookie(cookie("TUTOR")).header("Origin","http://localhost:8080").contentType("application/json").content("{\"academyId\":7,\"operation\":\"STUDENT_CREATE\",\"input\":{\"name\":\"하늘\"}}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.preview.name").value("하늘")).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.expiresAt").value(org.hamcrest.Matchers.endsWith("Z")));
 }
 @Test void givenConversation_whenReturningDraftAndOverview_thenJsonUsesPlainObjects() throws Exception {
  var d=new AssistantDraft(1L,"TUTOR",7L,null,"STUDENT_CREATE","{\"name\":\"하늘\"}","{\"name\":\"하늘\"}","snapshot");
  when(conversation.send(any(),eq(7L),eq(1L),eq("학생 하늘 등록"))).thenReturn(new AssistantConversationService.Turn("초안 생성","규칙 기반",null,java.util.List.of(d),java.util.List.of(),java.util.Map.of("academy",AssistantJson.read("{\"studentCount\":3}"))));
  mvc.perform(post("/api/dashboard/assistant/v1/conversation").cookie(cookie("TUTOR")).header("Origin","http://localhost:8080").contentType("application/json").content("{\"academyId\":7,\"businessId\":1,\"message\":\"학생 하늘 등록\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.drafts[0].preview.name").value("하늘")).andExpect(jsonPath("$.overview.academy.studentCount").value(3));
 }
 @Test void givenCrossOriginMaterial_whenUploading_thenForbiddenBeforeAnalysis() throws Exception {
  mvc.perform(multipart("/api/dashboard/assistant/v1/materials").file("file","이름\n하늘".getBytes(java.nio.charset.StandardCharsets.UTF_8)).cookie(cookie("TUTOR")).header("Origin","https://evil.example"))
    .andExpect(status().isForbidden());verifyNoInteractions(conversation);
 }
}
