package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyMediaService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoDeliveryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoPurpose;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoActiveSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.Cookie;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AcademyVideoController.class)
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
class AcademyVideoStreamControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean AcademyVideoService videoService;
  @MockitoBean AcademyVideoDeliveryService deliveryService;
  @MockitoBean AcademyMediaService mediaService;

  @Test
  void anonymousGetForwardsRangeAndStreamsDynamicMediaType() throws Exception {
    StreamingResponseBody body = output -> output.write(new byte[] {1, 2, 3, 4});
    when(deliveryService.get("published", 7L, "bytes=0-3")).thenReturn(
        ResponseEntity.status(206)
            .contentType(MediaType.parseMediaType("video/webm"))
            .contentLength(4)
            .header(HttpHeaders.CONTENT_RANGE, "bytes 0-3/10")
            .body(body));

    mockMvc.perform(get("/api/academies/published/videos/7/stream")
            .header(HttpHeaders.RANGE, "bytes=0-3"))
        .andExpect(status().isPartialContent())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/webm"))
        .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-3/10"))
        .andExpect(request().asyncStarted());
  }

  @Test
  void anonymousHeadReturnsMetadataWithoutRedirectOrBody() throws Exception {
    when(deliveryService.head("published", 7L)).thenReturn(ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType("video/quicktime"))
        .contentLength(10)
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .build());

    mockMvc.perform(head("/api/academies/published/videos/7/stream"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/quicktime"))
        .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "10"))
        .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
  }

  @Test
  void tutorMultipartUploadUsesServerSideVideoService() throws Exception {
    Cookie tutor = new Cookie(
        "ACCESS_TOKEN", tokenProvider.issue(1L, "tutor", "TUTOR"));
    when(videoService.upload(any(), any(), any())).thenReturn(new AcademyVideoResponse(
        7L, "clip.mp4", "/api/academies/my/videos/7/stream",
        "/api/academies/my/videos/7/thumbnail", 8, 8_000L, 720, 1280,
        VideoStatus.READY, VideoLifecycleStatus.READY, null,
        VideoActiveSource.NORMALIZED, AcademyVideoPurpose.LESSON,
        f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoSourceKind.DIRECT_UPLOAD,
        null, null, null, false, java.util.List.of(), LocalDateTime.now()));

    mockMvc.perform(multipart("/api/tutor/academy/videos")
            .file("video", new byte[] {1, 2, 3})
            .cookie(tutor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.videoId").value(7))
        .andExpect(jsonPath("$.activeSource").value("NORMALIZED"));
  }

  @Test
  void tutorPatchPurposeReassignsExistingVideo() throws Exception {
    Cookie tutor = new Cookie(
        "ACCESS_TOKEN", tokenProvider.issue(1L, "tutor", "TUTOR"));
    when(videoService.updatePurpose(any(), eq(7L), eq("DIRECTOR"))).thenReturn(new AcademyVideoResponse(
        7L, "clip.mp4", "/api/academies/my/videos/7/stream",
        "/api/academies/my/videos/7/thumbnail", 8, 8_000L, 720, 1280,
        VideoStatus.READY, VideoLifecycleStatus.READY, null,
        VideoActiveSource.NORMALIZED, AcademyVideoPurpose.DIRECTOR,
        f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoSourceKind.DIRECT_UPLOAD,
        null, null, null, false, java.util.List.of(), LocalDateTime.now()));

    mockMvc.perform(patch("/api/tutor/academy/videos/7/purpose")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"purpose\":\"DIRECTOR\"}")
            .cookie(tutor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.videoId").value(7))
        .andExpect(jsonPath("$.purpose").value("DIRECTOR"));
  }
}
