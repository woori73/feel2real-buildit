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
import org.springframework.jdbc.core.JdbcTemplate;
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

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @jakarta.persistence.PersistenceContext
  private jakarta.persistence.EntityManager entityManager;

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
    // 공개 homepage API 테스트를 위해 학원을 PUBLISHED로 변경
    // (공개 API는 기존 규칙대로 PUBLISHED 학원만 serving).
    // Academy에는 status setter가 없어 JDBC로 직접 바꾼다. JDBC UPDATE는 영속성 컨텍스트를
    // 우회하므로, 컨텍스트에 DRAFT 상태 엔티티가 남아 있으면 이후 dirty flush(setRenderMode 등)가
    // status='DRAFT'를 되덮어써 공개 API가 404가 된다. 그래서 UPDATE 전에 flush, 후에 clear 한다.
    // (테스트 트랜잭션이 없는 NOT_SUPPORTED 동시성 테스트는 영속성 컨텍스트가 공유되지 않아 불필요)
    boolean inTestTransaction =
        org.springframework.test.context.transaction.TestTransaction.isActive();
    if (inTestTransaction) {
      entityManager.flush();
    }
    int updated = jdbcTemplate.update(
        "UPDATE academies SET status = 'PUBLISHED', is_visible = true "
            + "WHERE tutor_user_id = ?",
        tutor.getUserId());
    if (updated != 1) {
      throw new IllegalStateException(
          "setUp: academies UPDATE affected " + updated + " rows, expected 1");
    }
    if (inTestTransaction) {
      entityManager.clear();
    }
  }

  /**
   * 테스트용 학원의 handle을 반환한다.
   */

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    // 클래스 기본은 테스트 종료 시 롤백되지만, NOT_SUPPORTED 동시성 테스트는 setUp 데이터가
    // 커밋된다. 다른 테스트/재실행에 남지 않도록 직접 정리한다 (academies 삭제 시 FK cascade).
    if (!org.springframework.test.context.transaction.TestTransaction.isActive()) {
      jdbcTemplate.update("DELETE FROM academies WHERE tutor_user_id = ?", tutor.getUserId());
      jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", tutor.getUserId());
    }
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
    // Jsoup 정제가 블록 요소에 개행을 넣어 pretty-print하므로 공백 정규화 후 비교
    assertThat(json.get("html").asText().replaceAll("\\s+", ""))
        .isEqualTo(html.replaceAll("\\s+", ""));
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
  void concurrentSaves_oneWins() throws Exception {    MvcResult r1 = putDraft("{\"html\":\"<p>init</p>\",\"css\":\"\",\"projectData\":\"{}\"}");
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

  // ── 2단계: 게시·가져오기·공개 홈페이지 ──

  private MvcResult postPublish(String body) throws Exception {
    return mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/tutor/academy/page-settings/visual-editor/publish")
                .cookie(authCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  private MvcResult postImport(String body) throws Exception {
    return mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/tutor/academy/page-settings/visual-editor/import")
                .cookie(authCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  private MvcResult postUnpublish() throws Exception {
    return mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/tutor/academy/page-settings/visual-editor/unpublish")
                .cookie(authCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andReturn();
  }

  private String myHandle() throws Exception {
    MvcResult me = mockMvc.perform(get("/api/tutor/academy/me").cookie(authCookie))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(
        me.getResponse().getContentAsString()).get("handle").asText();
  }

  @Test
  @DisplayName("게시: 초안 → published 복사 + renderMode=custom, 공개 API에서 조회")
  void publish_flow() throws Exception {
    String html = "<section><h1>게시 테스트</h1></section>";
    MvcResult saved = putDraft("{\"html\":\"" + html.replace("\"", "\\\"") + "\","
        + "\"css\":\"h1{color:red}\",\"projectData\":\"{}\"}");
    assertThat(saved.getResponse().getStatus()).isEqualTo(200);
    long rev = revisionOf(saved);

    MvcResult published = postPublish("{\"revision\":" + rev + "}");
    assertThat(published.getResponse().getStatus()).isEqualTo(200);
    JsonNode pub = objectMapper.readTree(
        published.getResponse().getContentAsString());
    assertThat(pub.get("status").asText()).isEqualTo("PUBLISHED");
    assertThat(pub.get("renderMode").asText()).isEqualTo("custom");

    // 공개 API에서 게시본 조회 (인증 불필요)
    String handle = myHandle();
    MvcResult homepage = mockMvc.perform(
            get("/api/public/academy/" + handle + "/homepage"))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode hp = objectMapper.readTree(
        homepage.getResponse().getContentAsString());
    assertThat(hp.get("renderMode").asText()).isEqualTo("custom");
    assertThat(hp.get("html").asText()).contains("게시 테스트");
  }

  @Test
  @DisplayName("게시: 초안 없음 → 409")
  void publish_withoutDraft_409() throws Exception {
    MvcResult result = postPublish("{\"revision\":0}");
    assertThat(result.getResponse().getStatus()).isEqualTo(409);
  }

  @Test
  @DisplayName("게시: revision 생략 → 400")
  void publish_withoutRevision_400() throws Exception {
    putDraft("{\"html\":\"<p>a</p>\",\"css\":\"\",\"projectData\":\"{}\"}");
    MvcResult result = postPublish("{}");
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  @DisplayName("게시: stale revision → 409")
  void publish_staleRevision_409() throws Exception {
    MvcResult r1 = putDraft("{\"html\":\"<p>a</p>\",\"css\":\"\",\"projectData\":\"{}\"}");
    long rev = revisionOf(r1);
    putDraft("{\"html\":\"<p>b</p>\",\"css\":\"\",\"projectData\":\"{}\","
        + "\"revision\":" + rev + "}");

    MvcResult result = postPublish("{\"revision\":" + rev + "}");
    assertThat(result.getResponse().getStatus()).isEqualTo(409);
  }

  @Test
  @DisplayName("게시 취소: renderMode=template, 공개 API 404 → P0 폴백")
  void unpublish_flow() throws Exception {
    MvcResult saved = putDraft("{\"html\":\"<p>a</p>\",\"css\":\"\",\"projectData\":\"{}\"}");
    long rev = revisionOf(saved);
    assertThat(postPublish("{\"revision\":" + rev + "}")
        .getResponse().getStatus()).isEqualTo(200);

    MvcResult unpub = postUnpublish();
    assertThat(unpub.getResponse().getStatus()).isEqualTo(200);
    JsonNode un = objectMapper.readTree(
        unpub.getResponse().getContentAsString());
    assertThat(un.get("renderMode").asText()).isEqualTo("template");

    String handle = myHandle();
    mockMvc.perform(get("/api/public/academy/" + handle + "/homepage"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("공개 홈페이지: 게시본 없음 → 404")
  void publicHomepage_withoutPublished_404() throws Exception {
    String handle = myHandle();
    mockMvc.perform(get("/api/public/academy/" + handle + "/homepage"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("공개 홈페이지: XSS는 출력 시 재검증되어 제거됨")
  void publicHomepage_xssSanitized() throws Exception {
    // 저장 시 script가 제거되므로, 정제된 내용이 게시·공개되는지 확인
    MvcResult saved = putDraft(
        "{\"html\":\"<p>ok</p><script>alert(1)</script>\","
            + "\"css\":\"\",\"projectData\":\"{}\"}");
    assertThat(saved.getResponse().getStatus()).isEqualTo(200);
    long rev = revisionOf(saved);
    assertThat(postPublish("{\"revision\":" + rev + "}")
        .getResponse().getStatus()).isEqualTo(200);

    String handle = myHandle();
    MvcResult homepage = mockMvc.perform(
            get("/api/public/academy/" + handle + "/homepage"))
        .andExpect(status().isOk())
        .andReturn();
    String html = objectMapper.readTree(
        homepage.getResponse().getContentAsString()).get("html").asText();
    assertThat(html).doesNotContain("<script");
    assertThat(html).contains("ok");
  }

  @Test
  @DisplayName("가져오기: 초안 없음 → 신규 초안 생성")
  void import_noExistingDraft_creates() throws Exception {
    String p0html = "<section><h1>P0 홈페이지</h1></section>";
    MvcResult result = postImport("{\"html\":\""
        + p0html.replace("\"", "\\\"") + "\",\"css\":\"h1{color:blue}\"}");
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode json = objectMapper.readTree(
        result.getResponse().getContentAsString());
    assertThat(json.get("status").asText()).isEqualTo("IMPORTED");

    // 초안으로 조회됨
    MvcResult loaded = mockMvc.perform(
            get("/api/tutor/academy/page-settings/visual-editor/draft")
                .cookie(authCookie))
        .andExpect(status().isOk())
        .andReturn();
    String stored = objectMapper.readTree(
        loaded.getResponse().getContentAsString()).get("html").asText();
    assertThat(stored).contains("P0 홈페이지");
  }

  @Test
  @DisplayName("가져오기: 기존 초안 있음 → 409 (무단 덮어쓰기 금지)")
  void import_existingDraft_409() throws Exception {
    putDraft("{\"html\":\"<p>existing</p>\",\"css\":\"\",\"projectData\":\"{}\"}");

    MvcResult result = postImport(
        "{\"html\":\"<p>p0</p>\",\"css\":\"\"}");
    assertThat(result.getResponse().getStatus()).isEqualTo(409);

    // 기존 초안이 그대로 유지됨
    MvcResult loaded = mockMvc.perform(
            get("/api/tutor/academy/page-settings/visual-editor/draft")
                .cookie(authCookie))
        .andExpect(status().isOk())
        .andReturn();
    String stored = objectMapper.readTree(
        loaded.getResponse().getContentAsString()).get("html").asText();
    assertThat(stored).contains("existing");
  }

  @Test
  @DisplayName("가져오기: XSS 포함 P0 HTML → 정제되어 저장")
  void import_xssSanitized() throws Exception {
    MvcResult result = postImport(
        "{\"html\":\"<p>ok</p><script>alert(1)</script>\",\"css\":\"\"}");
    assertThat(result.getResponse().getStatus()).isEqualTo(200);

    MvcResult loaded = mockMvc.perform(
            get("/api/tutor/academy/page-settings/visual-editor/draft")
                .cookie(authCookie))
        .andExpect(status().isOk())
        .andReturn();
    String stored = objectMapper.readTree(
        loaded.getResponse().getContentAsString()).get("html").asText();
    assertThat(stored).doesNotContain("<script");
    assertThat(stored).contains("ok");
  }

  @Test
  @DisplayName("renderMode: 잘못된 값 → 400")
  void renderMode_invalidValue_400() throws Exception {
    MvcResult result = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/tutor/academy/page-settings")
                .cookie(authCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"builderDraft\":{\"homepage.renderMode\":\"invalid\"}}"))
        .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @Test
  @DisplayName("병렬 게시 8건 동일 revision → 직렬화되어 전부 200(멱등), 5xx/데드락 없음, 게시본 1건")
  void concurrentPublish_idempotent() throws Exception {
    MvcResult r1 = putDraft("{\"html\":\"<p>pub</p>\",\"css\":\"\",\"projectData\":\"{}\"}");
    assertThat(r1.getResponse().getStatus()).isEqualTo(200);
    long rev = revisionOf(r1);

    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger ok200 = new AtomicInteger();
    AtomicInteger conflict409 = new AtomicInteger();
    AtomicInteger other = new AtomicInteger();
    java.util.concurrent.ConcurrentLinkedQueue<Integer> otherCodes =
        new java.util.concurrent.ConcurrentLinkedQueue<>();

    try {
      List<Future<?>> futures = new java.util.ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(pool.submit(() -> {
          ready.countDown();
          start.await();
          MvcResult r = postPublish("{\"revision\":" + rev + "}");
          int code = r.getResponse().getStatus();
          if (code == 200) {
            ok200.incrementAndGet();
          } else if (code == 409) {
            conflict409.incrementAndGet();
          } else {
            other.incrementAndGet();
            otherCodes.add(code);
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

    // 학원 행 락으로 게시가 직렬화되므로 같은 revision 재게시는 순차 호출과 동일하게 멱등(200).
    // (409 개수는 스레드 타이밍에 달라지므로 기대하지 않는다.) 5xx/데드락은 없어야 한다.
    assertThat(otherCodes).as("5xx 등 예상 외 응답 코드").isEmpty();
    assertThat(conflict409.get()).isZero();
    assertThat(ok200.get()).isEqualTo(threads);

    // 최종 게시본이 정상
    String handle = myHandle();
    MvcResult homepage = mockMvc.perform(
            get("/api/public/academy/" + handle + "/homepage"))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode hpJson = objectMapper.readTree(
        homepage.getResponse().getContentAsString());
    assertThat(hpJson.get("html").asText()).contains("pub");
    // renderMode 일관성: custom으로 설정되어 있어야 함
    assertThat(hpJson.get("renderMode").asText()).isEqualTo("custom");

    // DB 직접 확인: published 1건, renderMode=custom
    Long publishedCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM academy_visual_editor_published "
            + "WHERE academy_id = (SELECT academy_id FROM academies WHERE tutor_user_id = ?)",
        Long.class, tutor.getUserId());
    assertThat(publishedCount).isEqualTo(1L);
  }

  @Test
  @DisplayName("중복 게시 (순차 2회) → 모두 200 (멱등)")
  void duplicatePublish_sequential_idempotent() throws Exception {
    MvcResult r1 = putDraft("{\"html\":\"<p>dup</p>\",\"css\":\"\",\"projectData\":\"{}\"}");
    assertThat(r1.getResponse().getStatus()).isEqualTo(200);
    long rev = revisionOf(r1);

    assertThat(postPublish("{\"revision\":" + rev + "}")
        .getResponse().getStatus()).isEqualTo(200);
    // 같은 revision으로 다시 게시 → 멱등하게 200
    assertThat(postPublish("{\"revision\":" + rev + "}")
        .getResponse().getStatus()).isEqualTo(200);
  }

  // ── 안정화 1차: 실제 GrapesJS 출력 / 공개 반영 일치 / 기존 학원 호환 ──

  private JsonNode grapesFixture() throws Exception {
    try (java.io.InputStream in = getClass()
        .getResourceAsStream("/visual-editor/grapesjs-project.json")) {
      return objectMapper.readTree(in);
    }
  }

  private String draftBody(JsonNode fx, Long revision) throws Exception {
    var node = objectMapper.createObjectNode()
        .put("html", fx.get("html").asText())
        .put("css", fx.get("css").asText())
        .put("projectData", fx.get("projectData").toString());
    if (revision != null) {
      node.put("revision", revision);
    }
    return objectMapper.writeValueAsString(node);
  }

  private JsonNode me() throws Exception {
    return objectMapper.readTree(mockMvc.perform(get("/api/tutor/academy/me").cookie(authCookie))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  @Test
  @DisplayName("실제 GrapesJS 출력(head/docEl/page type 포함): 저장 200 → 재불러오기 → 재저장 200")
  void realGrapesJsOutput_saveReloadResave() throws Exception {
    // given: 실제 GrapesJS 0.23.6이 만든 projectData/html/css
    JsonNode fx = grapesFixture();

    // when: 최초 저장(수동 저장과 자동 저장은 같은 PUT API를 쓴다)
    MvcResult first = putDraft(draftBody(fx, null));

    // then
    assertThat(first.getResponse().getStatus())
        .as("실제 projectData 저장: " + first.getResponse().getContentAsString())
        .isEqualTo(200);
    long rev = revisionOf(first);

    // when: 재불러오기
    JsonNode loaded = objectMapper.readTree(
        mockMvc.perform(get("/api/tutor/academy/page-settings/visual-editor/draft")
                .cookie(authCookie))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

    // then: 편집기가 loadProjectData에 넘길 projectData 구조가 보존된다
    JsonNode pd = objectMapper.readTree(loaded.get("projectData").asText());
    JsonNode component = pd.get("pages").get(0).get("frames").get(0).get("component");
    assertThat(component.get("head").toString()).isEqualTo("{\"type\":\"head\"}");
    assertThat(component.get("docEl").toString()).isEqualTo("{\"tagName\":\"html\"}");
    assertThat(pd.get("pages").get(0).get("type").asText()).isEqualTo("main");
    assertThat(pd.get("styles").size()).isEqualTo(fx.get("projectData").get("styles").size());
    assertThat(loaded.get("html").asText()).contains("학원 이름").contains("id=\"");
    assertThat(loaded.get("css").asText()).contains("@media (max-width: 768px)");

    // when: 재불러오기한 projectData를 그대로 다시 저장(편집기가 로드 후 저장하는 흐름)
    var resave = objectMapper.createObjectNode()
        .put("html", loaded.get("html").asText())
        .put("css", loaded.get("css").asText())
        .put("projectData", loaded.get("projectData").asText())
        .put("revision", rev);
    MvcResult second = putDraft(objectMapper.writeValueAsString(resave));

    // then
    assertThat(second.getResponse().getStatus())
        .as("재저장: " + second.getResponse().getContentAsString()).isEqualTo(200);
  }

  @Test
  @DisplayName("실제 projectData에 script를 섞으면 여전히 400 (head 예외가 우회로가 되지 않는다)")
  void realGrapesJsOutput_withScript_stillRejected() throws Exception {
    // given
    JsonNode fx = grapesFixture();
    var pd = (com.fasterxml.jackson.databind.node.ObjectNode) fx.get("projectData").deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) pd.get("pages").get(0).get("frames")
        .get(0).get("component")).set("head", objectMapper.createObjectNode()
        .put("type", "head")
        .set("components", objectMapper.createArrayNode()
            .add(objectMapper.createObjectNode().put("tagName", "script"))));
    var body = objectMapper.createObjectNode()
        .put("html", "<p>x</p>").put("css", "").put("projectData", pd.toString());

    // when
    MvcResult res = putDraft(objectMapper.writeValueAsString(body));

    // then
    assertThat(res.getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  @DisplayName("게시: 학원이 비공개(DRAFT)면 응답이 publicReachable=false로 사실을 알린다")
  void publish_notReachableWhenAcademyIsDraft() throws Exception {
    // given: 초안 저장 + 학원을 비공개로 변경
    long rev = revisionOf(putDraft("{\"html\":\"<p>hi</p>\",\"css\":\"\",\"projectData\":\"{}\"}"));
    entityManager.flush();
    jdbcTemplate.update("UPDATE academies SET status = 'DRAFT' WHERE tutor_user_id = ?",
        tutor.getUserId());
    entityManager.clear();

    // when
    MvcResult res = postPublish("{\"revision\":" + rev + "}");

    // then: 게시 자체는 성공하지만 방문자에게 보이지 않는다고 정직하게 응답한다
    assertThat(res.getResponse().getStatus()).isEqualTo(200);
    JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
    assertThat(json.get("publicReachable").asBoolean()).isFalse();
    assertThat(json.get("message").asText()).contains("방문자에게는 아직 보이지 않습니다");
    assertThat(json.get("publicPath").asText()).startsWith("/academies/");
  }

  @Test
  @DisplayName("게시: 공개 학원이면 publicReachable=true")
  void publish_reachableWhenAcademyPublished() throws Exception {
    // given
    long rev = revisionOf(putDraft("{\"html\":\"<p>hi</p>\",\"css\":\"\",\"projectData\":\"{}\"}"));

    // when
    JsonNode json = objectMapper.readTree(
        postPublish("{\"revision\":" + rev + "}").getResponse().getContentAsString());

    // then
    assertThat(json.get("publicReachable").asBoolean()).isTrue();
    assertThat(json.get("renderMode").asText()).isEqualTo("custom");
  }

  @Test
  @DisplayName("게시: 빈 초안은 400 (공개 페이지가 P0로 되돌아가 게시 성공과 어긋나는 것을 막는다)")
  void publish_emptyDraft_400() throws Exception {
    // given: 빈 HTML 초안
    long rev = revisionOf(putDraft("{\"html\":\"\",\"css\":\"\",\"projectData\":\"{}\"}"));

    // when
    MvcResult res = postPublish("{\"revision\":" + rev + "}");

    // then
    assertThat(res.getResponse().getStatus()).isEqualTo(400);
    assertThat(res.getResponse().getContentAsString()).contains("빈 초안");
  }

  @Test
  @DisplayName("handle이 없는 기존 학원: 게시 후 slug 경로로 편집기 홈페이지가 공개된다")
  void publish_worksForAcademyWithoutHandle_viaSlugEndpoint() throws Exception {
    // given: handle이 없는 기존 학원 (V926: 기존 Academy는 handle 없이 경로형으로 유지)
    entityManager.flush();
    jdbcTemplate.update("UPDATE academies SET handle = NULL WHERE tutor_user_id = ?",
        tutor.getUserId());
    entityManager.clear();
    String slug = me().get("slug").asText();
    assertThat(me().get("handle").isNull()).isTrue();
    long rev = revisionOf(putDraft(
        "{\"html\":\"<section id=\\\"a1\\\"><h1>기존 학원 홈</h1></section>\","
            + "\"css\":\"#a1{color:red}\",\"projectData\":\"{}\"}"));

    // when
    assertThat(postPublish("{\"revision\":" + rev + "}").getResponse().getStatus()).isEqualTo(200);

    // then: slug 엔드포인트는 200, handle 엔드포인트는 해당 학원을 찾지 못한다
    mockMvc.perform(get("/api/academies/" + slug + "/homepage"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.renderMode").value("custom"))
        .andExpect(jsonPath("$.html").value(
            org.hamcrest.Matchers.containsString("기존 학원 홈")))
        .andExpect(jsonPath("$.css").value(org.hamcrest.Matchers.containsString("#a1")));
    mockMvc.perform(get("/api/public/academy/no-such-handle/homepage"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("slug 공개 API: 게시 전/게시 취소 후/존재하지 않는 slug는 404")
  void slugEndpoint_404WhenNotCustomOrUnknown() throws Exception {
    // given
    String slug = me().get("slug").asText();
    long rev = revisionOf(putDraft("{\"html\":\"<p>hi</p>\",\"css\":\"\",\"projectData\":\"{}\"}"));

    // then 1: 게시 전
    mockMvc.perform(get("/api/academies/" + slug + "/homepage")).andExpect(status().isNotFound());

    // when: 게시 → 게시 취소
    postPublish("{\"revision\":" + rev + "}");
    mockMvc.perform(get("/api/academies/" + slug + "/homepage")).andExpect(status().isOk());
    postUnpublish();

    // then 2: 취소 후에는 다시 404 → 공개 페이지가 P0로 폴백
    mockMvc.perform(get("/api/academies/" + slug + "/homepage")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/academies/not-a-real-slug-zzz/homepage"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("게시·게시 취소는 기존 학원 데이터(P0 설정·이름·소개)를 보존한다")
  void publishUnpublish_preservesExistingAcademyData() throws Exception {
    // given
    JsonNode before = me();
    long rev = revisionOf(putDraft("{\"html\":\"<p>hi</p>\",\"css\":\"\",\"projectData\":\"{}\"}"));

    // when
    postPublish("{\"revision\":" + rev + "}");
    JsonNode published = me();
    postUnpublish();
    JsonNode after = me();

    // then: renderMode만 바뀌고 나머지 P0 설정은 그대로
    assertThat(published.get("pageSettings").get("builderDraft").get("homepage.renderMode")
        .asText()).isEqualTo("custom");
    assertThat(after.get("pageSettings").get("builderDraft").get("homepage.renderMode")
        .asText()).isEqualTo("template");
    assertThat(after.get("pageSettings").get("builderDraft").get("designPreset").asText())
        .isEqualTo(before.get("pageSettings").get("builderDraft").get("designPreset").asText());
    assertThat(after.get("name").asText()).isEqualTo(before.get("name").asText());
    assertThat(after.get("intro").asText()).isEqualTo(before.get("intro").asText());
    assertThat(after.get("slug").asText()).isEqualTo(before.get("slug").asText());
    assertThat(after.get("status").asText()).isEqualTo(before.get("status").asText());
  }

  @Test
  @DisplayName("상태 API: 초안·게시·미게시 변경을 서버 사실대로 반영한다")
  void statusEndpoint_reflectsDraftAndPublishedState() throws Exception {
    String url = "/api/tutor/academy/page-settings/visual-editor/status";

    // 1) 초안 없음
    mockMvc.perform(get(url).cookie(authCookie)).andExpect(status().isOk())
        .andExpect(jsonPath("$.draftExists").value(false))
        .andExpect(jsonPath("$.renderMode").value("template"))
        .andExpect(jsonPath("$.publicReachable").value(true));

    // 2) 초안 저장 → 미게시 변경 있음
    long rev = revisionOf(putDraft("{\"html\":\"<p>a</p>\",\"css\":\"\",\"projectData\":\"{}\"}"));
    mockMvc.perform(get(url).cookie(authCookie))
        .andExpect(jsonPath("$.draftExists").value(true))
        .andExpect(jsonPath("$.hasUnpublishedChanges").value(true));

    // 3) 게시 → 미게시 변경 없음, custom
    postPublish("{\"revision\":" + rev + "}");
    mockMvc.perform(get(url).cookie(authCookie))
        .andExpect(jsonPath("$.renderMode").value("custom"))
        .andExpect(jsonPath("$.hasUnpublishedChanges").value(false))
        .andExpect(jsonPath("$.publishedAt").isNotEmpty());

    // 4) 다시 수정 → 미게시 변경 있음
    putDraft("{\"html\":\"<p>b</p>\",\"css\":\"\",\"projectData\":\"{}\",\"revision\":" + rev + "}");
    mockMvc.perform(get(url).cookie(authCookie))
        .andExpect(jsonPath("$.hasUnpublishedChanges").value(true));
  }
}
