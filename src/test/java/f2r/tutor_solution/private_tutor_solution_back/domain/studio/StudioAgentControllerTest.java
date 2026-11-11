package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** 가짜 토큰·가짜 파일 ID만 사용한다. */
class StudioAgentControllerTest {
  private static final String TOKEN = "test-agent-token-0123456789";

  private final StudioService studio = mock(StudioService.class);
  private final StudioAiService studioAi = mock(StudioAiService.class);
  private final UserRepository users = mock(UserRepository.class);

  private StudioAgentController controller(String token) {
    return new StudioAgentController(studio, studioAi, users, token, "modu");
  }

  private static User operator() {
    var u = User.builder().loginId("modu").name("운영").role(Role.TUTOR).build();
    ReflectionTestUtils.setField(u, "userId", 7L);
    return u;
  }

  private static MockHttpServletRequest request(String authorization) {
    var r = new MockHttpServletRequest();
    if (authorization != null) r.addHeader("Authorization", authorization);
    return r;
  }

  @Test
  void givenNoConfiguredToken_whenAuthenticate_thenNotFoundWithoutUserLookup() {
    // given
    var controller = controller("");
    // when
    var e =
        assertThrows(
            ResponseStatusException.class,
            () -> controller.authenticate(request("Bearer " + TOKEN)));
    // then
    assertEquals(404, e.getStatusCode().value());
    verifyNoInteractions(users, studio, studioAi);
  }

  @Test
  void givenWrongOrMissingToken_whenAuthenticate_thenUnauthorized() {
    // given
    var controller = controller(TOKEN);
    // when
    var wrong =
        assertThrows(
            ResponseStatusException.class, () -> controller.authenticate(request("Bearer nope")));
    var missing =
        assertThrows(ResponseStatusException.class, () -> controller.authenticate(request(null)));
    // then
    assertEquals(401, wrong.getStatusCode().value());
    assertEquals(401, missing.getStatusCode().value());
    verifyNoInteractions(users);
  }

  @Test
  void givenCorrectToken_whenAuthenticate_thenOperatorPrincipal() {
    // given
    when(users.findByLoginId("modu")).thenReturn(Optional.of(operator()));
    // when
    AuthenticatedPrincipal p = controller(TOKEN).authenticate(request("Bearer " + TOKEN));
    // then
    assertEquals(7L, p.userId());
    assertEquals("modu", p.loginId());
    assertEquals("TUTOR", p.role());
  }

  @Test
  void givenCorrectTokenButNoOperatorAccount_whenAuthenticate_thenServiceUnavailable() {
    // given
    when(users.findByLoginId("modu")).thenReturn(Optional.empty());
    // when
    var e =
        assertThrows(
            ResponseStatusException.class,
            () -> controller(TOKEN).authenticate(request("Bearer " + TOKEN)));
    // then
    assertEquals(503, e.getStatusCode().value());
  }

  @Test
  void givenCorrectToken_whenUpload_thenAiProposalWithProjectSelected() throws Exception {
    // given
    when(users.findByLoginId("modu")).thenReturn(Optional.of(operator()));
    when(studioAi.uploadAsset(
            any(), any(), anyString(), any(), anyString(), anyString(), any(InputStream.class),
            any(), any(), any(), any(), anyString(), any()))
        .thenReturn(Map.of("workId", "EP01_TEST", "serverVerified", true));
    var http = request("Bearer " + TOKEN);
    var file = new MockMultipartFile("file", "still.png", "image/png", new byte[] {1, 2, 3});
    // when
    var response =
        controller(TOKEN)
            .upload(
                http, file, "EP01_TEST", "r1", "시험 등록", "ai-agent-part1", null, "제목",
                "STORYBOARD", 1, null, null, "Claude");
    // then
    assertEquals(200, response.getStatusCode().value());
    assertEquals("ai-agent-part1", http.getAttribute(StudioService.MCP_PROJECT_ATTRIBUTE));
    var connector = ArgumentCaptor.forClass(StudioAiService.Connector.class);
    verify(studioAi)
        .uploadAsset(
            any(), connector.capture(), eq("EP01_TEST"), isNull(), eq("r1"), eq("still.png"),
            any(InputStream.class), eq("제목"), eq("STORYBOARD"), eq(1), isNull(), eq("시험 등록"),
            isNull());
    assertTrue(connector.getValue().ai());
    assertEquals(StudioAgentController.CLIENT, connector.getValue().client());
  }

  @Test
  void givenInvalidProjectId_whenUpload_thenBadRequestBeforeService() {
    // given
    when(users.findByLoginId("modu")).thenReturn(Optional.of(operator()));
    var file = new MockMultipartFile("file", "still.png", "image/png", new byte[] {1});
    // when
    var e =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controller(TOKEN)
                    .upload(
                        request("Bearer " + TOKEN), file, "EP01_TEST", "r1", "메모", "../etc",
                        null, null, null, null, null, null, null));
    // then
    assertEquals(400, e.getStatusCode().value());
    verifyNoInteractions(studioAi);
  }

  @Test
  void givenWrongToken_whenUpload_thenUnauthorizedBeforeService() {
    // given
    var file = new MockMultipartFile("file", "still.png", "image/png", new byte[] {1});
    // when
    var e =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controller(TOKEN)
                    .upload(
                        request("Bearer nope"), file, "EP01_TEST", "r1", "메모", null, null, null,
                        null, null, null, null, null));
    // then
    assertEquals(401, e.getStatusCode().value());
    verifyNoInteractions(studioAi);
  }

  @Test
  void givenCorrectToken_whenContent_thenOriginalBytesAsAttachment() throws Exception {
    // given
    when(users.findByLoginId("modu")).thenReturn(Optional.of(operator()));
    when(studio.file(any(), eq("file-1"))).thenReturn(Map.of("filename", "C1.png"));
    when(studio.bytes(any(), eq("file-1"))).thenReturn(new byte[] {9, 8, 7});
    var response = new MockHttpServletResponse();
    // when
    controller(TOKEN).content(request("Bearer " + TOKEN), "file-1", response);
    // then
    assertArrayEquals(new byte[] {9, 8, 7}, response.getContentAsByteArray());
    assertEquals("private, no-store", response.getHeader("Cache-Control"));
    assertTrue(response.getHeader("Content-Disposition").contains("C1.png"));
  }
}
