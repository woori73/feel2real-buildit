package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAiChatService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartKnowledgeContextService;
import jakarta.servlet.http.Cookie;
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
 * NewStart AI chat endpoint 계약 테스트. 인증은 기존 {@code /api/**} catch-all(인증 필요)을 그대로
 * 따르는지, 성공/실패 응답이 내부 상세를 노출하지 않는지 검증한다.
 */
@WebMvcTest(controllers = NewStartAiChatController.class)
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
class NewStartAiChatControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean NewStartAiChatService newStartAiChatService;

  private Cookie userCookie() {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "student", "STUDENT"));
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
        new NewStartAiChatService.NewStartAiAnswer(
            true, "새출발기금은 ...입니다.", null, "qwen/qwen3.7-flash", java.util.List.of()));

    mockMvc.perform(post("/api/newstart/ai/chat")
            .cookie(userCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"새출발기금이 무엇인가요?\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.answer").value("새출발기금은 ...입니다."))
        .andExpect(jsonPath("$.model").value("qwen/qwen3.7-flash"))
        .andExpect(jsonPath("$.sources").isArray())
        .andExpect(jsonPath("$.sources").isEmpty());
  }

  @Test
  void chatReturnsSourcesWhenGroundedOnKnowledge() throws Exception {
    NewStartKnowledgeContextService.SourceInfo source =
        new NewStartKnowledgeContextService.SourceInfo(
            7L, "OFFICIAL_CURRENT", "새출발기금 감면율 기준", "https://www.newstartfund.or.kr/guide",
            java.time.LocalDate.of(2026, 1, 1), "새출발기금", "감면율");
    when(newStartAiChatService.ask(anyString())).thenReturn(
        new NewStartAiChatService.NewStartAiAnswer(
            true, "감면율 기준은 ...입니다.", null, "qwen/qwen3.7-flash", java.util.List.of(source)));

    mockMvc.perform(post("/api/newstart/ai/chat")
            .cookie(userCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"감면율이 어떻게 되나요?\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sources[0].knowledgeId").value(7))
        .andExpect(jsonPath("$.sources[0].sourceType").value("OFFICIAL_CURRENT"))
        .andExpect(jsonPath("$.sources[0].title").value("새출발기금 감면율 기준"))
        .andExpect(jsonPath("$.sources[0].sourceUrl").value("https://www.newstartfund.or.kr/guide"));
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
}
