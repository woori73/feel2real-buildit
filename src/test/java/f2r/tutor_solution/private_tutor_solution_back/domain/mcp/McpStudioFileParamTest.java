package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioAiService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** upload_studio_asset의 ChatGPT 파일 인자(openai/fileParams). 가짜 주소·가짜 파일만 사용한다. */
class McpStudioFileParamTest {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final McpOAuth.Identity CHATGPT =
      new McpOAuth.Identity(
          "grant-1", new AuthenticatedPrincipal(4L, "modu", "TUTOR"), Set.of("studio:read", "studio:propose"));

  private final StudioAiService studioAi = mock(StudioAiService.class);
  private final McpFileFetcher fetcher = mock(McpFileFetcher.class);

  @SuppressWarnings("unchecked")
  private McpGateway gateway() {
    var oauth = mock(McpOAuth.class);
    when(oauth.scopeAllowed(anyString())).thenReturn(true);
    when(oauth.clientName(anyString())).thenReturn("ChatGPT");
    var gateway = new McpGateway(null, oauth, null, null, JSON, studioAi, mock(McpRateLimit.class));
    ObjectProvider<McpFileFetcher> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(fetcher);
    ReflectionTestUtils.setField(gateway, "fileFetcher", provider);
    return gateway;
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenToolList_whenUploadDescriptor_thenDeclaresFileParamAndBase64IsOptional() {
    // when
    var upload =
        gateway().tools().stream()
            .filter(t -> t.get("name").equals("upload_studio_asset"))
            .findFirst()
            .orElseThrow();
    // then
    assertEquals(List.of("file"), ((Map<String, Object>) upload.get("_meta")).get("openai/fileParams"));
    var schema = (Map<String, Object>) upload.get("inputSchema");
    var required = (List<String>) schema.get("required");
    assertFalse(required.contains("contentBase64"));
    assertFalse(required.contains("filename"));
    var file = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("file");
    assertEquals(List.of("download_url", "file_id"), file.get("required"));
    assertEquals(false, file.get("additionalProperties"));
  }

  @Test
  void givenHostInjectedFile_whenUpload_thenServerFetchesAndUsesFileName() throws Exception {
    // given
    when(fetcher.fetch("https://files.oaiusercontent.com/x"))
        .thenReturn(new McpFileFetcher.Fetched(new ByteArrayInputStream(new byte[] {7}), "image/png"));
    when(studioAi.uploadAsset(
            any(), any(), anyString(), any(), anyString(), anyString(), any(InputStream.class), any(),
            any(), any(), any(), anyString(), any()))
        .thenReturn(Map.of("serverVerified", true));
    var args =
        JSON.readTree(
            "{\"workId\":\"EP01_TEST\",\"versionLabel\":\"r1\",\"changeNote\":\"시험\","
                + "\"title\":\"시험\",\"kind\":\"STORYBOARD\","
                + "\"file\":{\"download_url\":\"https://files.oaiusercontent.com/x\",\"file_id\":\"file_1\","
                + "\"mime_type\":\"image/png\",\"file_name\":\"X1_end.png\"}}");
    // when
    gateway().execute(CHATGPT, "upload_studio_asset", args);
    // then
    var connector = ArgumentCaptor.forClass(StudioAiService.Connector.class);
    verify(studioAi)
        .uploadAsset(
            any(), connector.capture(), eq("EP01_TEST"), isNull(), eq("r1"), eq("X1_end.png"),
            any(InputStream.class), eq("시험"), eq("STORYBOARD"), isNull(), isNull(), eq("시험"), isNull());
    assertEquals("ChatGPT", connector.getValue().client());
  }

  @Test
  void givenBothOrNeitherSource_whenUpload_thenBadRequestWithoutService() throws Exception {
    // given
    var both =
        JSON.readTree(
            "{\"workId\":\"W\",\"versionLabel\":\"r1\",\"changeNote\":\"c\",\"filename\":\"a.png\","
                + "\"contentBase64\":\"AQ==\",\"file\":{\"download_url\":\"https://files.oaiusercontent.com/x\","
                + "\"file_id\":\"f\"}}");
    var neither =
        JSON.readTree("{\"workId\":\"W\",\"versionLabel\":\"r1\",\"changeNote\":\"c\",\"filename\":\"a.png\"}");
    // when
    var e1 =
        assertThrows(
            ResponseStatusException.class, () -> gateway().execute(CHATGPT, "upload_studio_asset", both));
    var e2 =
        assertThrows(
            ResponseStatusException.class,
            () -> gateway().execute(CHATGPT, "upload_studio_asset", neither));
    // then
    assertEquals(400, e1.getStatusCode().value());
    assertEquals(400, e2.getStatusCode().value());
    verifyNoInteractions(studioAi, fetcher);
  }

  @Test
  void givenBase64WithoutFilename_whenUpload_thenMissingFilename() throws Exception {
    // given
    var args =
        JSON.readTree(
            "{\"workId\":\"W\",\"versionLabel\":\"r1\",\"changeNote\":\"c\",\"contentBase64\":\"AQ==\"}");
    // when
    var e =
        assertThrows(
            ResponseStatusException.class, () -> gateway().execute(CHATGPT, "upload_studio_asset", args));
    // then
    assertEquals("missing_argument:filename", e.getReason());
    verifyNoInteractions(studioAi);
  }
}
