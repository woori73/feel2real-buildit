package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
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
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAdminOverviewService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

/** read_admin_dashboard MCP 도구 — ADMIN + student-roster:read 동의가 있어야 읽고, 읽기만 한다. */
class McpAdminDashboardTest {
  private final AuthenticatedPrincipal admin = new AuthenticatedPrincipal(1L, "admin", "ADMIN");

  @SuppressWarnings("unchecked")
  McpGateway gateway(String scopes, EasyAdminOverviewService overview) {
    var oauth = new McpOAuth(null, null, null, null);
    ReflectionTestUtils.setField(oauth, "allowedScopes", scopes);
    var gateway = new McpGateway(null, oauth, null, null, JsonMapper.builder().build(), null, mock(McpRateLimit.class));
    ObjectProvider<EasyAdminOverviewService> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(overview);
    ReflectionTestUtils.setField(gateway, "adminOverview", provider);
    return gateway;
  }

  @Test
  void givenScopeNotAllowed_whenListTools_thenDashboardToolHidden() {
    // given / when
    var names = gateway("studio:read academy-move:write", null).tools().stream().map(t -> t.get("name")).toList();
    // then
    assertFalse(names.contains("read_admin_dashboard"));
  }

  @Test
  void givenRosterScopeAllowed_whenListTools_thenDashboardToolListed() {
    // given / when
    var names = gateway("student-roster:read", null).tools().stream().map(t -> t.get("name")).toList();
    // then
    assertTrue(names.contains("read_admin_dashboard"));
  }

  @Test
  void givenAdminWithoutConsent_whenRead_thenRejectedBeforeReading() {
    // given
    var overview = mock(EasyAdminOverviewService.class);
    var identity = new McpOAuth.Identity("grant", admin, Set.of("studio:read"));
    // when / then
    assertThrows(ResponseStatusException.class,
        () -> gateway("", overview).execute(identity, "read_admin_dashboard", JsonMapper.builder().build().createObjectNode()));
    verifyNoInteractions(overview);
  }

  @Test
  void givenTutorWithConsent_whenRead_thenRejected() {
    // given
    var overview = mock(EasyAdminOverviewService.class);
    var identity = new McpOAuth.Identity("grant", new AuthenticatedPrincipal(2L, "tutor", "TUTOR"), Set.of(McpOAuth.STUDENT_ROSTER_READ));
    // when / then
    assertThrows(ResponseStatusException.class,
        () -> gateway("", overview).execute(identity, "read_admin_dashboard", JsonMapper.builder().build().createObjectNode()));
    verifyNoInteractions(overview);
  }

  @Test
  void givenAdminWithConsent_whenReadWithBusiness_thenDelegatesWithThatBusiness() throws Exception {
    // given
    var overview = mock(EasyAdminOverviewService.class);
    when(overview.mcpOverview(admin, 7L)).thenReturn(Map.of("month", "2026-11"));
    var identity = new McpOAuth.Identity("grant", admin, Set.of(McpOAuth.STUDENT_ROSTER_READ));
    var args = JsonMapper.builder().build().createObjectNode().put("businessId", 7);
    // when
    Object out = gateway("", overview).execute(identity, "read_admin_dashboard", args);
    // then
    verify(overview).mcpOverview(admin, 7L);
    assertEquals(Map.of("month", "2026-11"), out);
  }
}
