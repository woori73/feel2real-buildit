package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
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
  private final AcademyMoveController controller=new AcademyMoveController(studio,db,model,mock(AssistantModelBudget.class));
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
}
