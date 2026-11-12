package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.cafe.CafeService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class McpCafeTest {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(1L, "admin", "ADMIN");

  @SuppressWarnings("unchecked")
  McpGateway gateway(String scopes, CafeService cafe) {
    var oauth = new McpOAuth(null, null, null, null);
    ReflectionTestUtils.setField(oauth, "allowedScopes", scopes);
    var gateway = new McpGateway(null, oauth, null, null, JSON, null, mock(McpRateLimit.class));
    ObjectProvider<CafeService> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(cafe);
    ReflectionTestUtils.setField(gateway, "cafePublishing", provider);
    return gateway;
  }

  @Test
  void givenScopeDisabled_whenListing_thenCafeToolsHidden() {
    assertTrue(gateway("studio:read", null).tools().stream().noneMatch(t -> McpGateway.CAFE_TOOLS.contains(t.get("name"))));
    var tool = gateway("cafe:write", null).tools().stream().filter(t -> t.get("name").equals("publish_cafe_article")).findFirst().orElseThrow();
    var annotations = (Map<?, ?>) tool.get("annotations");
    assertEquals(false, annotations.get("readOnlyHint")); assertEquals(true, annotations.get("openWorldHint"));
  }

  @Test
  void givenExistingStudioGrant_whenListingTools_thenCafePublishingIsNotAdvertised() {
    var gateway = gateway("studio:read cafe:write", null);
    var old = new McpOAuth.Identity("old", ADMIN, Set.of("studio:read"));
    assertTrue(gateway.tools(old).stream().noneMatch(t -> McpGateway.CAFE_TOOLS.contains(t.get("name"))));
    var current = new McpOAuth.Identity("current", ADMIN, Set.of("cafe:write"));
    assertTrue(gateway.tools(current).stream().anyMatch(t -> t.get("name").equals("publish_cafe_article")));
    var nonAdmin = new McpOAuth.Identity("tutor", new AuthenticatedPrincipal(2L, "tutor", "TUTOR"), Set.of("cafe:write"));
    assertTrue(gateway.tools(nonAdmin).stream().noneMatch(t -> McpGateway.CAFE_TOOLS.contains(t.get("name"))));
  }

  @Test
  void givenMissingConsentOrNonAdmin_whenCalling_thenServiceNotInvoked() {
    var cafe = mock(CafeService.class); var gateway = gateway("cafe:write", cafe);
    assertThrows(ResponseStatusException.class, () -> gateway.execute(new McpOAuth.Identity("grant", ADMIN, Set.of("studio:read")), "cafe_status", JSON.createObjectNode()));
    assertThrows(ResponseStatusException.class, () -> gateway.execute(new McpOAuth.Identity("grant", new AuthenticatedPrincipal(2L,"tutor","TUTOR"), Set.of("cafe:write")), "cafe_status", JSON.createObjectNode()));
    verifyNoInteractions(cafe);
  }

  @Test
  void givenAdminAndConsent_whenPublishing_thenTypedPostDelegates() throws Exception {
    var cafe = mock(CafeService.class);
    gateway("cafe:write", cafe).execute(new McpOAuth.Identity("grant", ADMIN, Set.of("cafe:write")), "publish_cafe_article", JSON.readTree("{\"boardKey\":\"cases\",\"title\":\"제목\",\"content\":\"내용\",\"idempotencyKey\":\"post-00001\"}"));
    verify(cafe).publish(eq(ADMIN), eq("grant"), argThat(p -> p.boardKey().equals("cases") && p.content().equals("내용")));
  }
}
