package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiSuggestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyCompletionResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiTaskType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiContextService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiSuggestionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyCompletionCheckService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization 경계 검증: 이 컨트롤러의 어떤 엔드포인트도 academyId를 받지 않는다 — 인증
 * 여부(TUTOR role)만 이 계층의 관심사이고, "어느 Academy인지"는 principal로부터만 정해진다
 * (service 계층의 {@code requireMyAcademy}가 실제 scoping을 보장 — 별도 단위테스트에서 확인).
 */
@WebMvcTest(controllers = AcademyAiController.class)
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
class AcademyAiControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean AcademyAiContextService contextService;
  @MockitoBean AcademyAiSuggestionService suggestionService;
  @MockitoBean AcademyCompletionCheckService completionCheckService;
  @MockitoBean AiProvider aiProvider;

  private Cookie tutorCookie() {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "tutor", "TUTOR"));
  }

  @Test
  void anonymousRequestToContextIsRejected() throws Exception {
    mockMvc.perform(get("/api/tutor/academy/ai/context"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void anonymousRequestToSuggestionsIsRejected() throws Exception {
    mockMvc.perform(post("/api/tutor/academy/ai/suggestions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"task\":\"PRINCIPAL_INTRO\",\"target\":\"principalDesc\",\"currentValue\":\"x\"}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void authenticatedTutorCanReadOwnContext() throws Exception {
    when(contextService.buildContext(any())).thenReturn(new AcademyAiContext(
        "학원", "소개", List.of("영어"), List.of("MIDDLE"), "김원장", "원장", "소개문", List.of("", "", "", ""), List.of()));

    mockMvc.perform(get("/api/tutor/academy/ai/context").cookie(tutorCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.academyName").value("학원"));
  }

  @Test
  void authenticatedTutorCanGenerateSuggestion() throws Exception {
    when(suggestionService.generate(any(), any(), any(), any(), any())).thenReturn(
        new AcademyAiSuggestion(AcademyAiTaskType.PRINCIPAL_INTRO, "principalDesc", "현재", "추천"));

    mockMvc.perform(post("/api/tutor/academy/ai/suggestions")
            .cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"task\":\"PRINCIPAL_INTRO\",\"target\":\"principalDesc\",\"currentValue\":\"현재\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.suggestedValue").value("추천"));
  }

  @Test
  void unknownTaskIsRejectedBeforeReachingService() throws Exception {
    mockMvc.perform(post("/api/tutor/academy/ai/suggestions")
            .cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"task\":\"DELETE_ACADEMY\",\"target\":\"principalDesc\",\"currentValue\":\"x\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void authenticatedTutorCanReadCompletion() throws Exception {
    when(completionCheckService.evaluate(any())).thenReturn(
        new AcademyCompletionResult(50, List.of(
            new AcademyCompletionResult.CompletionItem("intro", "학원 소개", true))));

    mockMvc.perform(get("/api/tutor/academy/ai/completion").cookie(tutorCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scorePercent").value(50));
  }

  @Test
  void statusEndpointNeverExposesRawKey() throws Exception {
    when(aiProvider.name()).thenReturn("gemini");
    when(aiProvider.isReady()).thenReturn(true);

    mockMvc.perform(get("/api/tutor/academy/ai/status").cookie(tutorCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.provider").value("gemini"))
        .andExpect(jsonPath("$.ready").value(true))
        .andExpect(jsonPath("$.apiKey").doesNotExist())
        .andExpect(jsonPath("$.key").doesNotExist());
  }
}
