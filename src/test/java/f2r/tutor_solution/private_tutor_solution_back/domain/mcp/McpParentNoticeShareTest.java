package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.parentnotice.ParentNoticeService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("MCP 학부모 안내 — 초안 목록·바로 공유 링크(보내기는 사람이)")
class McpParentNoticeShareTest {

  static final String ID = "6f1c2b3a-0000-4000-8000-000000000001";
  final AuthenticatedPrincipal owner = new AuthenticatedPrincipal(7L, "owner", "TUTOR");
  final ParentNoticeService notices = mock(ParentNoticeService.class);

  @SuppressWarnings("unchecked")
  McpGateway gateway() {
    var oauth = new McpOAuth(null, null, null, null);
    ReflectionTestUtils.setField(oauth, "allowedScopes", "parent-notice:draft");
    ReflectionTestUtils.setField(oauth, "base", "https://f2r.test/");
    var gateway = new McpGateway(null, oauth, null, null, JsonMapper.builder().build(), null, mock(McpRateLimit.class));
    ObjectProvider<ParentNoticeService> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(notices);
    ReflectionTestUtils.setField(gateway, "parentNotices", provider);
    return gateway;
  }

  @Test
  @DisplayName("given 동의한 원장·안 한 연결 when 도구 목록 then 목록 도구는 동의한 연결에만, 보내기 도구는 없다")
  void listToolVisibleOnlyWithConsent() {
    // given
    var gw = gateway();

    // when
    var consenting = gw.tools(new McpOAuth.Identity("g", owner, Set.of(McpOAuth.PARENT_NOTICE_DRAFT)));
    var old = gw.tools(new McpOAuth.Identity("o", owner, Set.of("studio:read")));

    // then
    assertThat(consenting).extracting(t -> t.get("name")).contains(McpGateway.LIST_PARENT_NOTICE_DRAFTS);
    assertThat(old).extracting(t -> t.get("name")).doesNotContain(McpGateway.LIST_PARENT_NOTICE_DRAFTS);
    assertThat(consenting).extracting(t -> (String) t.get("name")).noneMatch(n -> n.contains("send"));
  }

  @Test
  @DisplayName("given 초안 저장 when 실행 then shareUrl은 그 초안을 바로 여는 링크")
  void saveReturnsShareUrl() throws Exception {
    // given
    when(notices.save(eq(owner), any(), eq(ParentNoticeService.Source.MCP))).thenReturn(
        new ParentNoticeService.Draft(ID, "NOTICE", "12월 수업", "12월은 정상 수업입니다.", "MCP", Instant.EPOCH));
    var args = JsonMapper.builder().build().createObjectNode().put("requestId", "8e4b5f07-7704-4d36-b7af-8347498a93d3")
        .put("kind", "NOTICE").put("title", "12월 수업").put("body", "12월은 정상 수업입니다.");

    // when
    @SuppressWarnings("unchecked") var out = (Map<String, Object>) gateway().execute(
        new McpOAuth.Identity("g", owner, Set.of(McpOAuth.PARENT_NOTICE_DRAFT)), McpGateway.SAVE_PARENT_NOTICE_DRAFT, args);

    // then
    assertThat(out).containsEntry("shareUrl", "https://f2r.test/dashboard/parent-notices?draft=" + ID)
        .containsEntry("status", "DRAFT");
    assertThat((String) out.get("message")).contains("공유 버튼");
  }

  @Test
  @DisplayName("given 초안 여러 건 when 목록 then 최근 10건과 각 shareUrl")
  void listReturnsShareUrls() throws Exception {
    // given
    var many = java.util.stream.IntStream.range(0, 12).mapToObj(i -> new ParentNoticeService.Draft(
        ID.substring(0, 35) + (i % 10), "NOTICE", "안내 " + i, "본문", "MCP", Instant.EPOCH)).toList();
    when(notices.list(owner)).thenReturn(many);

    // when
    @SuppressWarnings("unchecked") var out = (Map<String, Object>) gateway().execute(
        new McpOAuth.Identity("g", owner, Set.of(McpOAuth.PARENT_NOTICE_DRAFT)), McpGateway.LIST_PARENT_NOTICE_DRAFTS,
        JsonMapper.builder().build().createObjectNode());

    // then
    @SuppressWarnings("unchecked") var drafts = (List<Map<String, Object>>) out.get("drafts");
    assertThat(drafts).hasSize(10);
    assertThat((String) drafts.get(0).get("shareUrl")).startsWith("https://f2r.test/dashboard/parent-notices?draft=");
  }
}
