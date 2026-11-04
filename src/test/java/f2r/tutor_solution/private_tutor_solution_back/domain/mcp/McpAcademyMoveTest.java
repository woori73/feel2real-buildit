package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.AcademyMoveController;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class McpAcademyMoveTest {
  McpGateway gateway(String scopes,AcademyMoveController moves) {
    var oauth=new McpOAuth(null,null,null,null);ReflectionTestUtils.setField(oauth,"allowedScopes",scopes);
    var gateway=new McpGateway(null,oauth,null,null,JsonMapper.builder().build(),null,mock(McpRateLimit.class));
    ObjectProvider<AcademyMoveController> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(moves);
    ReflectionTestUtils.setField(gateway,"academyMove",provider);return gateway;
  }
  @Test void givenOldStudioScope_whenListTools_thenRelocationIsNotAddedToExistingConsent() {
    var names=gateway("studio:read studio:propose",null).tools().stream().map(t->t.get("name")).toList();
    assertFalse(names.contains("post_academy_move"));assertFalse(names.contains("read_academy_move"));
  }
  @Test void givenNoNewConsent_whenPost_thenRejectBeforeWriting() {
    var moves=mock(AcademyMoveController.class);var g=gateway("",moves);
    var identity=new McpOAuth.Identity("grant",new AuthenticatedPrincipal(1L,"admin","ADMIN"),Set.of("studio:propose"));
    var args=JsonMapper.builder().build().readTree("{\"content\":\"test\",\"issue\":\"원상복구\",\"idempotencyKey\":\"key\"}");
    assertThrows(ResponseStatusException.class,()->g.execute(identity,"post_academy_move",args));verifyNoInteractions(moves);
  }
  @Test void givenTutorWithScope_whenRead_thenReject() {
    var moves=mock(AcademyMoveController.class);var g=gateway("",moves);
    var identity=new McpOAuth.Identity("grant",new AuthenticatedPrincipal(2L,"tutor","TUTOR"),Set.of(McpOAuth.ACADEMY_MOVE_WRITE));
    assertThrows(ResponseStatusException.class,()->g.execute(identity,"read_academy_move",JsonMapper.builder().build().createObjectNode()));verifyNoInteractions(moves);
  }
  @Test void givenAdminAndExplicitConsent_whenPost_thenDelegateToSameNotebookAndSummary() throws Exception {
    var moves=mock(AcademyMoveController.class);var g=gateway("",moves);var principal=new AuthenticatedPrincipal(1L,"admin","ADMIN");
    when(moves.mcpPost(eq(principal),any(),eq("grant"))).thenReturn(Map.of("answer","정리 완료"));
    var identity=new McpOAuth.Identity("grant",principal,Set.of(McpOAuth.ACADEMY_MOVE_WRITE));
    var args=JsonMapper.builder().build().readTree("{\"content\":\"synthetic note\",\"issue\":\"원상복구\",\"idempotencyKey\":\"key\"}");
    assertEquals(Map.of("answer","정리 완료"),g.execute(identity,"post_academy_move",args));
    verify(moves).mcpPost(eq(principal),argThat(p->p.issue().equals("원상복구")&&p.content().equals("synthetic note")),eq("grant"));
  }
}
