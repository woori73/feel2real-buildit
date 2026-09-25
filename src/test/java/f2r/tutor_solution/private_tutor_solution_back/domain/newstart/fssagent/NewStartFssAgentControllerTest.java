package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.fssagent;

import static org.mockito.ArgumentMatchers.anyLong;
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
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guided Agent endpoint 계약 — 익명 차단(기존 /api/** catch-all 재사용), workflow 14단계 payload,
 * advance/back/restart는 요청 body 없이 동작하며 응답에 개인정보성 필드가 없다.
 */
@WebMvcTest(controllers = NewStartFssAgentController.class)
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
class NewStartFssAgentControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean FssGuidedAgentService guidedAgentService;

  private Cookie userCookie() {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "student", "STUDENT"));
  }

  @Test
  void anonymousRequestsAreRejected() throws Exception {
    mockMvc.perform(get("/api/newstart/fss/agent/session"))
        .andExpect(status().is4xxClientError());
    mockMvc.perform(post("/api/newstart/fss/agent/advance"))
        .andExpect(status().is4xxClientError());
    mockMvc.perform(post("/api/newstart/fss/agent/back"))
        .andExpect(status().is4xxClientError());
    mockMvc.perform(post("/api/newstart/fss/agent/restart"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void workflowReturnsFourteenStepsWithNoticeAndEntryUrl() throws Exception {
    mockMvc.perform(get("/api/newstart/fss/agent/workflow").cookie(userCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.steps.length()").value(14))
        .andExpect(jsonPath("$.steps[0].number").value(1))
        .andExpect(jsonPath("$.steps[13].number").value(14))
        .andExpect(jsonPath("$.pcChromeNotice")
            .value("금감원 민원 Agent는 PC의 Chrome 브라우저에서 이용할 수 있습니다."))
        .andExpect(jsonPath("$.officialEntryUrl").value(org.hamcrest.Matchers.startsWith(
            "https://www.fss.or.kr/fss/minwon/search.jsp")));
  }

  @Test
  void advanceWorksWithoutRequestBody() throws Exception {
    when(guidedAgentService.completeCurrentStep(anyLong()))
        .thenReturn(new FssGuidedSession(2, false));

    mockMvc.perform(post("/api/newstart/fss/agent/advance").cookie(userCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentStep").value(2))
        .andExpect(jsonPath("$.finished").value(false))
        .andExpect(jsonPath("$.totalSteps").value(14))
        .andExpect(jsonPath("$.stepTitle").value(FssGuidedWorkflow.step(2).title()));
  }

  @Test
  void sessionResponseFieldsAreOnlyStepState() throws Exception {
    when(guidedAgentService.session(anyLong())).thenReturn(new FssGuidedSession(1, false));

    mockMvc.perform(get("/api/newstart/fss/agent/session").cookie(userCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.*").isArray())
        // 사용자 입력·화면 내용·인증 정보가 실릴 필드 자체가 없다 — 5개 키만 허용.
        .andExpect(result -> {
          java.util.Map<String, Object> root = new com.fasterxml.jackson.databind.ObjectMapper()
              .readValue(result.getResponse().getContentAsString(), java.util.Map.class);
          org.assertj.core.api.Assertions.assertThat(root.keySet())
              .containsExactlyInAnyOrder("status", "currentStep", "finished", "totalSteps", "stepTitle");
        });
  }
}
