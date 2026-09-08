package f2r.tutor_solution.private_tutor_solution_back.domain.community.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
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
import f2r.tutor_solution.private_tutor_solution_back.domain.community.service.CommunityImageService;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.service.CommunityPostAttachmentService;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.service.CommunityPostAttachmentService.AttachmentFileBundle;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.service.CommunityService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("CommunityController 첨부 — Wiki 공유 파일 우회 방지를 위해 전체 회원 전용")
@WebMvcTest(controllers = CommunityController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class CommunityAttachmentControllerWebMvcTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean CommunityService communityService;
  @MockitoBean CommunityImageService communityImageService;
  @MockitoBean CommunityPostAttachmentService attachmentService;
  @MockitoBean AcademyRepository academyRepository;

  @BeforeEach
  void stubs() {
    when(attachmentService.getAttachmentFile(1L))
        .thenReturn(new AttachmentFileBundle("PDF".getBytes(), "application/pdf", "문제.pdf"));
    when(attachmentService.getAttachmentFile(2L))
        .thenReturn(new AttachmentFileBundle(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/jpeg", "5과 영영단어.jpg"));
  }

  @Test
  @DisplayName("given 회원 when PDF 첨부 다운로드 then 200 + Content-Disposition: attachment + 파일명 보존")
  void download_pdf_forcesAttachment() throws Exception {
    mockMvc.perform(get("/api/open-community/attachments/1/file").cookie(token("STUDENT")))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(header().string("Content-Disposition",
            org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.startsWith("attachment"),
                org.hamcrest.Matchers.containsString("filename*=UTF-8''"),
                org.hamcrest.Matchers.containsString(".pdf"))));
  }

  @Test
  @DisplayName("given 회원 when JPG 첨부 다운로드 then image/jpeg + attachment (브라우저 뷰어 X) + 한글 파일명")
  void download_jpg_forcesAttachmentNotInline() throws Exception {
    mockMvc.perform(get("/api/open-community/attachments/2/file").cookie(token("TUTOR")))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/jpeg"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Content-Disposition",
            org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.startsWith("attachment"),
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("inline")))));
  }

  @Test
  @DisplayName("given 익명 when 첨부 URL 직접 호출 then 401")
  void download_anonymous_unauthorized() throws Exception {
    mockMvc.perform(get("/api/open-community/attachments/1/file"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given 없는 첨부 when 다운로드 then 404")
  void download_missing_notFound() throws Exception {
    when(attachmentService.getAttachmentFile(999L)).thenThrow(
        new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "no"));
    mockMvc.perform(get("/api/open-community/attachments/999/file").cookie(token("STUDENT")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given 익명 when 첨부 등록 then 401")
  void add_anonymous_unauthorized() throws Exception {
    mockMvc.perform(multipart("/api/open-community/posts/1/attachments").file("files", "%PDF".getBytes()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given 비작성자(403 던짐) when 첨부 등록 then 403")
  void add_nonAuthor_forbidden() throws Exception {
    doThrow(new AccessDeniedException("nope"))
        .when(attachmentService).addAttachments(any(), anyLong(), any());
    mockMvc.perform(multipart("/api/open-community/posts/1/attachments").file("files", "%PDF".getBytes())
            .cookie(token("STUDENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("given 로그인 when 첨부 등록 then 200")
  void add_authenticated_ok() throws Exception {
    mockMvc.perform(multipart("/api/open-community/posts/1/attachments").file("files", "%PDF".getBytes())
            .cookie(token("STUDENT")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("given 로그인 when 글+JPG 첨부 원자 생성(multipart) then 200 · createPostWithAttachments 경유")
  void createWithAttachments_authenticated_ok() throws Exception {
    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    mockMvc.perform(multipart("/api/open-community/posts")
            .file("files", jpeg)
            .param("boardType", "VARIANT")
            .param("title", "변형문제 공유")
            .param("contentJson", "{}")
            .param("contentHtml", "<p>본문</p>")
            .cookie(token("STUDENT")))
        .andExpect(status().isOk());
    org.mockito.Mockito.verify(communityService)
        .createPostWithAttachments(any(), any(), any());
  }

  @Test
  @DisplayName("given 익명 when 글+첨부 원자 생성(multipart) then 401")
  void createWithAttachments_anonymous_unauthorized() throws Exception {
    mockMvc.perform(multipart("/api/open-community/posts")
            .file("files", "%PDF".getBytes())
            .param("boardType", "VARIANT")
            .param("title", "t")
            .param("contentHtml", "<p>x</p>"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given 로그인 when 첨부 삭제 then 204")
  void delete_authenticated_noContent() throws Exception {
    mockMvc.perform(delete("/api/open-community/attachments/1").cookie(token("STUDENT")))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("given 익명 when 첨부 삭제 then 401")
  void delete_anonymous_unauthorized() throws Exception {
    mockMvc.perform(delete("/api/open-community/attachments/1")).andExpect(status().isUnauthorized());
  }

  private Cookie token(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "test-" + role.toLowerCase(), role));
  }
}
