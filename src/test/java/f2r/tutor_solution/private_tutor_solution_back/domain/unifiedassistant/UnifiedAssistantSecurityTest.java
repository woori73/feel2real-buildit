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
 @MockitoBean CodiGeneralConversation general;
 @MockitoBean CodiCalls calls;
 @MockitoBean AssistantGradingPhotos gradingPhotos;
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
 @Test void givenGeneralMode_whenSending_thenOnlyGeneralPathReceivesTextAndSearchDefaultsOn() throws Exception {
  when(general.send(any(),eq(7L),eq("영어 수업 아이디어"),isNull(),eq(true))).thenReturn(
      new AssistantConversationService.Turn("답변","Claude 일반 대화",null,java.util.List.of(),java.util.List.of(),null,
          java.util.Map.of("conversationId","new-token","sources",java.util.List.of())));
  mvc.perform(post("/api/dashboard/assistant/v1/conversation").cookie(cookie("TUTOR")).header("Origin","http://localhost:8080")
      .contentType("application/json").content("""
          {"mode":"general","academyId":7,"businessId":457,"message":"영어 수업 아이디어"}
          """)).andExpect(status().isOk()).andExpect(jsonPath("$.drafts").isEmpty()).andExpect(jsonPath("$.data.conversationId").value("new-token"));
  verifyNoInteractions(conversation);
 }
 @Test void givenCrossOrigin_whenSendingGeneralMessage_thenProviderIsNotCalled() throws Exception {
  mvc.perform(post("/api/dashboard/assistant/v1/conversation").cookie(cookie("TUTOR")).header("Origin","https://other.example")
      .contentType("application/json").content("{\"mode\":\"general\",\"academyId\":7,\"message\":\"질문\"}"))
      .andExpect(status().isForbidden());
  verifyNoInteractions(general);
 }
 @Test void givenCrossOriginMaterial_whenUploading_thenForbiddenBeforeAnalysis() throws Exception {
  mvc.perform(multipart("/api/dashboard/assistant/v1/materials").file("file","이름\n하늘".getBytes(java.nio.charset.StandardCharsets.UTF_8)).cookie(cookie("TUTOR")).header("Origin","https://evil.example"))
    .andExpect(status().isForbidden());verifyNoInteractions(conversation);
 }
 @Test void givenOversizedMaterial_whenPosting_thenRejectBeforeReadingOrAnalyzing() throws Exception {
  for (var sample:java.util.Map.of("명단.csv",262145,"업무.txt",262145,"문서.html",5*1024*1024+1,"문서.pdf",5*1024*1024+1).entrySet()) {
    mvc.perform(multipart("/api/dashboard/assistant/v1/materials")
        .file(new org.springframework.mock.web.MockMultipartFile("file",sample.getKey(),"application/octet-stream",new byte[sample.getValue()]))
        .cookie(cookie("TUTOR")).header("Origin","http://localhost:8080")).andExpect(status().isPayloadTooLarge());
  }
  verifyNoInteractions(conversation);
 }
 @Test void givenHtmlOverCsvLimit_whenAuthorized_thenReturnReadOnlyTurn() throws Exception {
  var body=new byte[262145];
  var preview=new AssistantMaterialAnalyzer.DocumentPreview("HTML","READ","문서 본문",false,null);
  when(conversation.upload(any(),eq(7L),isNull(),eq("시연.HTM"),any(byte[].class))).thenReturn(
      new AssistantConversationService.Turn("본문을 확인해 주세요.","읽기 전용",null,java.util.List.of(),java.util.List.of(),
          null,java.util.Map.of("document",preview)));
  mvc.perform(multipart("/api/dashboard/assistant/v1/materials")
      .file(new org.springframework.mock.web.MockMultipartFile("file","시연.HTM","application/octet-stream",body))
      .param("academyId","7").cookie(cookie("TUTOR")).header("Origin","http://localhost:8080"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.drafts").isEmpty()).andExpect(jsonPath("$.data.document.format").value("HTML"))
      .andExpect(jsonPath("$.data.document.text").value("문서 본문")).andExpect(jsonPath("$.data.document.truncated").value(false));
 }
 @Test void givenGradingPhoto_whenPostingAcrossOriginOrAsStudent_thenNoPhotoAccess() throws Exception {
  String url="/api/dashboard/assistant/v1/grading-photo-targets/8/photos";
  for (String role : new String[]{"TUTOR", "STUDENT"}) {
    mvc.perform(multipart(url).file("file",new byte[]{1}).param("kind","STUDENT")
        .param("businessId","3").param("studentId","2").cookie(cookie(role))
        .header("Origin",role.equals("TUTOR") ? "https://other.example" : "http://localhost:8080"))
        .andExpect(status().isForbidden());
  }
  verifyNoInteractions(gradingPhotos);
 }

 @Test void givenTutorSession_whenPostingConfirmedImage_thenReturnEasyReceipt() throws Exception {
  var result=new f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.PhotoView(
      22L,"STUDENT","학생 답안","image/jpeg",100L);
  when(gradingPhotos.attach(any(),eq(3L),eq(2L),eq(8L),eq("STUDENT"),any())).thenReturn(result);
  mvc.perform(multipart("/api/dashboard/assistant/v1/grading-photo-targets/8/photos").file("file",new byte[]{1})
      .param("kind","STUDENT").param("businessId","3").param("studentId","2").cookie(cookie("TUTOR"))
      .header("Origin","http://localhost:8080")).andExpect(status().isOk())
      .andExpect(jsonPath("$.photoId").value(22)).andExpect(jsonPath("$.contentType").value("image/jpeg"));
 }
}
