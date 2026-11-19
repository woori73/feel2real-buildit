package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * TESTPREP 3단계 — `/testprep` 화면 진입·렌더·접근 통제 MySQL 통합 테스트.
 *
 * <p>SecurityConfig의 신규 규칙을 검증한다:
 * <ul>
 *   <li>{@code /testprep} 첫 페이지는 비회원 공개(200). 하위 화면 익명 요청은 raw 401이 아닌 302 → {@code /testprep?login=required&returnUrl=…}.</li>
 *   <li>익명 JSON API는 401.</li>
 *   <li>로그인 회원(STUDENT)에게는 Thymeleaf 화면이 정상 렌더(200)된다.</li>
 *   <li>feel2real 메인 {@code /}(BuildIt 랜딩)은 그대로 익명 접근 가능 — TESTPREP 화면 통제가 홈페이지에 영향 없음.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("TESTPREP 3단계 — /testprep 화면 진입·렌더·접근 통제")
class TestPrepPagesMySqlTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider tokenProvider;

  private Cookie auth() {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(900303L, "tp_page_student", "STUDENT"));
  }

  @Test
  @DisplayName("익명 /testprep 첫 페이지는 로그인 이동 없이 200으로 공개된다")
  void anonymousTestprepHomeIsPublic() throws Exception {
    MvcResult r = mockMvc.perform(get("/testprep").accept(MediaType.TEXT_HTML)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(r.getResponse().getContentAsString()).contains("tp-nav");
  }

  @Test
  @DisplayName("익명 하위 화면은 /testprep로 보내 로그인 안내를 띄우고 원래 경로를 returnUrl로 싣는다")
  void anonymousSubPagesRedirectToPublicHomeWithReturnUrl() throws Exception {
    for (String path : new String[] {
        "/testprep/practice", "/testprep/vocab", "/testprep/guide", "/testprep/run", "/testprep/scores",
        "/testprep/review", "/testprep/learning", "/testprep/sat", "/testprep/sat/run"}) {
      MvcResult r = mockMvc.perform(get(path).accept(MediaType.TEXT_HTML)).andReturn();
      assertThat(r.getResponse().getStatus()).as("status " + path).isEqualTo(302);
      assertThat(r.getResponse().getRedirectedUrl()).as("redirect " + path)
          .isEqualTo("/testprep?login=required&returnUrl=" + java.net.URLEncoder.encode(path, java.nio.charset.StandardCharsets.UTF_8));
    }
  }

  @Test
  @DisplayName("익명 JSON API(시험·응시·성적·이력)는 raw 401로 차단된다")
  void anonymousApisAreBlocked() throws Exception {
    for (String path : new String[] {
        "/api/testprep/exams", "/api/testprep/attempts", "/api/testprep/attempts/1",
        "/api/testprep/attempts/1/review", "/api/testprep/learning"}) {
      MvcResult r = mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON)).andReturn();
      assertThat(r.getResponse().getStatus()).as("status " + path).isEqualTo(401);
    }
  }

  @Test
  @DisplayName("로그인 회원은 /testprep 및 서브 화면이 200으로 렌더된다")
  void authenticatedPagesRender() throws Exception {
    for (String path : new String[] {
        "/testprep", "/testprep/practice", "/testprep/vocab", "/testprep/guide",
        "/testprep/run", "/testprep/scores", "/testprep/review", "/testprep/learning", "/testprep/sat"}) {
      MvcResult r = mockMvc.perform(get(path).cookie(auth()).accept(MediaType.TEXT_HTML)).andReturn();
      assertThat(r.getResponse().getStatus()).as("render " + path).isEqualTo(200);
      String html = r.getResponse().getContentAsString();
      assertThat(html).as("nav present on " + path).contains("tp-nav");
      // 한영 병기 셸이 함께 렌더되는지 확인.
      assertThat(html).as("bilingual ko span on " + path).contains("class=\"ko\"");
    }
  }

  @Test
  @DisplayName("로그인 회원의 SAT 시험 화면은 TESTPREP 메뉴 없이 시험 도구 셸만 렌더된다")
  void satRunRendersExamShell() throws Exception {
    MvcResult r = mockMvc.perform(get("/testprep/sat/run").cookie(auth()).accept(MediaType.TEXT_HTML)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    String html = r.getResponse().getContentAsString();
    assertThat(html).doesNotContain("tp-nav");
    assertThat(html).contains("id=\"sat-main\"", "id=\"calc-panel\"", "id=\"ref-panel\"", "id=\"line-reader\"",
        "/js/sat_run.js", "/js/sat_calc.js", "/js/sat_rich.js", "id=\"btn-home\"", "id=\"help-panel\"");
    // 동작하지 않던 Highlights & Notes 안내 팝업과 응시자 이름 칸은 지웠다(2026-11-15).
    assertThat(html).doesNotContain("id=\"hl-tip\"", "id=\"sat-name\"");
  }

  @Test
  @DisplayName("TESTPREP 화면 통제는 BuildIt 메인(/) 익명 접근을 막지 않는다")
  void homeRemainsPublic() throws Exception {
    MvcResult r = mockMvc.perform(get("/").accept(MediaType.TEXT_HTML)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("/testprep landing은 QA dev-login 배너 없이 실제 SSO 로그인만 안내한다")
  void landingHasNoDevLoginBanner() throws Exception {
    MvcResult r = mockMvc.perform(get("/testprep").cookie(auth()).accept(MediaType.TEXT_HTML)).andReturn();
    String html = r.getResponse().getContentAsString();
    assertThat(html).doesNotContain("qa-banner");
    assertThat(html).doesNotContain("Dev login");
    assertThat(html).doesNotContain("dev-login");
  }

  @Test
  @DisplayName("익명 /naesintest 첫 화면은 공개되고 학교 3곳(압구정중2·신구중2·압구정중1)을 고르는 표가 나온다")
  void anonymousNaesinHomeIsPublic() throws Exception {
    MvcResult r = mockMvc.perform(get("/naesintest").accept(MediaType.TEXT_HTML)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    String html = r.getResponse().getContentAsString();
    assertThat(html).contains("href=\"/naesintest/school/apgujeong-m2\"", "href=\"/naesintest/school/singu-m2\"",
        "href=\"/naesintest/school/apgujeong-m1\"", "신구중학교", "16회", "준비 중");
    assertThat(html).doesNotContain("data-exam=", "tp-nav");
  }

  @Test
  @DisplayName("익명 학교별 페이지는 공개되고, 압구정중2는 16회차·신구중2는 11회차가 범위별 표로, 문제가 없는 학교는 준비 중으로, 없는 학교는 404")
  void anonymousSchoolPages() throws Exception {
    MvcResult a = mockMvc.perform(get("/naesintest/school/apgujeong-m2").accept(MediaType.TEXT_HTML)).andReturn();
    assertThat(a.getResponse().getStatus()).isEqualTo(200);
    String html = a.getResponse().getContentAsString();
    assertThat(html.split("data-exam=\"TP-NAESIN-APM2-2602-R", -1)).hasSize(17);
    assertThat(html).contains("Lesson 7", "Lesson 8", "Lesson 7·8 종합", "Final", "/js/naesin_home.js", "href=\"/naesintest\"");
    // 직보 AI 인강 4편(2026-11-16): 압구정중2에만 있다.
    assertThat(html).contains("id=\"shorts\"", "/video/naesin-apm2-l7-it-subject-r1.mp4", "/video/%EC%84%9C%EC%88%98%EB%8B%88_7%EA%B3%BC_%EC%82%AC%EC%97%AD%EB%8F%99%EC%82%AC_%EC%B9%A0%ED%8C%90_r4.mp4",
        "/video/naesin-apm2-l8-thing-adj-r1.mp4", "/video/naesin-apm2-l8-object-relative-r1.mp4");
    for (String file : new String[] {"l7-it-subject-r1", "l8-thing-adj-r1", "l8-object-relative-r1"}) {
      assertThat(getClass().getResource("/static/video/naesin-apm2-" + file + ".mp4")).as(file).isNotNull();
      assertThat(getClass().getResource("/static/video/naesin-apm2-" + file + ".jpg")).as(file).isNotNull();
    }
    // 7과 가주어 It·8과 -thing + 형용사·8과 목적격 관계대명사는 2026-11-17 칠판판(r1)으로 바꿨다.
    // 7과 사역동사는 2026-11-16 칠판판(r4)으로 바꿨다. 영상은 원본 이름 그대로 올라와 있다.
    assertThat(getClass().getResource("/static/video/서수니_7과_사역동사_칠판_r4.mp4")).isNotNull();
    assertThat(getClass().getResource("/static/video/naesin-apm2-l7-causative-r4.jpg")).isNotNull();
    MvcResult s = mockMvc.perform(get("/naesintest/school/singu-m2").accept(MediaType.TEXT_HTML)).andReturn();
    assertThat(s.getResponse().getStatus()).isEqualTo(200);
    String singu = s.getResponse().getContentAsString();
    assertThat(singu.split("data-exam=\"TP-NAESIN-SGM2-2602-R", -1)).hasSize(12); // 신구중2 Lesson 7 1~5회 · Lesson 8 6~10회(V1120) · 종합 Final 11회(V1121)
    assertThat(singu).contains("신구중학교", "Lesson 7", "Lesson 8", "Lesson 7·8 종합 Final").doesNotContain("준비하고 있습니다", "id=\"shorts\"");
    MvcResult m1 = mockMvc.perform(get("/naesintest/school/apgujeong-m1").accept(MediaType.TEXT_HTML)).andReturn();
    assertThat(m1.getResponse().getContentAsString()).contains("준비하고 있습니다").doesNotContain("data-exam=");
    MvcResult none = mockMvc.perform(get("/naesintest/school/nowhere").accept(MediaType.TEXT_HTML)).andReturn();
    assertThat(none.getResponse().getStatus()).isEqualTo(404);
  }

  @Test
  @DisplayName("익명 /naesintest 하위 화면은 /naesintest로 보내 로그인 안내를 띄우고, 문항 그림은 막힌다")
  void anonymousNaesinSubPagesRedirect() throws Exception {
    for (String path : new String[] {"/naesintest/run", "/naesintest/review"}) {
      MvcResult r = mockMvc.perform(get(path).accept(MediaType.TEXT_HTML)).andReturn();
      assertThat(r.getResponse().getStatus()).as("status " + path).isEqualTo(302);
      assertThat(r.getResponse().getRedirectedUrl()).as("redirect " + path)
          .isEqualTo("/naesintest?login=required&returnUrl=" + java.net.URLEncoder.encode(path, java.nio.charset.StandardCharsets.UTF_8));
    }
    MvcResult img = mockMvc.perform(get("/naesin/apgujeong-m2-2026-2/r01/q01.webp").accept(MediaType.IMAGE_PNG)).andReturn();
    assertThat(img.getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("로그인 회원에게 /naesintest 시험·채점 화면과 문항 그림이 열린다")
  void memberSeesNaesinPages() throws Exception {
    MvcResult run = mockMvc.perform(get("/naesintest/run").cookie(auth()).accept(MediaType.TEXT_HTML)).andReturn();
    assertThat(run.getResponse().getStatus()).isEqualTo(200);
    assertThat(run.getResponse().getContentAsString()).contains("nt-omr", "/js/naesin_run.js").doesNotContain("해설 보기");
    MvcResult review = mockMvc.perform(get("/naesintest/review").cookie(auth()).accept(MediaType.TEXT_HTML)).andReturn();
    assertThat(review.getResponse().getStatus()).isEqualTo(200);
    MvcResult img = mockMvc.perform(get("/naesin/apgujeong-m2-2026-2/r01/q01.webp").cookie(auth())).andReturn();
    assertThat(img.getResponse().getStatus()).isEqualTo(200);
  }
}
