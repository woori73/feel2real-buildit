package f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiMaterialCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.WikiDownloadRecordingService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.dto.WikiLearningMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.service.WikiLearningMaterialService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.service.WikiLearningMaterialService.DownloadFile;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("WikiLearningMaterialController — 목록 공개 / 다운로드·업로드 회원 / 관리 ADMIN")
@WebMvcTest(controllers = WikiLearningMaterialController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class WikiLearningMaterialControllerWebMvcTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean WikiLearningMaterialService service;
  @MockitoBean AcademyRepository academyRepository;
  @MockitoBean WikiDownloadRecordingService downloadRecordingService;

  @BeforeEach
  void stubs() {
    when(service.searchPublic(any())).thenReturn(List.of(sample()));
    when(service.listAdmin()).thenReturn(List.of(sample()));
    when(service.getDetail(1L)).thenReturn(sample());
    when(service.getPreview(1L)).thenReturn(new DownloadFile("%PDF-1.4".getBytes(), "어휘.pdf", "application/pdf"));
    when(service.getDownload(1L)).thenReturn(new DownloadFile("PDF".getBytes(), "어휘.pdf", "application/pdf"));
    when(service.upload(anyLong(), anyString(), any(), any(), any())).thenReturn(sample());
  }

  @Test
  @DisplayName("given 익명 when 목록/상세/미리보기 then 200 (미리보기는 inline+nosniff)")
  void publicRead_anonymous_ok() throws Exception {
    mockMvc.perform(get("/api/wiki/learning-materials").param("grade", "2")).andExpect(status().isOk());
    mockMvc.perform(get("/api/wiki/learning-materials/1")).andExpect(status().isOk());
    mockMvc.perform(get("/api/wiki/learning-materials/1/preview"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("inline")))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'self'")));
  }

  @Test
  @DisplayName("given 미리보기 불가 형식 when preview then 415")
  void preview_unsupported_type() throws Exception {
    when(service.getPreview(2L)).thenThrow(new org.springframework.web.server.ResponseStatusException(
        org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, "no"));
    mockMvc.perform(get("/api/wiki/learning-materials/2/preview"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  @DisplayName("given 익명 when 다운로드/업로드 then 401 (URL 직접 입력해도 파일 안 나감)")
  void downloadAndUpload_anonymous_unauthorized() throws Exception {
    mockMvc.perform(get("/api/wiki/learning-materials/1/download")).andExpect(status().isUnauthorized());
    mockMvc.perform(multipart("/api/wiki/learning-materials").file("file", "%PDF".getBytes())
            .param("dataType", "VOCAB").param("grade", "2"))
        .andExpect(status().isUnauthorized());

    verify(service, never()).getDownload(anyLong());
    verifyNoInteractions(downloadRecordingService);
  }

  @Test
  @DisplayName("given 회원(STUDENT) when 업로드/다운로드 then 200 + attachment 헤더 + 다운로드 기록(STUDENT)")
  void memberUploadAndDownload_ok() throws Exception {
    mockMvc.perform(multipart("/api/wiki/learning-materials").file("file", "%PDF".getBytes())
            .param("dataType", "VOCAB").param("grade", "2").param("title", "고2 어휘")
            .cookie(token("STUDENT")))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/wiki/learning-materials/1/download").cookie(token("STUDENT")))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
            org.hamcrest.Matchers.containsString("attachment")));

    verify(downloadRecordingService).record(
        eq(WikiMaterialCategory.LEARNING), eq(1L), anyString(), eq(1L), eq("STUDENT"));
  }

  @Test
  @DisplayName("given 회원 when 관리 목록/삭제 then 403 (ADMIN 아님)")
  void adminApi_deniedForMember() throws Exception {
    mockMvc.perform(get("/api/admin/wiki/learning-materials").cookie(token("STUDENT")))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/admin/wiki/learning-materials/1").cookie(token("STUDENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("given ADMIN when 관리 목록/삭제 then 200/204")
  void adminApi_admin_ok() throws Exception {
    mockMvc.perform(get("/api/admin/wiki/learning-materials").cookie(token("ADMIN")))
        .andExpect(status().isOk());
    mockMvc.perform(delete("/api/admin/wiki/learning-materials/1").cookie(token("ADMIN")))
        .andExpect(status().isNoContent());
  }

  private Cookie token(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "test-" + role.toLowerCase(), role));
  }

  private WikiLearningMaterialResponse sample() {
    return new WikiLearningMaterialResponse(1L, "VOCAB", "어휘", 2, "고2 어휘", "어휘.pdf", 100L,
        "홍길동", LocalDateTime.now(), true, "/api/wiki/learning-materials/1/preview",
        "/api/wiki/learning-materials/1/download");
  }
}
