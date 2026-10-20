package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.controller;

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
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.dto.WikiExamOriginalResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.service.WikiExamOriginalService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.service.WikiExamOriginalService.DownloadFile;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("WikiExamOriginalController — 회원 조회·다운로드 / 관리 ADMIN")
@WebMvcTest(controllers = WikiExamOriginalController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class WikiExamOriginalControllerWebMvcTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean WikiExamOriginalService service;
  @MockitoBean AcademyRepository academyRepository;
  @MockitoBean WikiDownloadRecordingService downloadRecordingService;

  @BeforeEach
  void stubs() {
    when(service.searchPublic(any())).thenReturn(List.of(sample()));
    when(service.getPublicDownload(1L)).thenReturn(new DownloadFile("%PDF".getBytes(), "9월-원본.pdf"));
    when(service.create(anyString(), anyInt(), any(), anyString(), anyBoolean(), any(), any(), any()))
        .thenReturn(sample());
  }

  @Test
  @DisplayName("given 익명 when 목록 직접 조회 then 401")
  void list_anonymous_unauthorized() throws Exception {
    mockMvc.perform(get("/api/wiki/originals").param("grade", "3"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given 익명 when 다운로드 URL 직접 호출 then 401 + 파일 조회/로그 기록 없음")
  void download_anonymous_unauthorizedWithoutLog() throws Exception {
    mockMvc.perform(get("/api/wiki/originals/1/download")).andExpect(status().isUnauthorized());
    verify(service, never()).getPublicDownload(anyLong());
    verifyNoInteractions(downloadRecordingService);
  }

  @Test
  @DisplayName("given 회원 when 다운로드 then 200 + attachment 헤더 + 다운로드 기록")
  void download_member_okAndRecorded() throws Exception {
    mockMvc.perform(get("/api/wiki/originals/1/download").cookie(token("STUDENT")))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
            org.hamcrest.Matchers.containsString("attachment")));
    verify(downloadRecordingService).record(
        eq(WikiMaterialCategory.ORIGINAL), eq(1L), anyString(), eq(1L), eq("STUDENT"));
  }

  @Test
  @DisplayName("given 익명 when 관리 등록 then 401")
  void adminCreate_anonymous_unauthorized() throws Exception {
    mockMvc.perform(multipart("/api/admin/wiki/originals")
            .file("file", "%PDF".getBytes())
            .param("title", "t").param("grade", "3")
            .param("examLabel", "2026 9월 모의고사").param("docType", "QUESTION"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given STUDENT when 관리 등록 then 403")
  void adminCreate_student_forbidden() throws Exception {
    mockMvc.perform(multipart("/api/admin/wiki/originals")
            .file("file", "%PDF".getBytes())
            .param("title", "t").param("grade", "3")
            .param("examLabel", "2026 9월 모의고사").param("docType", "QUESTION")
            .cookie(token("STUDENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("given ADMIN when 관리 등록 then 200")
  void adminCreate_admin_ok() throws Exception {
    mockMvc.perform(multipart("/api/admin/wiki/originals")
            .file("file", "%PDF".getBytes())
            .param("title", "t").param("grade", "3")
            .param("examLabel", "2026 9월 모의고사").param("docType", "QUESTION")
            .cookie(token("ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("given ADMIN when 연도·월로 해설 등록 then 200 + 서비스에 회차가 전달된다")
  void adminCreate_withYearMonth_ok() throws Exception {
    mockMvc.perform(multipart("/api/admin/wiki/originals")
            .file("file", "<html></html>".getBytes())
            .param("title", "고1 10월 문제풀이 해설").param("grade", "1")
            .param("docType", "EXPLANATION").param("examYear", "2026").param("examMonth", "10")
            .param("visible", "false")
            .cookie(token("ADMIN")))
        .andExpect(status().isOk());
    verify(service).create(eq("고1 10월 문제풀이 해설"), eq(1), eq(null), eq("EXPLANATION"),
        eq(false), any(), eq(2026), eq(10));
  }

  @Test
  @DisplayName("given 회원 when HTML 열람 then 200 + inline + CSP sandbox")
  void view_member_html_okSandboxed() throws Exception {
    when(service.getPublicView(1L)).thenReturn(
        new DownloadFile("<html>해설</html>".getBytes(), "해설.html"));
    mockMvc.perform(get("/api/wiki/originals/1/view").cookie(token("STUDENT")))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
            org.hamcrest.Matchers.containsString("inline")))
        .andExpect(header().string("Content-Security-Policy", "sandbox"));
  }

  @Test
  @DisplayName("given 익명 when 열람 then 401")
  void view_anonymous_unauthorized() throws Exception {
    mockMvc.perform(get("/api/wiki/originals/1/view")).andExpect(status().isUnauthorized());
    verify(service, never()).getPublicView(anyLong());
  }

  @Test
  @DisplayName("given STUDENT when 관리 목록/삭제/공개전환 then 403")
  void adminReadWrite_student_forbidden() throws Exception {
    mockMvc.perform(get("/api/admin/wiki/originals").cookie(token("STUDENT")))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/admin/wiki/originals/1").cookie(token("STUDENT")))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/admin/wiki/originals/1/hard").cookie(token("STUDENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("given 익명 when 관리 목록/삭제 then 401")
  void adminReadWrite_anonymous_unauthorized() throws Exception {
    mockMvc.perform(get("/api/admin/wiki/originals")).andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/api/admin/wiki/originals/1")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given ADMIN when 삭제 then 204")
  void adminDelete_admin_noContent() throws Exception {
    mockMvc.perform(delete("/api/admin/wiki/originals/1").cookie(token("ADMIN")))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("given 없는 자료 when 다운로드 then 404")
  void publicDownload_missing_notFound() throws Exception {
    when(service.getPublicDownload(999L)).thenThrow(
        new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "no"));
    mockMvc.perform(get("/api/wiki/originals/999/download").cookie(token("STUDENT"))).andExpect(status().isNotFound());
  }

  private Cookie token(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "test-" + role.toLowerCase(), role));
  }

  private WikiExamOriginalResponse sample() {
    return new WikiExamOriginalResponse(1L, "고3 9월 문제지", 3, 2026, 9, "2026 9월 모의고사",
        "QUESTION", "문제지", "original.pdf", "pdf", 100L, true,
        LocalDateTime.now(), LocalDateTime.now(), "/api/wiki/originals/1/download",
        "/api/wiki/originals/1/view");
  }
}
