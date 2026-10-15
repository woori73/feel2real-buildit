package f2r.tutor_solution.private_tutor_solution_back.config.security;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller.ConversationPageController;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller.StudentConversationPageController;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.ConversationDetailResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service.ConversationService;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.stereotype.Controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = {
    ConversationChatSecurityIntegrationTest.EmptyController.class,
    ConversationPageController.class,
    StudentConversationPageController.class
})
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
@DisplayName("AI Chat 화면 권한 테스트")
class ConversationChatSecurityIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean
  ConversationService conversationService;

  @Test
  @DisplayName("학생이 AI Chat URL에 직접 접근하면 학생 대시보드로 리다이렉트된다")
  void studentRedirectedFromConversationChat() throws Exception {
    // given
    Cookie token = token("STUDENT");

    // when & then
    mockMvc.perform(get("/conversation/chat?id=1").cookie(token))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", "/stdt_dashboard"));
  }

  @Test
  @DisplayName("학생이 AI Chat .html URL에 직접 접근해도 학생 대시보드로 리다이렉트된다")
  void studentRedirectedFromConversationChatHtml() throws Exception {
    // given
    Cookie token = token("STUDENT");

    // when & then
    mockMvc.perform(get("/conversation/chat.html?id=1").cookie(token))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", "/stdt_dashboard"));
  }

  @Test
  @DisplayName("ADMIN은 AI Chat 화면을 볼 수 있다")
  void adminCanOpenConversationChat() throws Exception {
    // given
    Cookie token = token("ADMIN");

    // when & then
    mockMvc.perform(get("/conversation/chat?id=1").cookie(token))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<!DOCTYPE html>")));
  }

  @Test
  @DisplayName("TUTOR는 AI Chat 화면을 볼 수 있다")
  void tutorCanOpenConversationChat() throws Exception {
    // given
    Cookie token = token("TUTOR");

    // when & then
    mockMvc.perform(get("/conversation/chat?id=1").cookie(token))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<!DOCTYPE html>")));
  }

  @Test
  @DisplayName("대화 대시보드의 AI Chat 메뉴는 STUDENT에게 렌더링되지 않는다")
  void dashboardHidesAiChatMenuForStudent() throws Exception {
    // given
    Cookie token = token("STUDENT");
    when(conversationService.getDetail(1L)).thenReturn(detail());

    // when & then
    mockMvc.perform(get("/conversation/dashboard?id=1").cookie(token))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("AI Chat"))));
  }

  @Test
  @DisplayName("대화 대시보드의 AI Chat 메뉴는 ADMIN에게 렌더링된다")
  void dashboardShowsAiChatMenuForAdmin() throws Exception {
    // given
    Cookie token = token("ADMIN");
    when(conversationService.getDetail(1L)).thenReturn(detail());

    // when & then
    mockMvc.perform(get("/conversation/dashboard?id=1").cookie(token))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("AI Chat")));
  }

  private Cookie token(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "test-" + role.toLowerCase(), role));
  }

  private static ConversationDetailResponse detail() {
    return new ConversationDetailResponse(
        1L,
        "대화",
        LocalDateTime.now(),
        null,
        List.of(),
        List.of(),
        null,
        null,
        null);
  }

  @Controller
  static class EmptyController {
  }
}
