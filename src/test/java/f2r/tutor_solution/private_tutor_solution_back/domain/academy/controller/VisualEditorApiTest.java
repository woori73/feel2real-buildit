package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.VisualEditorDraftRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BuildIt 시각 편집기 자동화 테스트.
 *
 * <p>검증 항목:
 * <ul>
 *   <li>저장·조회</li>
 *   <li>권한 (본인 학원만)</li>
 *   <li>병렬 409 (동시 수정 충돌)</li>
 *   <li>XSS (HTML·projectData)</li>
 *   <li>P0 템플릿 HTML/CSS 보존</li>
 *   <li>용량 경계값</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class VisualEditorApiTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private VisualEditorService visualEditorService;

  @Autowired
  private VisualEditorDraftRepository draftRepository;

  @MockBean
  private AcademyService academyService;

  private JwtTokenProvider.AuthenticatedPrincipal principal;
  private f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse
      academyResponse;

  @BeforeEach
  void setUp() {
    Long testUserId = 999001L;
    Long testAcademyId = 999001L;
    principal = new JwtTokenProvider.AuthenticatedPrincipal(
        testUserId, "test-tutor", "TUTOR");

    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, List.of()));

    // AcademyService 모킹
    academyResponse = Mockito.mock(
        f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse.class);
    when(academyResponse.academyId()).thenReturn(testAcademyId);
    when(academyService.getMine(any())).thenReturn(Optional.of(academyResponse));

    // 기존 초안 정리
    draftRepository.findByAcademyId(testAcademyId)
        .ifPresent(draftRepository::delete);
    draftRepository.flush();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void saveAndLoadDraft() throws Exception {
    String html = "<section><h1>테스트 학원</h1></section>";
    String css = "h1 { color: red; }";

    // 저장 (신규이므로 revision null 허용)
    MvcResult saveResult = mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "html", html, "css", css, "projectData", "{}"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT_SAVED"))
        .andExpect(jsonPath("$.revision").value(1))
        .andReturn();

    // 조회
    mockMvc.perform(get("/api/tutor/academy/page-settings/visual-editor/draft"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.html").value(html))
        .andExpect(jsonPath("$.revision").value(1));
  }

  @Test
  void revisionRequiredWhenDraftExists() throws Exception {
    // 첫 저장
    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "html", "<p>1</p>", "css", "", "projectData", "{}"))))
        .andExpect(status().isOk());

    // revision 생략한 두 번째 저장 → 400 거부
    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "html", "<p>2</p>", "css", "", "projectData", "{}"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(
            org.hamcrest.Matchers.containsString("revision")));
  }

  @Test
  void concurrentSaves_onlyOneSucceeds() throws Exception {
    // 첫 저장으로 초안 생성
    MvcResult first = mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "html", "<p>initial</p>", "css", "", "projectData", "{}"))))
        .andExpect(status().isOk())
        .andReturn();
    Long revision = objectMapper.readTree(
        first.getResponse().getContentAsString()).get("revision").asLong();

    // 동일 revision으로 병렬 2건 저장 (서로 다른 내용)
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Integer>> tasks = List.of(
          () -> saveWithRevision("<p>content-A</p>", revision),
          () -> saveWithRevision("<p>content-B</p>", revision));
      List<Future<Integer>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);

      int success200 = 0;
      int conflict409 = 0;
      for (Future<Integer> f : futures) {
        int code = f.get(10, TimeUnit.SECONDS);
        if (code == 200) success200++;
        else if (code == 409) conflict409++;
      }
      // 정확히 1건만 200, 나머지 409
      assertThat(success200).isEqualTo(1);
      assertThat(conflict409).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  private int saveWithRevision(String html, Long revision) throws Exception {
    MvcResult result = mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "html", html, "css", "", "projectData", "{}",
                "revision", revision))))
        .andReturn();
    return result.getResponse().getStatus();
  }

  @Test
  void xssScriptBlocked() throws Exception {
    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "html", "<p>hi</p><script>alert(1)</script>",
                "css", "", "projectData", "{}"))))
        .andExpect(status().isOk());

    MvcResult result = mockMvc.perform(
            get("/api/tutor/academy/page-settings/visual-editor/draft"))
        .andExpect(status().isOk())
        .andReturn();
    String savedHtml = objectMapper.readTree(
        result.getResponse().getContentAsString()).get("html").asText();
    assertThat(savedHtml).doesNotContain("<script");
  }

  @Test
  void xssProjectDataScriptBlocked() throws Exception {
    String malicious = "{\"components\":[{\"content\":\"<script>alert(1)</script>\"}]}";
    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "html", "<p>hi</p>", "css", "",
                "projectData", malicious))))
        .andExpect(status().isOk());

    // 저장된 projectData의 content가 정제되었는지 확인
    MvcResult result = mockMvc.perform(
            get("/api/tutor/academy/page-settings/visual-editor/draft"))
        .andExpect(status().isOk())
        .andReturn();
    String savedProject = objectMapper.readTree(
        result.getResponse().getContentAsString()).get("projectData").asText();
    assertThat(savedProject).doesNotContain("<script");
  }

  @Test
  void xssProjectDataOnAttrBlocked() throws Exception {
    String malicious = "{\"components\":[{\"attributes\":{\"onclick\":\"alert(1)\"}}]}";
    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "html", "<p>hi</p>", "css", "",
                "projectData", malicious))))
        .andExpect(status().isOk());

    MvcResult result = mockMvc.perform(
            get("/api/tutor/academy/page-settings/visual-editor/draft"))
        .andExpect(status().isOk())
        .andReturn();
    String savedProject = objectMapper.readTree(
        result.getResponse().getContentAsString()).get("projectData").asText();
    assertThat(savedProject.toLowerCase()).doesNotContain("onclick");
  }

  @Test
  void cssSizeLimit_65536bytes_ok_65537bytes_400() throws Exception {
    // 정확히 65536바이트 (64KB) → 허용
    String css65536 = "a{color:red;}".repeat(65536 / 13 + 1).substring(0, 65536);
    assertThat(css65536.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
        .isEqualTo(65536);

    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "html", "<p>hi</p>", "css", css65536, "projectData", "{}"))))
        .andExpect(status().isOk());

    // 65537바이트 → 400 (500이 아님)
    String css65537 = css65536 + "x";
    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "html", "<p>hi</p>", "css", css65537, "projectData", "{}",
                "revision", 1L))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void cssImportBlocked() throws Exception {
    String css = "@import url('http://evil.com/x.css'); p { color: red; }";
    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "html", "<p>hi</p>", "css", css, "projectData", "{}"))))
        .andExpect(status().isOk());

    MvcResult result = mockMvc.perform(
            get("/api/tutor/academy/page-settings/visual-editor/draft"))
        .andExpect(status().isOk())
        .andReturn();
    String savedCss = objectMapper.readTree(
        result.getResponse().getContentAsString()).get("css").asText();
    assertThat(savedCss).doesNotContain("@import");
  }

  @Test
  void p0TemplateTagsPreserved() throws Exception {
    // P0 시맨틱 태그 보존 확인
    String html = "<section><header><h1>제목</h1></header>"
        + "<main><article><p>본문</p></article></main>"
        + "<footer><nav><a href=\"/about\">소개</a></nav></footer></section><hr>";
    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "html", html, "css", "", "projectData", "{}"))))
        .andExpect(status().isOk());

    MvcResult result = mockMvc.perform(
            get("/api/tutor/academy/page-settings/visual-editor/draft"))
        .andExpect(status().isOk())
        .andReturn();
    String saved = objectMapper.readTree(
        result.getResponse().getContentAsString()).get("html").asText();
    assertThat(saved).contains("<section");
    assertThat(saved).contains("<header");
    assertThat(saved).contains("<footer");
    assertThat(saved).contains("<nav");
    assertThat(saved).contains("<main");
    assertThat(saved).contains("<article");
  }

  @Test
  void koreanContentPreserved() throws Exception {
    String html = "<section><h1>안녕하세요 🎉</h1><p>한글 테스트입니다.</p></section>";
    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "html", html, "css", "", "projectData", "{}"))))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/tutor/academy/page-settings/visual-editor/draft"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.html").value(html));
  }

  @Test
  void publishDisabled() throws Exception {
    mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/publish")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "html", "<p/>", "css", ""))))
        .andExpect(status().isNotImplemented());
  }
}
