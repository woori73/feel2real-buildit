package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class McpScopeAllowlistTest {
  static final String PRODUCTION = "studio:read studio:propose originals:read offline_access";

  McpOAuth oauth(String allowed) {
    var oauth = new McpOAuth(null, null, null, null);
    ReflectionTestUtils.setField(oauth, "allowedScopes", allowed);
    return oauth;
  }

  @Test
  void givenNoAllowlist_whenListingScopes_thenAllKnownScopesOffered() {
    var scopes = oauth("").scopes();
    assertThat(scopes).contains("easy:read", "academy:read", "studio:read", "studio:propose");
  }

  @Test
  void givenProductionAllowlist_whenListingScopes_thenOnlyStudioCollaborationScopes() {
    assertThat(oauth(PRODUCTION).scopes())
        .containsExactlyInAnyOrder(
            "studio:read", "studio:propose", "originals:read", "offline_access");
  }

  @Test
  void givenProductionAllowlist_whenListingTools_thenOnlyStudioToolsAndNoApprovalTools() {
    var gateway = new McpGateway(null, oauth(PRODUCTION), null, null, null, null, null);
    var names = gateway.tools().stream().map(tool -> (String) tool.get("name")).toList();
    assertThat(names)
        .contains(
            "read_studio",
            "list_studio_assets",
            "read_studio_asset",
            "get_asset_versions",
            "submit_studio_review",
            "upload_studio_asset",
            "create_studio_task")
        .doesNotContain("read_easy", "read_academy", "read_members", "list_catalog");
    assertThat(names).noneMatch(name -> name.matches(".*(approve|delete|restore|publish|deploy).*"));
    for (var tool : gateway.tools())
      assertThat(((Map<?, ?>) tool.get("annotations")).get("destructiveHint")).isEqualTo(false);
  }

  @Test
  void givenReadOnlyAllowlist_whenListingTools_thenWriteToolsHidden() {
    var gateway = new McpGateway(null, oauth("studio:read"), null, null, null, null, null);
    var names = gateway.tools().stream().map(tool -> (String) tool.get("name")).toList();
    assertThat(names).contains("list_studio_assets").doesNotContainAnyElementsOf(McpGateway.PROPOSE_TOOLS);
    assertThat(List.of("submit_studio_review")).allMatch(McpGateway.PROPOSE_TOOLS::contains);
  }
}
