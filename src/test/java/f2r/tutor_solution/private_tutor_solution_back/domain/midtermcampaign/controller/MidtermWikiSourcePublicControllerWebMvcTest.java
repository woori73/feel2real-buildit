package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceListResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service.MidtermWikiSourceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * TUTOR Wiki 연결 API 의 공개 계약:
 * <ul>
 *   <li>{@code /wiki-sources} · {@code /preview} 는 비로그인으로 접근 가능(경로 permitAll, 판정은 서비스)
 *   <li>{@code /preview} HTML 응답 헤더 = learning-material preview 와 동일(script 금지 CSP · inline · nosniff)
 *   <li>목록 payload 에 storageKey / 원본 URL / download endpoint 가 없다
 * </ul>
 */
@WebMvcTest(controllers = MidtermWikiSourcePublicController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, WebMvcConfig.class, RoleModelInterceptor.class,
    JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class MidtermWikiSourcePublicControllerWebMvcTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean MidtermWikiSourceService service;

  @Test
  void list_isReachableWithoutLogin_andPayloadHasNoKeyOrUrlLeak() throws Exception {
    WikiSourceView view = new WikiSourceView(
        "school-print-11", "SCHOOL_PRINT", "학교 프린트", "5과 학습지", "신구중학교", "2",
        2026, "2학기", "중간고사", "2026-09-07",
        "/api/academies/eduholic/midterm-campaign/wiki-sources/school-print-11/preview");
    when(service.listSources("eduholic")).thenReturn(new WikiSourceListResult(List.of(view), List.of()));

    String body = mockMvc.perform(get("/api/academies/eduholic/midterm-campaign/wiki-sources"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    assertThat(body).contains("school-print-11").contains("/preview");
    assertThat(body).doesNotContain("storage", "storageKey", "127.0.0.1", "8000", "/html", "/original", "/download");
  }

  @Test
  void preview_servesInlineHtml_withScriptBlockingCspIdenticalToLearningMaterial() throws Exception {
    byte[] html = "<html><body><h1>신구중 5과</h1></body></html>".getBytes();
    when(service.previewHtml(anyString(), anyString())).thenReturn(html);

    mockMvc.perform(get("/api/academies/eduholic/midterm-campaign/wiki-sources/school-print-11/preview"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/html"))
        .andExpect(header().string("Content-Disposition", "inline; filename=\"preview.html\""))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Content-Security-Policy",
            MidtermWikiSourcePublicController.HTML_PREVIEW_CSP))
        .andExpect(header().string("Cache-Control", "private, max-age=60"))
        .andExpect(content().bytes(html));
  }

  @Test
  void previewCsp_matchesLearningMaterialControllerSource() throws Exception {
    String learning = Files.readString(Path.of(
        "src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/wikilearning/controller/WikiLearningMaterialController.java"));
    // learning preview 의 HTML CSP 조각이 그대로 들어 있어야 한다(둘이 어긋나면 실패).
    assertThat(learning).contains("script-src 'none'").contains("connect-src 'none'");
    assertThat(MidtermWikiSourcePublicController.HTML_PREVIEW_CSP)
        .isEqualTo("default-src 'none'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; "
            + "frame-ancestors 'self'; base-uri 'none'; form-action 'none'; object-src 'none'; "
            + "script-src 'none'; connect-src 'none'");
    assertThat(learning).contains(MidtermWikiSourcePublicController.HTML_PREVIEW_CSP);
  }
}
