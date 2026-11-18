package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

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

  @Test
  void givenParentNoticeScope_whenListingTools_thenOnlyConsentingAccountsSeeDraftTool() {
    var gateway = new McpGateway(null, oauth("studio:read parent-notice:draft"), null, null, null, null, null);
    var tutor = new AuthenticatedPrincipal(7L, "owner", "TUTOR");
    var oldGrant = new McpOAuth.Identity("old", tutor, Set.of("studio:read"));
    var draftGrant = new McpOAuth.Identity("new", tutor, Set.of(McpOAuth.PARENT_NOTICE_DRAFT));
    assertThat(gateway.tools(oldGrant)).noneMatch(tool -> tool.get("name").equals(McpGateway.SAVE_PARENT_NOTICE_DRAFT));
    assertThat(gateway.tools(draftGrant)).anyMatch(tool -> tool.get("name").equals(McpGateway.SAVE_PARENT_NOTICE_DRAFT));
    assertThat(gateway.tools(draftGrant)).noneMatch(tool -> ((String) tool.get("name")).contains("send_parent_notice"));
  }

  @Test
  void givenNoParentNoticeConsent_whenDraftInvoked_thenRejectedBeforeStorage() {
    var gateway = new McpGateway(null, oauth("parent-notice:draft"), null, null, null, null, null);
    var admin = new AuthenticatedPrincipal(1L, "admin", "ADMIN");
    var identity = new McpOAuth.Identity("old", admin, Set.of("studio:read"));
    var args = JsonMapper.builder().build().createObjectNode()
        .put("requestId", "8e4b5f07-7704-4d36-b7af-8347498a93d3")
        .put("kind", "NOTICE").put("title", "수업 안내").put("body", "월요일은 정상 수업입니다.");
    assertThatThrownBy(() -> gateway.execute(identity, McpGateway.SAVE_PARENT_NOTICE_DRAFT, args))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("parent_notice_consent_required");
  }
}
