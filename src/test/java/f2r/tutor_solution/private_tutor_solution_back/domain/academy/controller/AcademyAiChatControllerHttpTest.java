package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageVersion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAiActionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAiSessionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiApplyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiChatService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiContextService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiCreateService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiIdempotencyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyPageVersionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import jakarta.servlet.http.Cookie;
import java.util.Map;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * BuildIt P1 AI 채팅 컨트롤러 HTTP 테스트.
 *
 * <p>/create, /create/apply, /apply, /restore 엔드포인트의 HTTP 응답을 검증한다.
 * 서비스는 모킹하고, 컨트롤러의 요청 검증·상태 코드·에러 응답에 집중한다.
 */
@WebMvcTest(controllers = AcademyAiChatController.class)
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
@DisplayName("Academy AI 채팅 컨트롤러 HTTP 테스트")
class AcademyAiChatControllerHttpTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean AcademyAiChatService chatService;
  @MockitoBean AcademyAiCreateService createService;
  @MockitoBean AcademyAiContextService contextService;
  @MockitoBean AcademyPageVersionService versionService;
  @MockitoBean AcademyService academyService;
  @MockitoBean AcademyAiSessionRepository sessionRepository;
  @MockitoBean AcademyAiActionRepository actionRepository;
  @MockitoBean AcademyAiIdempotencyService idempotencyService;
  @MockitoBean AcademyAiApplyService applyService;

  private Cookie tutorCookie() {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "tutor", "TUTOR"));
  }

  @Test
  @DisplayName("POST /create: 인증 없이 요청 시 4xx")
  void create_anonymous_isRejected() throws Exception {
    mockMvc.perform(post("/api/tutor/academy/ai/chat/create")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @DisplayName("POST /create/apply: idempotencyKey 누락 시 400")
  void createApply_missingKey_returns400() throws Exception {
    // academyService.getMine이 빈 값을 반환하도록 (Academy 없음)
    when(academyService.getMine(any())).thenReturn(Optional.empty());

    mockMvc.perform(post("/api/tutor/academy/ai/chat/create/apply")
            .cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"academyName\":\"테스트학원\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /create/apply: 중복 키 시 409")
  void createApply_duplicateKey_returns409() throws Exception {
    when(academyService.getMine(any())).thenReturn(Optional.empty());
    // claim에서 DuplicateIdempotencyKeyException 발생 시뮬레이션
    when(idempotencyService.claim(any(), any()))
        .thenThrow(new AcademyAiIdempotencyService.DuplicateIdempotencyKeyException(
            new AcademyAiIdempotency("1", "dup-key")));

    mockMvc.perform(post("/api/tutor/academy/ai/chat/create/apply")
            .cookie(tutorCookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"idempotencyKey\":\"dup-key\",\"academyName\":\"테스트학원\",\"intro\":\"소개\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("POST /apply: 인증 없이 요청 시 4xx")
  void apply_anonymous_isRejected() throws Exception {
    mockMvc.perform(post("/api/tutor/academy/ai/chat/apply")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sessionId\":\"1\",\"approved\":true}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @DisplayName("POST /versions/{id}/restore: 존재하지 않는 버전 시 4xx/5xx")
  void restore_nonExistentVersion_returnsError() throws Exception {
    when(versionService.getForRestore(any(), any()))
        .thenThrow(new IllegalArgumentException("버전을 찾을 수 없습니다: 999"));

    mockMvc.perform(post("/api/tutor/academy/ai/chat/versions/999/restore")
            .cookie(tutorCookie()))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @DisplayName("POST /versions/{id}/restore: 타인 버전 접근 시 403")
  void restore_otherAcademyVersion_returns403() throws Exception {
    when(versionService.getForRestore(any(), any()))
        .thenThrow(new org.springframework.security.access.AccessDeniedException(
            "다른 학원의 버전에 접근할 수 없습니다."));

    mockMvc.perform(post("/api/tutor/academy/ai/chat/versions/1/restore")
            .cookie(tutorCookie()))
        .andExpect(status().isForbidden());
  }
}
