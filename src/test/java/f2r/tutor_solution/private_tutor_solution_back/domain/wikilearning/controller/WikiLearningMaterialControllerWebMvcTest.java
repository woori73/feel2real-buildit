package f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

  @BeforeEach
  void stubs() {
    when(service.searchPublic(any())).thenReturn(List.of(sample()));
    when(service.listAdmin()).thenReturn(List.of(sample()));
    when(service.getDownload(1L)).thenReturn(new DownloadFile("PDF".getBytes(), "어휘.pdf", "application/pdf"));
    when(service.upload(anyLong(), anyString(), any(), any(), any())).thenReturn(sample());
  }

  @Test
  @DisplayName("given 익명 when 목록 then 200")
  void list_anonymous_ok() throws Exception {
    mockMvc.perform(get("/api/wiki/learning-materials").param("grade", "2")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("given 익명 when 다운로드/업로드 then 401 (URL 직접 입력해도 파일 안 나감)")
  void downloadAndUpload_anonymous_unauthorized() throws Exception {
    mockMvc.perform(get("/api/wiki/learning-materials/1/download")).andExpect(status().isUnauthorized());
    mockMvc.perform(multipart("/api/wiki/learning-materials").file("file", "%PDF".getBytes())
            .param("dataType", "VOCAB").param("grade", "2"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given 회원(STUDENT) when 업로드/다운로드 then 200 + attachment 헤더")
  void memberUploadAndDownload_ok() throws Exception {
    mockMvc.perform(multipart("/api/wiki/learning-materials").file("file", "%PDF".getBytes())
            .param("dataType", "VOCAB").param("grade", "2").param("title", "고2 어휘")
            .cookie(token("STUDENT")))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/wiki/learning-materials/1/download").cookie(token("STUDENT")))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
            org.hamcrest.Matchers.containsString("attachment")));
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
        "홍길동", LocalDateTime.now(), "/api/wiki/learning-materials/1/download");
  }
}
