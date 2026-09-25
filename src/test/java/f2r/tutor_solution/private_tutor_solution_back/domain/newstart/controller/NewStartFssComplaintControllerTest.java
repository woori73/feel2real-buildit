package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.FssComplaintHelperService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 금감원 민원 도우미 endpoint 계약 테스트. 인증은 {@code /api/**} catch-all 유지,
 * companion 3종 payload, compose 성공/실패 응답이 사용자 원문·내부 상세를 노출하지 않음을 본다.
 */
@WebMvcTest(controllers = NewStartFssComplaintController.class)
@AutoConfigureMockMvc
@Import({
    SecurityConfig.class,
    WebMvcConfig.class,
    RoleModelInterceptor.class,
    JwtTokenProvider.class,
    CookieUtil.class
})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class NewStartFssComplaintControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean FssComplaintHelperService fssComplaintHelperService;

  private Cookie userCookie() {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "student", "STUDENT"));
  }

  @Test
  void anonymousRequestsAreRejected() throws Exception {
    mockMvc.perform(get("/api/newstart/fss/companion"))
        .andExpect(status().is4xxClientError());
    mockMvc.perform(post("/api/newstart/fss/companion"))
        .andExpect(status().is4xxClientError());
    mockMvc.perform(post("/api/newstart/fss/compose")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sampleId\":\"FS-1\",\"situationText\":\"x\"}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void companionReturnsThreeEbookSamplesAndManual() throws Exception {
    when(fssComplaintHelperService.samples()).thenReturn(List.of(
        new FssComplaintHelperService.ComplaintSample("FS-1", "EBK-6-3 / 3-1", "L713-733",
            "채권추심 및 법적 절차 중지 요청", "상황 설명", "제목", "본문 1", "본문 1"),
        new FssComplaintHelperService.ComplaintSample("FS-2", "EBK-6-3 / 3-2", "L737-775",
            "상계처리 중단 요청", "상황 설명", "제목", "본문 2", "본문 2"),
        new FssComplaintHelperService.ComplaintSample("FS-3", "EBK-6-3 / 3-3", "L779-823",
            "신청 예정자 추심 중단 요청", "상황 설명", "제목", "본문 3", "본문 3")));
    when(fssComplaintHelperService.manual()).thenReturn(
        new FssComplaintHelperService.ManualGuide("EBK-6-1 / EBK-6-2", "인트로",
            List.of(new FssComplaintHelperService.ManualStep(1, "민원을 넣는다", "전자책 문장", null)),
            "note", null));

    mockMvc.perform(get("/api/newstart/fss/companion").cookie(userCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.samples.length()").value(3))
        .andExpect(jsonPath("$.samples[0].id").value("FS-1"))
        .andExpect(jsonPath("$.samples[0].ebookRef").value("EBK-6-3 / 3-1"))
        .andExpect(jsonPath("$.manual.steps[0].action").value("민원을 넣는다"))
        // Guided Agent 14단계 안내가 companion 1회 응답에 함께 실린다(정적 문구, 개인정보 없음).
        .andExpect(jsonPath("$.agent.steps.length()").value(14))
        .andExpect(jsonPath("$.agent.pcChromeNotice")
            .value("금감원 민원 Agent는 PC의 Chrome 브라우저에서 이용할 수 있습니다."));
  }

  @Test
  void composeSuccessReturnsTextAndModel() throws Exception {
    when(fssComplaintHelperService.compose(eq("FS-1"), any())).thenReturn(
        new FssComplaintHelperService.ComposeResult(true, "정리된 민원문입니다.", "",
            "정리된 민원문입니다.", null, "test-model"));

    mockMvc.perform(post("/api/newstart/fss/compose")
            .cookie(userCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sampleId\":\"FS-1\",\"situationText\":\"추심 문자가 와요\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.composedText").value("정리된 민원문입니다."))
        .andExpect(jsonPath("$.model").value("test-model"));
  }

  @Test
  @DisplayName("interview 페이로드가 있으면 자유서술이 아니라 guided interview 경로로 라우팅된다")
  void composeWithInterviewRoutesToInterviewPath() throws Exception {
    when(fssComplaintHelperService.composeInterview(any())).thenReturn(
        new FssComplaintHelperService.ComposeResult(true,
            "제목: 추심 연락 절차 확인 요청\n본문:\n사건 개요 ...",
            "추심 연락 절차 확인 요청", "사건 개요 ...", null, "test-model"));

    mockMvc.perform(post("/api/newstart/fss/compose")
            .cookie(userCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"interview\":{\"category\":\"COLLECTION_CONTACT\",\"facts\":\"매일 전화가 와요\","
                + "\"company\":\"OO채권관리\",\"events\":[{\"date\":\"2026-09-01\",\"party\":\"OO채권관리\","
                + "\"happened\":\"전화 수신\",\"evidence\":\"통화기록\"}],"
                + "\"contacts\":\"변제 안 하면 법적 조치라고 했음\",\"ownResponses\":\"아직 없음\","
                + "\"evidence\":[\"통화기록\"],\"outcome\":\"연락 빈도 제한\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.subject").value("추심 연락 절차 확인 요청"))
        .andExpect(jsonPath("$.body").value("사건 개요 ..."))
        .andExpect(jsonPath("$.model").value("test-model"));
    verify(fssComplaintHelperService, never()).compose(any(), any());
  }

  @Test
  void composeFailureKeepsUserTextOutOfResponse() throws Exception {
    when(fssComplaintHelperService.compose(any(), any())).thenReturn(
        FssComplaintHelperService.ComposeResult.failed("AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요."));

    mockMvc.perform(post("/api/newstart/fss/compose")
            .cookie(userCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sampleId\":\"FS-1\",\"situationText\":\"민원원문비밀마커-CTRL99 포함 문장\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.composedText").value(""))
        .andExpect(jsonPath("$.errorMessage").value("AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요."))
        .andExpect(result -> org.assertj.core.api.Assertions
            .assertThat(result.getResponse().getContentAsString())
            .doesNotContain("민원원문비밀마커-CTRL99"));
  }
}
