package f2r.tutor_solution.private_tutor_solution_back.domain.wiki.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
 * 실제 authenticated request(JWT 쿠키 → JwtAuthenticationFilter → principal)로 /wiki 를 렌더해
 * 관리자 업로드 UI 노출이 principal 에 따라 달라지는지 검증한다(template 문자열 존재 여부가 아님).
 */
@DisplayName("WikiPageController — /wiki 렌더: ADMIN principal 일 때만 업로드 UI, 항상 BuildIt 홈 링크")
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
  @DisplayName("익명: 업로드 버튼/모달/스크립트 없음, BuildIt 홈 링크(href=\"/\") 있음")
  void anonymous_noAdminUi_hasHomeLink() throws Exception {
    mockMvc.perform(get("/wiki"))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("id=\"btnUploadOriginal\""))))
        .andExpect(content().string(not(containsString("id=\"wikiUploadModal\""))))
        .andExpect(content().string(not(containsString("wiki_admin_upload.js"))))
        .andExpect(content().string(containsString(">BuildIt 홈</a>")))
        .andExpect(content().string(containsString("class=\"wiki-home-link\" href=\"/\"")));
  }

  @Test
  @DisplayName("STUDENT: 관리자 업로드 UI 없음")
  void student_noAdminUi() throws Exception {
    mockMvc.perform(get("/wiki").cookie(token("STUDENT")))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("id=\"btnUploadOriginal\""))))
        .andExpect(content().string(not(containsString("wiki_admin_upload.js"))));
  }

  @Test
  @DisplayName("ADMIN: 원본/분석 업로드 버튼 + 모달 + wiki_admin_upload.js + BuildIt 홈 모두 렌더")
  void admin_rendersAdminUi() throws Exception {
    mockMvc.perform(get("/wiki").cookie(token("ADMIN")))
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
  @DisplayName("응답에 Cache-Control: no-store + Vary: Cookie (앞단 캐시가 익명 HTML을 공유 못하게)")
  void response_isNotSharedCacheable() throws Exception {
    mockMvc.perform(get("/wiki").cookie(token("ADMIN")))
        .andExpect(header().string("Cache-Control", containsString("no-store")))
        .andExpect(header().string("Vary", containsString("Cookie")));
  }

  @Test
  @DisplayName("/wiki/originals·/wiki/analysis: ADMIN 이면 업로드 진입점, 익명이면 없음")
  void listPages_adminUploadEntry() throws Exception {
    mockMvc.perform(get("/wiki/originals").cookie(token("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("＋ 원본 업로드")));
    mockMvc.perform(get("/wiki/analysis").cookie(token("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("＋ 분석 자료 업로드")));
    mockMvc.perform(get("/wiki/originals"))
        .andExpect(content().string(not(containsString("＋ 원본 업로드"))));
  }
}
