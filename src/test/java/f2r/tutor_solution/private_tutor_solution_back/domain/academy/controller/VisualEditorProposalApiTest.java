package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorProposalPort.ProposalApplyResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorProposalPort.ProposalException;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
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
 * 저장 API의 appliedProposalId 계약(요청 파싱·응답·오류 code) 슬라이스 테스트.
 * 설계: docs/BUILDIT_AI_AGENT_PROPOSAL_FINAL_DESIGN.md §1.2
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
@DisplayName("시각 편집기 저장 API — appliedProposalId 계약")
class VisualEditorProposalApiTest {

  private static final String URL = "/api/tutor/academy/page-settings/visual-editor/draft";

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
    VisualEditorDraft d = new VisualEditorDraft(20L, "<p>hi</p>", "", "{}", "10");
    ReflectionTestUtils.setField(d, "draftId", 1L);
    ReflectionTestUtils.setField(d, "revision", revision);
    ReflectionTestUtils.setField(d, "updatedAt", LocalDateTime.now());
    return d;
  }

  private String body(String extra) {
    return "{\"html\":\"<p>hi</p>\",\"css\":\"\",\"projectData\":\"{}\",\"revision\":3"
        + (extra.isEmpty() ? "" : "," + extra) + "}";
  }

  private void existingDraft() {
    when(visualEditorService.getDraft(any())).thenReturn(Optional.of(draft(3L)));
  }

  @Test
  @DisplayName("given appliedProposalId 없음 when 저장 then 기존 경로만 쓰고 appliedProposal 키가 없다")
  void withoutProposalId_usesLegacyPath() throws Exception {
    existingDraft();
    when(visualEditorService.saveDraft(any(), anyString(), anyString(), anyString(), eq(3L)))
        .thenReturn(draft(4L));

    mockMvc.perform(put(URL).cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON).content(body("")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT_SAVED"))
        .andExpect(jsonPath("$.revision").value(4))
        .andExpect(jsonPath("$.appliedProposal").doesNotExist());

    verify(visualEditorService, never()).saveDraftWithProposal(
        any(), anyString(), anyString(), anyString(), any(), anyLong());
  }

  @Test
  @DisplayName("given appliedProposalId 있음 when 저장 then 제안 경로를 쓰고 appliedProposal을 응답한다")
  void withProposalId_returnsAppliedProposal() throws Exception {
    existingDraft();
    when(visualEditorService.saveDraftWithProposal(
        any(), anyString(), anyString(), anyString(), eq(3L), eq(77L)))
        .thenReturn(new VisualEditorService.SaveOutcome(
            draft(4L), new ProposalApplyResult(77L, "APPLIED", 4L)));

    mockMvc.perform(put(URL).cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON).content(body("\"appliedProposalId\":77")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(4))
        .andExpect(jsonPath("$.appliedProposal.proposalId").value(77))
        .andExpect(jsonPath("$.appliedProposal.status").value("APPLIED"))
        .andExpect(jsonPath("$.appliedProposal.appliedRevision").value(4));

    verify(visualEditorService, never()).saveDraft(
        any(), anyString(), anyString(), anyString(), any());
  }

  @Test
  @DisplayName("given 숫자 문자열 id when 저장 then 허용")
  void numericStringId_isAccepted() throws Exception {
    existingDraft();
    when(visualEditorService.saveDraftWithProposal(
        any(), anyString(), anyString(), anyString(), eq(3L), eq(77L)))
        .thenReturn(new VisualEditorService.SaveOutcome(
            draft(4L), new ProposalApplyResult(77L, "APPLIED", 4L)));

    mockMvc.perform(put(URL).cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON).content(body("\"appliedProposalId\":\"77\"")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("given 잘못된 id 형식 when 저장 then 400이고 저장 서비스는 호출되지 않는다")
  void malformedId_is400() throws Exception {
    existingDraft();
    for (String bad : List.of("\"abc\"", "{}", "0", "-5", "true", "1.5", "[1]")) {
      mockMvc.perform(put(URL).cookie(tutorCookie())
              .contentType(MediaType.APPLICATION_JSON)
              .content(body("\"appliedProposalId\":" + bad)))
          .andExpect(status().isBadRequest());
    }
    verify(visualEditorService, never()).saveDraftWithProposal(
        any(), anyString(), anyString(), anyString(), any(), anyLong());
    verify(visualEditorService, never()).saveDraft(
        any(), anyString(), anyString(), anyString(), any());
  }

  @Test
  @DisplayName("given 제안 실패(404/409/422/400) when 저장 then HTTP·code·details로 변환")
  void proposalFailures_areMappedWithCode() throws Exception {
    existingDraft();
    when(visualEditorService.saveDraftWithProposal(
        any(), anyString(), anyString(), anyString(), any(), eq(1L)))
        .thenThrow(ProposalException.notFound());
    when(visualEditorService.saveDraftWithProposal(
        any(), anyString(), anyString(), anyString(), any(), eq(2L)))
        .thenThrow(ProposalException.notPending());
    when(visualEditorService.saveDraftWithProposal(
        any(), anyString(), anyString(), anyString(), any(), eq(3L)))
        .thenThrow(ProposalException.verificationFailed(List.of("TARGET_NOT_FOUND:ve5")));
    when(visualEditorService.saveDraftWithProposal(
        any(), anyString(), anyString(), anyString(), any(), eq(4L)))
        .thenThrow(ProposalException.notAvailable());

    mockMvc.perform(put(URL).cookie(tutorCookie()).contentType(MediaType.APPLICATION_JSON)
            .content(body("\"appliedProposalId\":1")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_FOUND"));
    mockMvc.perform(put(URL).cookie(tutorCookie()).contentType(MediaType.APPLICATION_JSON)
            .content(body("\"appliedProposalId\":2")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_PENDING"));
    mockMvc.perform(put(URL).cookie(tutorCookie()).contentType(MediaType.APPLICATION_JSON)
            .content(body("\"appliedProposalId\":3")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("PROPOSAL_VERIFICATION_FAILED"))
        .andExpect(jsonPath("$.details[0]").value("TARGET_NOT_FOUND:ve5"));
    mockMvc.perform(put(URL).cookie(tutorCookie()).contentType(MediaType.APPLICATION_JSON)
            .content(body("\"appliedProposalId\":4")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_AVAILABLE"));
  }

  @Test
  @DisplayName("given revision 충돌 when 저장 then 409에 code=REVISION_CONFLICT (제안 코드와 구분)")
  void revisionConflict_hasDistinctCode() throws Exception {
    existingDraft();
    when(visualEditorService.saveDraftWithProposal(
        any(), anyString(), anyString(), anyString(), any(), eq(5L)))
        .thenThrow(new VisualEditorService.DraftConflictException("충돌"));

    mockMvc.perform(put(URL).cookie(tutorCookie()).contentType(MediaType.APPLICATION_JSON)
            .content(body("\"appliedProposalId\":5")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
  }
}
