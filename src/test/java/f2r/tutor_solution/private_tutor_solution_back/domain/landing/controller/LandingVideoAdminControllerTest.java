package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.GlobalExceptionHandler;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.LandingVideoAdminResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.service.LandingVideoService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("LandingVideoAdminController 계약")
class LandingVideoAdminControllerTest {

  private LandingVideoService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(LandingVideoService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new LandingVideoAdminController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("GET은 fixed-order response 배열을 그대로 반환한다")
  void listContract() throws Exception {
    // given
    when(service.listAdmin())
        .thenReturn(
            List.of(
                LandingVideoAdminResponse.bundled(LandingVideoSlotKey.HERO_BACKGROUND, true),
                LandingVideoAdminResponse.bundled(
                    LandingVideoSlotKey.ACADEMY_SHORTS_DEMO, true),
                LandingVideoAdminResponse.bundled(
                    LandingVideoSlotKey.PARENT_REPORT_DEMO, false)));

    // when / then
    mockMvc.perform(get("/api/admin/landing/videos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].slot").value("HERO_BACKGROUND"))
        .andExpect(jsonPath("$[0].sourceType").value("BUNDLED"))
        .andExpect(jsonPath("$[0].storageKey").doesNotExist())
        .andExpect(jsonPath("$[2].visible").value(false));
  }

  @Test
  @DisplayName("PUT multipart part 이름은 video이고 slot을 서비스로 전달한다")
  void uploadContract() throws Exception {
    // given
    MockMultipartFile video =
        new MockMultipartFile("video", "clip.mp4", "video/mp4", new byte[] {1});
    var response =
        LandingVideoAdminResponse.bundled(LandingVideoSlotKey.HERO_BACKGROUND, true);
    when(service.upload(eq(LandingVideoSlotKey.HERO_BACKGROUND), any(), any()))
        .thenReturn(response);

    // when / then
    mockMvc.perform(
            multipart("/api/admin/landing/videos/HERO_BACKGROUND")
                .file(video)
                .with(request -> {
                  request.setMethod("PUT");
                  return request;
                }))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slot").value("HERO_BACKGROUND"));
    verify(service).upload(eq(LandingVideoSlotKey.HERO_BACKGROUND), any(), any());
  }

  @Test
  @DisplayName("PATCH visible은 non-null이며 missing/null은 400")
  void visibilityRequiresNonNullBoolean() throws Exception {
    // given
    when(service.updateVisibility(LandingVideoSlotKey.HERO_BACKGROUND, false))
        .thenReturn(
            LandingVideoAdminResponse.bundled(LandingVideoSlotKey.HERO_BACKGROUND, false));

    // when / then
    mockMvc.perform(
            patch("/api/admin/landing/videos/HERO_BACKGROUND/visibility")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visible\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.visible").value(false));

    mockMvc.perform(
            patch("/api/admin/landing/videos/HERO_BACKGROUND/visibility")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(
            patch("/api/admin/landing/videos/HERO_BACKGROUND/visibility")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visible\":null}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("유효하지 않은 slot path는 400")
  void invalidSlotIsBadRequest() throws Exception {
    mockMvc.perform(get("/api/admin/landing/videos/NOT_A_SLOT/stream"))
        .andExpect(status().isBadRequest());
  }
}
