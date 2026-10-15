package f2r.tutor_solution.private_tutor_solution_back.domain.wiki.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * /wiki 랜딩은 공개하고 실제 자료 화면은 로그인 회원에게만 렌더링한다.
 * 관리자 업로드 UI와 API 권한 계약은 로그인 후 기존 동작을 유지한다.
 */
@DisplayName("WikiPageController — 공개 랜딩 / 회원 전용 자료 화면")
@WebMvcTest(controllers = WikiPageController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class WikiPageControllerRenderTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean AcademyRepository academyRepository;

  private Cookie token(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "test-" + role.toLowerCase(), role));
  }

  @Test
  @DisplayName("익명 GET /wiki → 두 업로드 버튼 + 모달 + 스크립트가 HTML 에 존재")
  void anonymous_hub_hasBothUploadButtons() throws Exception {
    mockMvc.perform(get("/wiki"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("id=\"btnUploadOriginal\"")))
        .andExpect(content().string(containsString("id=\"btnUploadAnalysis\"")))
        .andExpect(content().string(containsString("id=\"wikiUploadModal\"")))
        .andExpect(content().string(containsString("wiki_admin_upload.js")))
        .andExpect(content().string(containsString("＋ 원본 업로드")))
        .andExpect(content().string(containsString("＋ 분석 자료 업로드")))
        .andExpect(content().string(containsString(">BuildIt 홈</a>")));
  }

  @Test
  @DisplayName("STUDENT / ADMIN GET /wiki → 익명과 동일하게 버튼 존재(렌더가 principal 에 의존하지 않음)")
  void authenticated_hub_hasSameButtons() throws Exception {
    for (String role : new String[] {"STUDENT", "ADMIN"}) {
      mockMvc.perform(get("/wiki").cookie(token(role)))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("id=\"btnUploadOriginal\"")))
          .andExpect(content().string(containsString("id=\"btnUploadAnalysis\"")));
    }
  }

  @Test
  @DisplayName("익명 GET /wiki/originals → 본문 없이 로그인 안내 랜딩으로 이동")
  void anonymous_originalsPage_redirectsToLogin() throws Exception {
    mockMvc.perform(get("/wiki/originals").header("Accept", "text/html"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location",
            "/?login=required&returnUrl=%2Fwiki%2Foriginals"));
  }

  @Test
  @DisplayName("익명 GET /wiki/analysis → 본문 없이 로그인 안내 랜딩으로 이동")
  void anonymous_analysisPage_redirectsToLogin() throws Exception {
    mockMvc.perform(get("/wiki/analysis").header("Accept", "text/html"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location",
            "/?login=required&returnUrl=%2Fwiki%2Fanalysis"));
  }

  @Test
  @DisplayName("회원 GET /wiki 자료 화면 → 기존 렌더링 유지")
  void member_materialPages_renderNormally() throws Exception {
    mockMvc.perform(get("/wiki/originals").cookie(token("STUDENT")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("id=\"btnUploadOriginal\"")));
    mockMvc.perform(get("/wiki/analysis").cookie(token("TUTOR")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("id=\"btnUploadAnalysis\"")));
  }

  @Test
  @DisplayName("HTML 에 서버 role 조건부 렌더 흔적이 없다")
  void noServerSideRoleGating() throws Exception {
    String body = mockMvc.perform(get("/wiki")).andReturn().getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(body)
        .doesNotContain("${wikiAdmin}", "${role");
  }

  @Test
  @DisplayName("Cache-Control: no-store (앞단 캐시가 HTML 공유 안 하게)")
  void response_noStore() throws Exception {
    mockMvc.perform(get("/wiki"))
        .andExpect(header().string("Cache-Control", containsString("no-store")));
  }
}
