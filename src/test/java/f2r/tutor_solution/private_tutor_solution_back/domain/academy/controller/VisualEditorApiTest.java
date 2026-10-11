package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 시각 편집기 API 컨트롤러 슬라이스 테스트 (Spring Boot 4 호환).
 *
 * <p>{@link VisualEditorService}는 목으로 대체하고,
 * 정제 로직({@code VisualEditorSanitizer} 정적 메서드)은 실제로 실행한다.
 * 실제 JWT 쿠키 인증을 통과하므로 400/409 매핑과 요청 검증을 검증한다.
 *
 * <p>DB 없이 실행되므로 CI build 잡에 포함된다.
 */
@WebMvcTest(controllers = AcademyEditorApiController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("시각 편집기 API 컨트롤러 테스트")
class VisualEditorApiTest {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  JwtTokenProvider tokenProvider;

  @MockitoBean
  VisualEditorService visualEditorService;

  private Cookie tutorCookie() {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(10L, "tutor1", "TUTOR"));
  }

  private VisualEditorDraft draft(Long revision) {
    VisualEditorDraft d = new VisualEditorDraft(
        20L, "<p>hi</p>", "", "{}", "10");
    ReflectionTestUtils.setField(d, "draftId", 1L);
    ReflectionTestUtils.setField(d, "revision", revision);
    ReflectionTestUtils.setField(d, "updatedAt", LocalDateTime.now());
    return d;
  }

  @Test
  @DisplayName("초안 없음 + revision 생략 → 신규 저장 200")
  void saveWithoutRevision_whenNoDraft_creates() throws Exception {
    when(visualEditorService.getDraft(any())).thenReturn(Optional.empty());
    when(visualEditorService.saveDraft(any(), anyString(), anyString(), anyString(),
        isNull())).thenReturn(draft(0L));

    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"html\":\"<p>hi</p>\",\"css\":\"\",\"projectData\":\"{}\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT_SAVED"))
        .andExpect(jsonPath("$.revision").value(0));
  }

  @Test
  @DisplayName("초안 없음 + revision 0 → 신규 저장 200 (null/0 구분)")
  void saveWithRevisionZero_whenNoDraft_creates() throws Exception {
    when(visualEditorService.getDraft(any())).thenReturn(Optional.empty());
    when(visualEditorService.saveDraft(any(), anyString(), anyString(), anyString(),
        eq(0L))).thenReturn(draft(0L));

    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"html\":\"<p>hi</p>\",\"css\":\"\",\"projectData\":\"{}\","
                + "\"revision\":0}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("초안 없음 + revision 5 → 409")
  void saveWithStaleRevision_whenNoDraft_conflict() throws Exception {
    when(visualEditorService.getDraft(any())).thenReturn(Optional.empty());

    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"html\":\"<p>hi</p>\",\"css\":\"\",\"projectData\":\"{}\","
                + "\"revision\":5}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  @DisplayName("초안 있음 + revision 생략 → 400")
  void saveWithoutRevision_whenDraftExists_badRequest() throws Exception {
    when(visualEditorService.getDraft(any()))
        .thenReturn(Optional.of(draft(3L)));

    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"html\":\"<p>hi</p>\",\"css\":\"\",\"projectData\":\"{}\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(
            org.hamcrest.Matchers.containsString("revision")));
  }

  @Test
  @DisplayName("revision 형식 오류(문자열) → 400")
  void saveWithInvalidRevisionFormat_badRequest() throws Exception {
    when(visualEditorService.getDraft(any())).thenReturn(Optional.empty());

    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"html\":\"<p>hi</p>\",\"css\":\"\",\"projectData\":\"{}\","
                + "\"revision\":\"abc\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  @DisplayName("html 타입 오류(숫자) → 400 (500 아님)")
  void saveWithWrongHtmlType_badRequest() throws Exception {
    when(visualEditorService.getDraft(any())).thenReturn(Optional.empty());

    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"html\":12345,\"css\":\"\",\"projectData\":\"{}\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  @DisplayName("CSS 정확히 65,536바이트 → 400 (500 아님)")
  void saveWithCss65536_badRequest() throws Exception {
    when(visualEditorService.getDraft(any())).thenReturn(Optional.empty());
    String css = "a{color:red;}".repeat(6000).substring(0, 65536);

    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"html\":\"<p>hi</p>\",\"css\":\""
                + css.replace("\"", "\\\"")
                + "\",\"projectData\":\"{}\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  @DisplayName("projectData tagName=script → 400")
  void saveWithScriptTagName_badRequest() throws Exception {
    when(visualEditorService.getDraft(any())).thenReturn(Optional.empty());
    String projectData = "{\\\"components\\\":[{\\\"tagName\\\":\\\"script\\\"}]}";

    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"html\":\"<p>hi</p>\",\"css\":\"\",\"projectData\":\""
                + projectData + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  @DisplayName("서비스 충돌 → 409 JSON")
  void saveConflict_mapsTo409() throws Exception {
    when(visualEditorService.getDraft(any()))
        .thenReturn(Optional.of(draft(3L)));
    when(visualEditorService.saveDraft(any(), anyString(), anyString(), anyString(),
        eq(2L)))
        .thenThrow(new VisualEditorService.DraftConflictException("충돌"));

    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"html\":\"<p>hi</p>\",\"css\":\"\",\"projectData\":\"{}\","
                + "\"revision\":2}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.message").value("충돌"));
  }

  @Test
  @DisplayName("초안 조회 → 200, revision 포함")
  void getDraft_returnsRevision() throws Exception {
    when(visualEditorService.getDraft(any()))
        .thenReturn(Optional.of(draft(7L)));

    mockMvc.perform(get("/api/tutor/academy/page-settings/visual-editor/draft")
            .cookie(tutorCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(7));
  }

  @Test
  @DisplayName("미인증 요청 → 401/403 (컨트롤러 미도달)")
  void unauthenticated_forbidden() throws Exception {
    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"html\":\"<p>hi</p>\"}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @DisplayName("게시 API → 501 유지")
  void publish_notImplemented() throws Exception {
    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/publish")
            .cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isNotImplemented());
  }
}
