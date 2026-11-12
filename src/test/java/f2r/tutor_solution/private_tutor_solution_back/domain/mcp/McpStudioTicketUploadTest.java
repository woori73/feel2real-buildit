package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioAiService;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** begin_studio_upload 도구 → POST /mcp/studio/uploads(원본 bytes) 2단계 업로드. 가짜 파일만 사용한다. */
class McpStudioTicketUploadTest {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final McpOAuth.Identity CLAUDE =
      new McpOAuth.Identity("grant-1", new AuthenticatedPrincipal(4L, "modu", "TUTOR"), Set.of("studio:read", "studio:propose"));
  private static final McpOAuth.Identity READ_ONLY =
      new McpOAuth.Identity("grant-2", new AuthenticatedPrincipal(4L, "modu", "TUTOR"), Set.of("studio:read"));
  private static final String ARGS =
      "{\"projectId\":\"ai-agent-part1\",\"workId\":\"EP01_TEST\",\"versionLabel\":\"r1\",\"changeNote\":\"시험\","
          + "\"filename\":\"test.mp4\",\"kind\":\"VIDEO\",\"episode\":1,\"modelReported\":\"claude-opus\"}";

  private final StudioAiService studioAi = mock(StudioAiService.class);
  private final McpOAuth oauth = mock(McpOAuth.class);
  private final McpStudioUploadTickets tickets = new McpStudioUploadTickets();

  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @SuppressWarnings("unchecked")
  private McpGateway gateway() {
    // projectId가 있으면 게이트웨이가 현재 MCP 요청에 프로젝트를 표시한다.
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("POST", "/mcp")));
    when(oauth.scopeAllowed(anyString())).thenReturn(true);
    when(oauth.clientName(anyString())).thenReturn("Claude");
    when(oauth.base()).thenReturn("https://feel2real.co.kr");
    var gateway = new McpGateway(null, oauth, null, null, JSON, studioAi, mock(McpRateLimit.class));
    ObjectProvider<McpStudioUploadTickets> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(tickets);
    ReflectionTestUtils.setField(gateway, "uploadTickets", provider);
    return gateway;
  }

  private McpStudioUploadController controller() {
    return new McpStudioUploadController(oauth, mock(McpRateLimit.class), studioAi, tickets);
  }

  private static MockHttpServletRequest raw(String ticket, byte[] body) {
    var req = new MockHttpServletRequest("POST", "/mcp/studio/uploads");
    if (ticket != null) req.addHeader(McpStudioUploadController.TICKET_HEADER, ticket);
    req.setContentType("application/octet-stream");
    req.setContent(body);
    return req;
  }

  @SuppressWarnings("unchecked")
  private String begin() throws Exception {
    var out = (Map<String, Object>) gateway().execute(CLAUDE, "begin_studio_upload", JSON.readTree(ARGS));
    return (String) out.get("ticket");
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenToolList_whenBeginUploadDescriptor_thenHasNoContentArgumentsAndNeedsFilename() {
    // when
    var tool =
        gateway().tools().stream().filter(t -> t.get("name").equals("begin_studio_upload")).findFirst().orElseThrow();
    // then
    var schema = (Map<String, Object>) tool.get("inputSchema");
    var props = (Map<String, Object>) schema.get("properties");
    assertFalse(props.containsKey("contentBase64"));
    assertFalse(props.containsKey("file"));
    assertTrue(((List<String>) schema.get("required")).contains("filename"));
    assertTrue(McpGateway.PROPOSE_TOOLS.contains("begin_studio_upload"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenProposeConsent_whenBeginUpload_thenReturnsTicketUrlAndCurl() throws Exception {
    // when
    var out = (Map<String, Object>) gateway().execute(CLAUDE, "begin_studio_upload", JSON.readTree(ARGS));
    // then
    assertEquals("upload_ready", out.get("status"));
    assertEquals("https://feel2real.co.kr/mcp/studio/uploads", out.get("upload_url"));
    assertEquals(McpStudioUploadController.TICKET_HEADER, out.get("ticket_header"));
    assertEquals(StudioService.MAX_FILE, out.get("max_bytes"));
    String ticket = (String) out.get("ticket");
    assertTrue(ticket.length() >= 40);
    assertTrue(((String) out.get("curl")).contains("--data-binary"));
    assertEquals(1, tickets.size());
  }

  @Test
  void givenReadOnlyConsent_whenBeginUpload_thenForbiddenAndNoTicket() {
    // when
    var e =
        assertThrows(
            ResponseStatusException.class,
            () -> gateway().execute(READ_ONLY, "begin_studio_upload", JSON.readTree(ARGS)));
    // then
    assertEquals(403, e.getStatusCode().value());
    assertEquals(0, tickets.size());
  }

  @Test
  void givenMissingFilename_whenBeginUpload_thenBadRequest() {
    // given
    String noName = ARGS.replace(",\"filename\":\"test.mp4\"", "");
    // when
    var e =
        assertThrows(
            ResponseStatusException.class, () -> gateway().execute(CLAUDE, "begin_studio_upload", JSON.readTree(noName)));
    // then
    assertEquals(400, e.getStatusCode().value());
  }

  @Test
  void givenValidTicket_whenRawBytesPosted_thenRegistersWithTicketArgsOnce() throws Exception {
    // given
    String ticket = begin();
    byte[] body = {1, 2, 3, 4, 5};
    when(studioAi.uploadAsset(any(), any(), anyString(), any(), anyString(), anyString(), any(InputStream.class), any(),
            any(), any(), any(), anyString(), any()))
        .thenAnswer(inv -> Map.of("serverVerified", true, "bytesRead", ((InputStream) inv.getArgument(6)).readAllBytes().length));
    var req = raw(ticket, body);
    // when
    var res = controller().ticketUpload(req);
    // then
    assertEquals(200, res.getStatusCode().value());
    assertEquals(5, ((Map<?, ?>) res.getBody()).get("bytesRead"));
    var connector = ArgumentCaptor.forClass(StudioAiService.Connector.class);
    verify(studioAi).uploadAsset(eq(CLAUDE.principal()), connector.capture(), eq("EP01_TEST"), isNull(), eq("r1"),
        eq("test.mp4"), any(InputStream.class), isNull(), eq("VIDEO"), eq(1), isNull(), eq("시험"), isNull());
    assertEquals("grant-1", connector.getValue().grantId());
    assertEquals("Claude", connector.getValue().client());
    assertEquals("claude-opus", connector.getValue().modelReported());
    assertEquals("ai-agent-part1", req.getAttribute(StudioService.MCP_PROJECT_ATTRIBUTE));
    // and the ticket cannot be reused
    var again = assertThrows(ResponseStatusException.class, () -> controller().ticketUpload(raw(ticket, body)));
    assertEquals(401, again.getStatusCode().value());
  }

  @Test
  void givenNoOrUnknownTicket_whenPosted_thenUnauthorizedAndNothingStored() {
    // when
    var none = assertThrows(ResponseStatusException.class, () -> controller().ticketUpload(raw(null, new byte[] {1})));
    var bad = assertThrows(ResponseStatusException.class, () -> controller().ticketUpload(raw("nope", new byte[] {1})));
    // then
    assertEquals(401, none.getStatusCode().value());
    assertEquals(401, bad.getStatusCode().value());
    verifyNoInteractions(studioAi);
  }

  @Test
  void givenEmptyBody_whenPosted_thenPayloadTooLarge413AndNothingStored() throws Exception {
    // given
    String ticket = begin();
    // when
    var e = assertThrows(ResponseStatusException.class, () -> controller().ticketUpload(raw(ticket, new byte[0])));
    // then
    assertEquals(413, e.getStatusCode().value());
    verify(studioAi, never()).uploadAsset(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void givenStreamLongerThanLimit_whenRead_thenStopsWithTooLarge() {
    // given
    var limited = new McpStudioUploadController.Limited(new java.io.ByteArrayInputStream(new byte[10]), 4);
    // when / then
    assertThrows(McpStudioUploadController.Limited.TooLarge.class, limited::readAllBytes);
  }
}
