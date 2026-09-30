package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAiChatService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartEntitlementService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
 * NewStart AI chat endpoint 계약 테스트. §7 경계에 따라 실제 질문 실행은 THANKS_MEMBER 전용 —
 * 비로그인 401, grant 없는 회원 403, grant 보유 회원만 AI 호출이 진행된다.
 * 성공/실패 응답이 내부 상세를 노출하지 않는지도 본다.
 */
@WebMvcTest(controllers = NewStartAiChatController.class)
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
class NewStartAiChatControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean NewStartAiChatService newStartAiChatService;
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
  void anonymousRequestIsRejected() throws Exception {
    mockMvc.perform(post("/api/newstart/ai/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"질문\"}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void chatReturnsOkWithAnswer() throws Exception {
    when(newStartAiChatService.ask(anyString())).thenReturn(
        new NewStartAiChatService.NewStartAiAnswer(true, "새출발기금은 ...입니다.", null, "qwen/qwen3.7-flash",
            java.util.List.of(new NewStartAiChatService.AnswerSource("공식 자료", "공식 안내", "https://x/y"))));

    mockMvc.perform(post("/api/newstart/ai/chat")
            .cookie(userCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"새출발기금이 무엇인가요?\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.answer").value("새출발기금은 ...입니다."))
        .andExpect(jsonPath("$.model").value("qwen/qwen3.7-flash"))
        .andExpect(jsonPath("$.sources[0].kind").value("공식 자료"))
        .andExpect(jsonPath("$.sources[0].title").value("공식 안내"));
  }

  @Test
  void chatRejectsBlankQuestionWith400() throws Exception {
    when(newStartAiChatService.ask(anyString())).thenReturn(
        NewStartAiChatService.NewStartAiAnswer.failed("질문을 입력해 주세요."));

    mockMvc.perform(post("/api/newstart/ai/chat")
            .cookie(userCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.errorMessage").value("질문을 입력해 주세요."));
  }

  @Test
  void chatFailureMessageDoesNotLeakInternalDetails() throws Exception {
    when(newStartAiChatService.ask(any())).thenReturn(
        NewStartAiChatService.NewStartAiAnswer.failed("AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요."));

    mockMvc.perform(post("/api/newstart/ai/chat")
            .cookie(userCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"질문\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.errorMessage").value("AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요."))
        .andExpect(jsonPath("$.answer").value(""));
  }

  @Test
  void memberWithoutGrantIsRejectedWithoutCallingAi() throws Exception {
    when(grantRepository.existsByUserIdAndCapability(anyLong(), any(NewStartCapability.class)))
        .thenReturn(false);

    mockMvc.perform(post("/api/newstart/ai/chat")
            .cookie(userCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"새출발기금이 무엇인가요?\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.errorMessage").value(NewStartEntitlementService.DENIAL_MESSAGE));

    verify(newStartAiChatService, never()).ask(any());
  }

  @Test
  void adminPassesWithoutGrantRow() throws Exception {
    when(grantRepository.existsByUserIdAndCapability(anyLong(), any(NewStartCapability.class)))
        .thenReturn(false);
    when(newStartAiChatService.ask(anyString())).thenReturn(
        new NewStartAiChatService.NewStartAiAnswer(true, "답변", null, "test-model", java.util.List.of()));

    // given: ADMIN은 capability row 없이 운영자 = ALL로 통과한다.
    mockMvc.perform(post("/api/newstart/ai/chat")
            .cookie(new Cookie("ACCESS_TOKEN", tokenProvider.issue(9L, "admin", "ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"질문\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }

  @Test
  void chatMarksStageCaseAndOfficialEndpointMarksStageOfficial() throws Exception {
    when(newStartAiChatService.ask(anyString())).thenReturn(
        new NewStartAiChatService.NewStartAiAnswer(true, "1차", null, "m", java.util.List.of()));
    when(newStartAiChatService.askOfficial(anyString(), anyString())).thenReturn(
        new NewStartAiChatService.NewStartAiAnswer(true, "2차", null, "m",
            java.util.List.of(new NewStartAiChatService.AnswerSource("공식 자료", "공식", "https://x"))));

    mockMvc.perform(post("/api/newstart/ai/chat").cookie(userCookie())
            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"질문\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("CASE")).andExpect(jsonPath("$.answer").value("1차"));

    mockMvc.perform(post("/api/newstart/ai/chat/official").cookie(userCookie())
            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"질문\",\"step1Answer\":\"1차\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("OFFICIAL"))
        .andExpect(jsonPath("$.answer").value("2차")).andExpect(jsonPath("$.sources[0].kind").value("공식 자료"));

    verify(newStartAiChatService).askOfficial("질문", "1차");
  }

  @Test
  void officialStepRequiresLoginAndGrantAndFailureIsStepScoped() throws Exception {
    mockMvc.perform(post("/api/newstart/ai/chat/official").contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"질문\",\"step1Answer\":\"1차\"}"))
        .andExpect(status().is4xxClientError());

    when(grantRepository.existsByUserIdAndCapability(anyLong(), any(NewStartCapability.class))).thenReturn(false);
    mockMvc.perform(post("/api/newstart/ai/chat/official").cookie(userCookie())
            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"질문\",\"step1Answer\":\"1차\"}"))
        .andExpect(status().isForbidden());
    verify(newStartAiChatService, never()).askOfficial(any(), any());

    when(grantRepository.existsByUserIdAndCapability(anyLong(), any(NewStartCapability.class))).thenReturn(true);
    when(newStartAiChatService.askOfficial(any(), any())).thenReturn(
        NewStartAiChatService.NewStartAiAnswer.failed("AI 서비스 응답이 지연되고 있습니다."));
    mockMvc.perform(post("/api/newstart/ai/chat/official").cookie(userCookie())
            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"질문\",\"step1Answer\":\"1차\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.stage").doesNotExist());
  }
}
