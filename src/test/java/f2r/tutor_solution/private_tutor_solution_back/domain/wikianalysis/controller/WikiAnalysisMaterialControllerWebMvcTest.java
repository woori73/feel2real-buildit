package f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
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
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.dto.WikiAnalysisMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.service.WikiAnalysisMaterialService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.service.WikiAnalysisMaterialService.DownloadFile;
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

@DisplayName("WikiAnalysisMaterialController — 회원 조회·다운로드 / 관리 ADMIN")
@WebMvcTest(controllers = WikiAnalysisMaterialController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class WikiAnalysisMaterialControllerWebMvcTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean WikiAnalysisMaterialService service;
  @MockitoBean AcademyRepository academyRepository;
  @MockitoBean WikiDownloadRecordingService downloadRecordingService;

  @BeforeEach
  void stubs() {
    when(service.searchPublic(any())).thenReturn(List.of(sample()));
    when(service.getPublicDownload(1L))
        .thenReturn(new DownloadFile("HWP".getBytes(), "분석.hwp", "application/x-hwp"));
    when(service.create(anyString(), any(), any(), anyInt(), anyInt(), anyString(), anyString(), any(),
        anyBoolean(), any())).thenReturn(sample());
  }

  @Test
  @DisplayName("given 익명 when 목록 직접 조회 then 401")
  void list_anonymous_unauthorized() throws Exception {
    mockMvc.perform(get("/api/wiki/analysis-materials").param("grade", "3"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given 익명 when 다운로드 URL 직접 호출 then 401 + 파일 조회/로그 기록 없음")
  void download_anonymous_unauthorizedWithoutLog() throws Exception {
    mockMvc.perform(get("/api/wiki/analysis-materials/1/download")).andExpect(status().isUnauthorized());
    verify(service, never()).getPublicDownload(anyLong());
    verifyNoInteractions(downloadRecordingService);
  }

  @Test
  @DisplayName("given 회원 when 다운로드 then 200 + attachment 헤더 + 다운로드 기록")
  void download_member_okAndRecorded() throws Exception {
    mockMvc.perform(get("/api/wiki/analysis-materials/1/download").cookie(token("STUDENT")))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
            org.hamcrest.Matchers.containsString("attachment")));
    verify(downloadRecordingService).record(
        eq(WikiMaterialCategory.ANALYSIS), eq(1L), anyString(), eq(1L), eq("STUDENT"));
  }

  @Test
  @DisplayName("given 익명/STUDENT when 관리 등록 then 401/403")
  void adminCreate_deniedForNonAdmin() throws Exception {
    mockMvc.perform(multipart("/api/admin/wiki/analysis-materials").file("file", "%PDF".getBytes())
            .param("title", "t").param("grade", "3").param("year", "2026")
            .param("semester", "1학기").param("examType", "기말"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(multipart("/api/admin/wiki/analysis-materials").file("file", "%PDF".getBytes())
            .param("title", "t").param("grade", "3").param("year", "2026")
            .param("semester", "1학기").param("examType", "기말").cookie(token("STUDENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("given ADMIN when 관리 등록/삭제 then 200/204")
  void adminCrud_admin_ok() throws Exception {
    mockMvc.perform(multipart("/api/admin/wiki/analysis-materials").file("file", "%PDF".getBytes())
            .param("title", "t").param("grade", "3").param("year", "2026")
            .param("semester", "1학기").param("examType", "기말").cookie(token("ADMIN")))
        .andExpect(status().isOk());
    mockMvc.perform(delete("/api/admin/wiki/analysis-materials/1").cookie(token("ADMIN")))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("given 없는 자료 when 다운로드 then 404")
  void publicDownload_missing_notFound() throws Exception {
    when(service.getPublicDownload(999L)).thenThrow(
        new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "no"));
    mockMvc.perform(get("/api/wiki/analysis-materials/999/download").cookie(token("STUDENT"))).andExpect(status().isNotFound());
  }

  private Cookie token(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "test-" + role.toLowerCase(), role));
  }

  private WikiAnalysisMaterialResponse sample() {
    return new WikiAnalysisMaterialResponse(1L, "신사중 3학년 기말 분석", null, "신사중", 3, 2026, "1학기", "기말",
        "영어", "application/x-hwp", "분석.hwp", 100L, true,
        LocalDateTime.now(), LocalDateTime.now(), "/api/wiki/analysis-materials/1/download");
  }
}
