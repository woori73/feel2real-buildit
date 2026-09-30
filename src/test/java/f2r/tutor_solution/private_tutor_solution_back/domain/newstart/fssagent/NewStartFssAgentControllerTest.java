package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.fssagent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberGrantRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartEntitlementService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guided Agent endpoint 계약 — §7 접근 경계의 API측 증거.
 *
 * <p>14단계 안내 {@code /workflow}는 익명에게도 공개(정적 문구, 개인정보 없음), 세션 실행
 * 4종은 "비로그인 401 → 권한 없는 회원 403 → grant 보유 회원만 200"의 3단으로 갈린다.
 * advance/back/restart는 요청 body 없이 동작하며 응답에 개인정보성 필드가 없다.
 */
@WebMvcTest(controllers = NewStartFssAgentController.class)
@AutoConfigureMockMvc
@Import({
    SecurityConfig.class,
    WebMvcConfig.class,
    RoleModelInterceptor.class,
    JwtTokenProvider.class,
    CookieUtil.class,
    NewStartEntitlementService.class
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
  @MockitoBean NewStartMemberGrantRepository grantRepository;

  private Cookie userCookie() {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "student", "STUDENT"));
  }

  /** 기본 시나리오는 "grant 보유 회원" — 거절 케이스만 각 테스트에서 false로 되돌린다. */
  @BeforeEach
  void memberHoldsThanksGrant() {
    when(grantRepository.existsByUserIdAndCapability(anyLong(), any(NewStartCapability.class)))
        .thenReturn(true);
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
    // 설명 공개(§7): grant 여부와 무관하게, 인증 없이도 14단계 안내 전문을 읽을 수 있다.
    mockMvc.perform(get("/api/newstart/fss/agent/workflow"))
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

  @Test
  void memberWithoutGrantIsBlockedFromRunningAgent() throws Exception {
    when(grantRepository.existsByUserIdAndCapability(anyLong(), any(NewStartCapability.class)))
        .thenReturn(false);

    mockMvc.perform(get("/api/newstart/fss/agent/session").cookie(userCookie()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.errorMessage").value(NewStartEntitlementService.DENIAL_MESSAGE));
    mockMvc.perform(post("/api/newstart/fss/agent/advance").cookie(userCookie()))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/newstart/fss/agent/restart").cookie(userCookie()))
        .andExpect(status().isForbidden());

    // 거절되면 세션 자체가 만들어지지 않는다(권한 검사 선행).
    verify(guidedAgentService, never()).session(anyLong());
    verify(guidedAgentService, never()).completeCurrentStep(anyLong());
    verify(guidedAgentService, never()).restart(anyLong());
    // 단, 안내 문구는 권한과 무관하게 계속 공개된다.
    mockMvc.perform(get("/api/newstart/fss/agent/workflow").cookie(userCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.steps.length()").value(14));
  }
}
