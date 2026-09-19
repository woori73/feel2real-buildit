package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartChatService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartChatService.NewStartChatDeleteResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartChatService.NewStartChatHistory;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartChatService.NewStartChatMessageView;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * NewStart 회원 채팅 REST 계약 테스트. 인증은 기존 {@code /api/**} catch-all을 그대로 따르는지,
 * 삭제 권한 계약(본인 200 / 타인 403 / 이상 400)을 검증한다. 전송은 WS라 여기 없다.
 */
@WebMvcTest(controllers = NewStartChatController.class)
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
class NewStartChatControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean NewStartChatService newStartChatService;

  private Cookie userCookie() {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "student", "STUDENT"));
  }

  private NewStartChatMessageView view(long id) {
    return new NewStartChatMessageView(id, 2L, "별명2", "메시지" + id, false, "2026-09-19T09:00:00");
  }

  @Test
  void anonymousHistoryRequestIsRejected() throws Exception {
    mockMvc.perform(get("/api/newstart/chat/messages"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void anonymousDeleteRequestIsRejected() throws Exception {
    mockMvc.perform(delete("/api/newstart/chat/messages/1"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void messagesReturnsHistory() throws Exception {
    when(newStartChatService.history(isNull(), any())).thenReturn(
        new NewStartChatHistory(List.of(view(3L), view(4L)), true));

    mockMvc.perform(get("/api/newstart/chat/messages").cookie(userCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages.length()").value(2))
        .andExpect(jsonPath("$.messages[0].displayName").value("별명2"))
        .andExpect(jsonPath("$.messages[0].senderUserId").value(2))
        .andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  void messagesSupportsBeforePaging() throws Exception {
    when(newStartChatService.history(eq(3L), any())).thenReturn(
        new NewStartChatHistory(List.of(view(1L)), false));

    mockMvc.perform(get("/api/newstart/chat/messages").cookie(userCookie())
            .param("before", "3").param("limit", "30"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[0].id").value(1))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  void afterReturnsRecoveryMessages() throws Exception {
    when(newStartChatService.after(5L)).thenReturn(List.of(view(6L)));

    mockMvc.perform(get("/api/newstart/chat/after").cookie(userCookie()).param("after", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(6));
  }

  @Test
  void deleteOwnMessageReturnsOk() throws Exception {
    when(newStartChatService.delete(any(), eq(7L))).thenReturn(NewStartChatDeleteResult.success());

    mockMvc.perform(delete("/api/newstart/chat/messages/7").cookie(userCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }

  @Test
  void deleteOthersMessageReturnsForbidden() throws Exception {
    when(newStartChatService.delete(any(), eq(8L))).thenReturn(
        NewStartChatDeleteResult.failed("본인이 작성한 메시지만 삭제할 수 있습니다.", true));

    mockMvc.perform(delete("/api/newstart/chat/messages/8").cookie(userCookie()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.errorMessage").value("본인이 작성한 메시지만 삭제할 수 있습니다."));
  }

  @Test
  void deleteUnknownMessageReturnsBadRequest() throws Exception {
    when(newStartChatService.delete(any(), eq(9L))).thenReturn(
        NewStartChatDeleteResult.failed("이미 삭제되었거나 존재하지 않는 메시지입니다.", false));

    mockMvc.perform(delete("/api/newstart/chat/messages/9").cookie(userCookie()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("error"));
  }
}
