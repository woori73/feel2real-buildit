package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import java.util.Map;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.AssistantModelBudget;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

class AcademyMoveControllerTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final StudioService studio=mock(StudioService.class);
  private final MetaMuseSparkLlmTransport model=mock(MetaMuseSparkLlmTransport.class);
  private final AssistantModelBudget budget=mock(AssistantModelBudget.class);
  private final AcademyMoveController controller=new AcademyMoveController(studio,db,model,budget);
  private final AuthenticatedPrincipal admin=new AuthenticatedPrincipal(1L,"admin","ADMIN");

  @Test void givenTutor_whenGuard_thenForbiddenBeforeDataAccess() {
    var request=new MockHttpServletRequest("GET","/api/admin/academy-move/entries");
    assertThrows(ResponseStatusException.class,()->controller.guard(new AuthenticatedPrincipal(2L,"modu","TUTOR"),request,new MockHttpServletResponse()));
    verifyNoInteractions(db,studio,model);
  }
  @Test void givenForeignOrigin_whenPosting_thenForbidden() {
    ReflectionTestUtils.setField(controller,"publicBase","https://feel2real.co.kr");
    var request=new MockHttpServletRequest("POST","/api/admin/academy-move/entries");request.addHeader("Origin","https://other.example");
    assertThrows(ResponseStatusException.class,()->controller.guard(admin,request,new MockHttpServletResponse()));
  }
  @Test void givenAdminRead_whenGuard_thenSelectFixedPrivateProjectWithoutWriting() {
    var request=new MockHttpServletRequest("GET","/api/admin/academy-move/entries");
    request.addParameter("projectId","unrelated-project");
    controller.guard(admin,request,new MockHttpServletResponse());
    assertEquals("academy-relocation",request.getAttribute(StudioService.MCP_PROJECT_ATTRIBUTE));
    verifyNoInteractions(db,studio,model);
  }
  @Test void givenPrivateMoveProject_whenOperatorUsesGenericStudioApi_thenForbidden() {
    var request=new MockHttpServletRequest();request.addParameter("projectId","academy-relocation");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {var service=new StudioService(db,mock(FileStorage.class));assertThrows(ResponseStatusException.class,()->service.permission(new AuthenticatedPrincipal(2L,"modu","TUTOR")));}
    finally {RequestContextHolder.resetRequestAttributes();}
    verifyNoInteractions(db);
  }
  @Test void givenUnknownCategory_whenSave_thenNoWrites() {
    assertThrows(ResponseStatusException.class,()->controller.save(admin,new AcademyMoveController.Entry(null,null,"title","text","invalid",false,null)));
    verifyNoInteractions(db,studio);
  }
  @Test void givenLogoQuestion_whenLocalAdvice_thenConcreteNextStepsWithoutInventedFacts() {
    String answer=AcademyMoveController.advice("로고를 변경하고 싶어요",0);
    assertTrue(answer.contains("가로형·정사각형"));assertTrue(answer.contains("활성 자료 0건"));
    assertFalse(answer.contains("변경을 완료"));
  }

  @Test void givenRelocationAiEnabled_whenChat_thenConfiguredModelAnswersAndDraftIsSaved() {
    ReflectionTestUtils.setField(controller,"aiEnabled",true);
    when(budget.tryAcquire(1L)).thenReturn(true);
    when(model.complete(anyString(),anyString(),eq(6000))).thenReturn(NewStartLlmCompletion.success("이전일과 로고 기준부터 정리하세요.","meta","muse"));
    when(studio.save(eq(admin),any(StudioService.Revision.class))).thenReturn(Map.of("id","answer-1"));
    var answer=(Map<?,?>)controller.chat(admin,new AcademyMoveController.Question("이전 계획을 도와주세요","suni"));
    assertEquals("이전일과 로고 기준부터 정리하세요.",answer.get("answer"));
    assertEquals("AI 상담 · 등록 설명과 최근 대화 참고",answer.get("method"));
    verify(studio).save(eq(admin),argThat(r -> r.origin().equals("AI_PROPOSAL") && r.content().contains("로고 기준")));
  }
  @Test void givenBudgetExhausted_whenChat_thenChecklistStillWorksWithoutModelCall() {
    ReflectionTestUtils.setField(controller,"aiEnabled",true);
    when(studio.save(eq(admin),any(StudioService.Revision.class))).thenReturn(Map.of("id","answer-2"));
    var answer=(Map<?,?>)controller.chat(admin,new AcademyMoveController.Question("이전 일정","seven"));
    assertEquals("자료 기반 체크리스트 · 모델 미사용",answer.get("method"));
    verifyNoInteractions(model);
  }
  @Test void givenLargeNotes_whenBuildingModelContext_thenEachBodyIsBounded() {
    var context=AcademyMoveController.context(List.of(Map.of("title","견적","content","x".repeat(100000))),1500);
    assertTrue(context.getFirst().length()<1600);
  }
}
