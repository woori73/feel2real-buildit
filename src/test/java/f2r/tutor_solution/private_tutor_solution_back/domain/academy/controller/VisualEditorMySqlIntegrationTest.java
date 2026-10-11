package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyRegionRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorService;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * BuildIt 시각 편집기 MySQL 통합 테스트 (격리 DB).
 *
 * <p>실제 HTTP(MockMvc, 전체 필터 체인 + 실제 JWT 쿠키 인증)와
 * 격리 MySQL(f2r_test, V1057 원본 마이그레이션 적용)로 검증한다:
 * 저장·조회·revision 규칙·병렬 409·XSS·CSS 경계값·P0 태그 보존.
 *
 * <p>운영 DB와 완전히 분리되며, 운영 접속정보를 사용하지 않는다.
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
@DisplayName("시각 편집기 MySQL 통합 테스트 (격리 DB, 실제 HTTP)")
class VisualEditorMySqlIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private JwtTokenProvider tokenProvider;

  @Autowired
  private AcademyService academyService;

  @Autowired
  private VisualEditorService visualEditorService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private SubjectRepository subjectRepository;

  private User tutor;
  private JwtTokenProvider.AuthenticatedPrincipal principal;
  private Cookie authCookie;
  private Subject subject;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    tutor = userRepository.save(new User(
        "ve_test_" + suffix, "pw", "테스트강사", "별명",
        null, null, Role.TUTOR));
    principal = new JwtTokenProvider.AuthenticatedPrincipal(
        tutor.getUserId(), tutor.getLoginId(), "TUTOR");
    authCookie = new Cookie("ACCESS_TOKEN",
        tokenProvider.issue(tutor.getUserId(), tutor.getLoginId(), "TUTOR"));
    subject = subjectRepository.findByNameKo("영어")
        .orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    // 실제 학원 생성 (시각 편집기는 본인 학원 기준으로 동작)
    academyService.createDraft(principal,
        new AcademyUpsertRequest(
            "편집기테스트학원", null, "소개문",
            Set.of(subject.getSubjectId()),
            Set.of(TargetGroup.MIDDLE),
            List.of(new AcademyRegionRequest("서울특별시", "강남구")),
            new AcademyPageSettings(null, null, null, null, null, null,
                Map.of("designPreset", "ENGLISH"), null)),
        "ve-key-" + suffix);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private MvcResult putDraft(String body) throws Exception {
    return mockMvc.perform(put("/api/tutor/academy/page-settings/visual-editor/draft")
            .cookie(authCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andReturn();
  }

  private long revisionOf(MvcResult result) throws Exception {
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("revision").asLong();
  }

  @Test
  @DisplayName("저장 → 조회 라운드트립 (실제 HTTP)")
  void saveAndLoad_roundtrip() throws Exception {
    String html = "<section><h1>우리 학원</h1></section>";
    String css = "h1 { color: blue; }";

    MvcResult saved = putDraft(
        "{\"html\":\"" + html.replace("\"", "\\\"") + "\","
            + "\"css\":\"" + css.replace("\"", "\\\"") + "\","
            + "\"projectData\":\"{}\"}");
    assertThat(saved.getResponse().getStatus()).isEqualTo(200);
    long revision = revisionOf(saved);

    MvcResult loaded = mockMvc.perform(
            get("/api/tutor/academy/page-settings/visual-editor/draft")
                .cookie(authCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value((int) revision))
        .andReturn();
    JsonNode json = objectMapper.readTree(loaded.getResponse().getContentAsString());
    assertThat(json.get("html").asText()).isEqualTo(html);
    assertThat(json.get("css").asText()).isEqualTo(css);
  }

  @Test
  @DisplayName("최초 저장: revision null → 신규 생성, revision 0 → 기존 rev 0과 일치 시 저장")
  void firstSave_nullAndZeroRevision() throws Exception {
    // revision null + 초안 없음 → 신규 생성 (@Version 초기값 0)
    MvcResult r1 = putDraft("{\"html\":\"<p>a</p>\",\"css\":\"\",\"projectData\":\"{}\"}");
    assertThat(r1.getResponse().getStatus()).isEqualTo(200);
    assertThat(revisionOf(r1)).isEqualTo(0L);

    // revision 0 + 초안 rev 0 → 일치하므로 저장 성공, rev 1
    MvcResult r2 = putDraft("{\"html\":\"<p>b</p>\",\"css\":\"\",\"projectData\":\"{}\","
        + "\"revision\":0}");
    assertThat(r2.getResponse().getStatus()).isEqualTo(200);
    assertThat(revisionOf(r2)).isEqualTo(1L);
  }

  @Test
  @DisplayName("초안 존재 + revision 생략 → 400")
  void saveWithoutRevision_whenDraftExists_400() throws Exception {
    putDraft("{\"html\":\"<p>a</p>\",\"css\":\"\",\"projectData\":\"{}\"}");

    MvcResult result = putDraft(
        "{\"html\":\"<p>b</p>\",\"css\":\"\",\"projectData\":\"{}\"}");
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(result.getResponse().getContentAsString()).contains("revision");
  }

  @Test
  @DisplayName("revision 일치 → 저장 성공, revision 증가")
  void saveWithMatchingRevision_succeeds() throws Exception {
    MvcResult r1 = putDraft("{\"html\":\"<p>a</p>\",\"css\":\"\",\"projectData\":\"{}\"}");
    long rev = revisionOf(r1);

    MvcResult r2 = putDraft("{\"html\":\"<p>b</p>\",\"css\":\"\",\"projectData\":\"{}\","
        + "\"revision\":" + rev + "}");
    assertThat(r2.getResponse().getStatus()).isEqualTo(200);
    assertThat(revisionOf(r2)).isEqualTo(rev + 1);
  }

  @Test
  @DisplayName("stale revision → 409")
  void saveWithStaleRevision_409() throws Exception {
    MvcResult r1 = putDraft("{\"html\":\"<p>a</p>\",\"css\":\"\",\"projectData\":\"{}\"}");
    long rev = revisionOf(r1);
    // 한 번 더 저장해서 revision 증가
    putDraft("{\"html\":\"<p>b</p>\",\"css\":\"\",\"projectData\":\"{}\","
        + "\"revision\":" + rev + "}");

    MvcResult stale = putDraft("{\"html\":\"<p>c</p>\",\"css\":\"\",\"projectData\":\"{}\","
        + "\"revision\":" + rev + "}");
    assertThat(stale.getResponse().getStatus()).isEqualTo(409);
    JsonNode json = objectMapper.readTree(stale.getResponse().getContentAsString());
    assertThat(json.get("status").asInt()).isEqualTo(409);
  }

  @Test
  @DisplayName("HTML script XSS → 저장 시 정제됨")
  void xssScriptInHtml_sanitized() throws Exception {
    MvcResult saved = putDraft("{\"html\":\"<p>hi</p><script>alert(1)</script>\","
        + "\"css\":\"\",\"projectData\":\"{}\"}");
    assertThat(saved.getResponse().getStatus()).isEqualTo(200);

    MvcResult loaded = mockMvc.perform(
            get("/api/tutor/academy/page-settings/visual-editor/draft")
                .cookie(authCookie))
        .andExpect(status().isOk())
        .andReturn();
    String html = objectMapper.readTree(
        loaded.getResponse().getContentAsString()).get("html").asText();
    assertThat(html).doesNotContain("<script");
    assertThat(html).contains("hi");
  }

  @Test
  @DisplayName("projectData tagName=script → 400")
  void xssTagNameScript_400() throws Exception {
    String projectData = "{\\\"components\\\":[{\\\"tagName\\\":\\\"script\\\"}]}";
    MvcResult result = putDraft("{\"html\":\"<p>hi</p>\",\"css\":\"\","
        + "\"projectData\":\"" + projectData + "\"}");
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  @DisplayName("projectData components 문자열 script → 정제되어 저장")
  void xssComponentsString_sanitized() throws Exception {
    String projectData =
        "{\\\"components\\\":\\\"<section><script>alert(1)</script><p>ok</p></section>\\\"}";
    MvcResult saved = putDraft("{\"html\":\"<p>hi</p>\",\"css\":\"\","
        + "\"projectData\":\"" + projectData + "\"}");
    assertThat(saved.getResponse().getStatus()).isEqualTo(200);

    MvcResult loaded = mockMvc.perform(
            get("/api/tutor/academy/page-settings/visual-editor/draft")
                .cookie(authCookie))
        .andExpect(status().isOk())
        .andReturn();
    String stored = objectMapper.readTree(
        loaded.getResponse().getContentAsString()).get("projectData").asText();
    assertThat(stored).doesNotContain("<script");
  }

  @Test
  @DisplayName("CSS 정확히 65,536바이트 → 400 (500 아님)")
  void cssExactly65536_400() throws Exception {
    String css = "a{color:red;}".repeat(6000).substring(0, 65536);
    assertThat(css.getBytes(StandardCharsets.UTF_8).length).isEqualTo(65536);

    MvcResult result = putDraft("{\"html\":\"<p>hi</p>\","
        + "\"css\":\"" + css + "\",\"projectData\":\"{}\"}");
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(json.get("status").asInt()).isEqualTo(400);
  }

  @Test
  @DisplayName("CSS 65,535바이트 → 200")
  void css65535_ok() throws Exception {
    String css = "a{color:red;}".repeat(6000).substring(0, 65535);

    MvcResult result = putDraft("{\"html\":\"<p>hi</p>\","
        + "\"css\":\"" + css + "\",\"projectData\":\"{}\"}");
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("잘못된 요청 타입(html 숫자) → 400")
  void wrongHtmlType_400() throws Exception {
    MvcResult result = putDraft(
        "{\"html\":12345,\"css\":\"\",\"projectData\":\"{}\"}");
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  @DisplayName("P0 시맨틱 태그 보존")
  void p0SemanticTags_preserved() throws Exception {
    String html = "<section><header><h1>제목</h1></header>"
        + "<main><article><p>본문</p></article></main>"
        + "<footer><nav><a href=\"/about\">소개</a></nav></footer></section>";
    MvcResult saved = putDraft("{\"html\":\""
        + html.replace("\"", "\\\"") + "\",\"css\":\"\",\"projectData\":\"{}\"}");
    assertThat(saved.getResponse().getStatus()).isEqualTo(200);

    MvcResult loaded = mockMvc.perform(
            get("/api/tutor/academy/page-settings/visual-editor/draft")
                .cookie(authCookie))
        .andExpect(status().isOk())
        .andReturn();
    String stored = objectMapper.readTree(
        loaded.getResponse().getContentAsString()).get("html").asText();
    for (String tag : List.of("section", "header", "footer", "nav", "main", "article")) {
      assertThat(stored).contains("<" + tag);
    }
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @Test
  @DisplayName("병렬 8건 동일 revision → 1건만 200, 7건 409 (서로 다른 내용)")
  void concurrentSaves_oneWins() throws Exception {
    MvcResult r1 = putDraft("{\"html\":\"<p>init</p>\",\"css\":\"\",\"projectData\":\"{}\"}");
    assertThat(r1.getResponse().getStatus()).isEqualTo(200);
    long rev = revisionOf(r1);

    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger ok200 = new AtomicInteger();
    AtomicInteger conflict409 = new AtomicInteger();

    try {
      List<Future<?>> futures = new java.util.ArrayList<>();
      for (int i = 0; i < threads; i++) {
        final int idx = i;
        futures.add(pool.submit(() -> {
          ready.countDown();
          start.await();
          // 매 요청마다 다른 내용 사용
          MvcResult r = putDraft("{\"html\":\"<p>content-" + idx + "-"
              + UUID.randomUUID() + "</p>\",\"css\":\"\",\"projectData\":\"{}\","
              + "\"revision\":" + rev + "}");
          int code = r.getResponse().getStatus();
          if (code == 200) {
            ok200.incrementAndGet();
          } else if (code == 409) {
            conflict409.incrementAndGet();
          }
          return null;
        }));
      }
      ready.await();
      start.countDown();
      for (Future<?> f : futures) {
        f.get(30, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(ok200.get()).isEqualTo(1);
    assertThat(conflict409.get()).isEqualTo(threads - 1);
  }
}
