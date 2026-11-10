package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudentRosterController;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class McpStudentRosterTest {
  private final AuthenticatedPrincipal admin = new AuthenticatedPrincipal(1L, "admin", "ADMIN");

  @AfterEach
  void reset() {
    RequestContextHolder.resetRequestAttributes();
  }

  @SuppressWarnings("unchecked")
  McpGateway gateway(String scopes, StudentRosterController roster) {
    var oauth = new McpOAuth(null, null, null, null);
    ReflectionTestUtils.setField(oauth, "allowedScopes", scopes);
    var gateway = new McpGateway(null, oauth, null, null, JsonMapper.builder().build(), null, mock(McpRateLimit.class));
    ObjectProvider<StudentRosterController> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(roster);
    ReflectionTestUtils.setField(gateway, "studentRoster", provider);
    return gateway;
  }

  @Test
  void givenExistingScopesOnly_whenListTools_thenRosterToolHidden() {
    // given / when
    var names = gateway("studio:read studio:propose academy-move:write", null).tools().stream().map(t -> t.get("name")).toList();
    // then
    assertFalse(names.contains("read_student_roster"));
  }

  @Test
  void givenRosterScopeAllowed_whenListTools_thenRosterToolListed() {
    // given / when
    var names = gateway("student-roster:read", null).tools().stream().map(t -> t.get("name")).toList();
    // then
    assertTrue(names.contains("read_student_roster"));
  }

  @Test
  void givenAdminWithoutConsent_whenRead_thenRejectedBeforeReading() {
    // given
    var roster = mock(StudentRosterController.class);
    var identity = new McpOAuth.Identity("grant", admin, Set.of("studio:read", McpOAuth.ACADEMY_MOVE_WRITE));
    // when / then
    assertThrows(ResponseStatusException.class,
        () -> gateway("", roster).execute(identity, "read_student_roster", JsonMapper.builder().build().createObjectNode()));
    verifyNoInteractions(roster);
  }

  @Test
  void givenTutorWithConsent_whenRead_thenRejected() {
    // given
    var roster = mock(StudentRosterController.class);
    var identity = new McpOAuth.Identity("grant", new AuthenticatedPrincipal(2L, "tutor", "TUTOR"), Set.of(McpOAuth.STUDENT_ROSTER_READ));
    // when / then
    assertThrows(ResponseStatusException.class,
        () -> gateway("", roster).execute(identity, "read_student_roster", JsonMapper.builder().build().createObjectNode()));
    verifyNoInteractions(roster);
  }

  @Test
  void givenAdminWithConsent_whenRead_thenDelegatesToAggregateOnlyReader() throws Exception {
    // given
    var roster = mock(StudentRosterController.class);
    when(roster.mcpRead(admin)).thenReturn(Map.of("totalStudents", 3));
    var identity = new McpOAuth.Identity("grant", admin, Set.of(McpOAuth.STUDENT_ROSTER_READ));
    // when
    var result = gateway("", roster).execute(identity, "read_student_roster", JsonMapper.builder().build().createObjectNode());
    // then
    assertEquals(Map.of("totalStudents", 3), result);
  }

  @Test
  void givenGenericStudioToolOnRosterProject_whenExecute_thenRejectedEvenWithConsent() throws Exception {
    // given — 원본(실명) 파일은 일반 Studio 도구로 열 수 없다
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    var identity = new McpOAuth.Identity("grant", admin, Set.of("studio:read", McpOAuth.STUDENT_ROSTER_READ, "originals:read"));
    var g = gateway("", null);
    var json = JsonMapper.builder().build();
    // when / then
    for (String project : List.of("student-roster", "Student-Roster")) {
      var list = assertThrows(ResponseStatusException.class,
          () -> g.execute(identity, "list_studio_assets", json.readTree("{\"projectId\":\"" + project + "\"}")));
      assertTrue(list.getReason().contains("student_roster"), project);
      var doc = assertThrows(ResponseStatusException.class,
          () -> g.execute(identity, "studio_document", json.readTree("{\"fileId\":\"f1\",\"projectId\":\"" + project + "\"}")));
      assertTrue(doc.getReason().contains("student_roster"), project);
    }
  }
}
