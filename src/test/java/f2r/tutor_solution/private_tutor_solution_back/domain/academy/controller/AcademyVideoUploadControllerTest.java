package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.InitiateResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.PartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.SessionResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.SessionObservation;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoUploadSessionState;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoUploadApiException;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoUploadSessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AcademyVideoUploadController.class)
@AutoConfigureMockMvc
@Import({
    SecurityConfig.class,
    WebMvcConfig.class,
    RoleModelInterceptor.class,
    JwtTokenProvider.class,
    CookieUtil.class,
    AcademyVideoUploadExceptionHandler.class
})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("Academy video upload HTTP/security contract")
class AcademyVideoUploadControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean AcademyVideoUploadSessionService service;

  @Test
  @DisplayName("given anonymous or STUDENT when initiate then 401/403")
  void initiate_requires_tutor() throws Exception {
    // given / when / then
    mockMvc.perform(post("/api/tutor/academy/videos/uploads")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"filename\":\"clip.webm\",\"declaredSizeBytes\":3}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/tutor/academy/videos/uploads")
            .cookie(token("STUDENT"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"filename\":\"clip.webm\",\"declaredSizeBytes\":3}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("given TUTOR when initiate then response exposes opaque URL but no storage identity fields")
  void initiate_response_has_no_structured_secrets() throws Exception {
    // given
    when(service.initiate(any(), any())).thenReturn(new InitiateResponse(
        "session", LocalDateTime.now(), LocalDateTime.now().plusMinutes(3),
        20L * 1024 * 1024,
        List.of(new PartCapability(1, "https://opaque.invalid/capability"))));

    // when / then
    mockMvc.perform(post("/api/tutor/academy/videos/uploads")
            .cookie(token("TUTOR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"filename\":\"clip.webm\",\"declaredSizeBytes\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parts[0].uploadUrl")
            .value("https://opaque.invalid/capability"))
        .andExpect(jsonPath("$", not(hasKey("bucket"))))
        .andExpect(jsonPath("$", not(hasKey("storageKey"))))
        .andExpect(jsonPath("$", not(hasKey("uploadId"))));
  }

  @Test
  @DisplayName("given due COMPLETING replay when complete then 202 and Retry-After map exactly")
  void complete_maps_observation_retry() throws Exception {
    // given
    SessionResponse response = new SessionResponse(
        "session", VideoUploadSessionState.COMPLETING, null, "TRANSIENT", 5);
    when(service.complete(any(), anyString(), any())).thenReturn(
        new AcademyVideoUploadSessionService.CompleteResult(response, HttpStatus.ACCEPTED));

    // when / then
    mockMvc.perform(post("/api/tutor/academy/videos/uploads/session/complete")
            .cookie(token("TUTOR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"parts\":[{\"partNumber\":1,\"eTag\":\"etag\"}]}"))
        .andExpect(status().isAccepted())
        .andExpect(header().string("Retry-After", "5"))
        .andExpect(jsonPath("$.state").value("COMPLETING"));
  }

  @Test
  @DisplayName("given manifest conflict when service rejects then safe 409 JSON maps without capability")
  void maps_safe_conflict_error() throws Exception {
    // given
    when(service.complete(any(), anyString(), any())).thenThrow(
        new AcademyVideoUploadApiException(
            HttpStatus.CONFLICT, "UPLOAD_MANIFEST_CONFLICT", "manifest conflict", null));

    // when / then
    mockMvc.perform(post("/api/tutor/academy/videos/uploads/session/complete")
            .cookie(token("TUTOR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"parts\":[{\"partNumber\":1,\"eTag\":\"etag\"}]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("UPLOAD_MANIFEST_CONFLICT"))
        .andExpect(jsonPath("$", not(hasKey("uploadUrl"))));
  }

  @Test
  @DisplayName("given accepted already when abort then 200 accepted outcome; terminal abort returns 204")
  void abort_maps_both_terminal_winners() throws Exception {
    // given
    when(service.abort(any(), anyString()))
        .thenReturn(new AcademyVideoUploadSessionService.CompleteResult(
            new SessionResponse("session", VideoUploadSessionState.ACCEPTED, 7L, null, null),
            HttpStatus.OK))
        .thenReturn(new AcademyVideoUploadSessionService.CompleteResult(
            new SessionResponse("session", VideoUploadSessionState.ABORTED, null, null, null),
            HttpStatus.NO_CONTENT));

    // when / then
    mockMvc.perform(delete("/api/tutor/academy/videos/uploads/session")
            .cookie(token("TUTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acceptedVideoId").value(7));
    mockMvc.perform(delete("/api/tutor/academy/videos/uploads/session")
            .cookie(token("TUTOR")))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("given owned session when observe then safe state and server deadline are returned")
  void observe_maps_safe_refresh_recovery_state() throws Exception {
    LocalDateTime serverNow = LocalDateTime.of(2026, 7, 30, 12, 0);
    when(service.observe(any(), anyString())).thenReturn(new SessionObservation(
        "session", VideoUploadSessionState.COMPLETING, null, "STORAGE_TRANSIENT", 5,
        serverNow, serverNow.plusMinutes(3)));

    mockMvc.perform(get("/api/tutor/academy/videos/uploads/session")
            .cookie(token("TUTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("COMPLETING"))
        .andExpect(jsonPath("$.retryAfterSeconds").value(5))
        .andExpect(jsonPath("$", not(hasKey("uploadUrl"))))
        .andExpect(jsonPath("$", not(hasKey("uploadId"))));
  }

  private Cookie token(String role) {
    return new Cookie(
        "ACCESS_TOKEN", tokenProvider.issue(1L, "test-" + role.toLowerCase(), role));
  }
}
