package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyRegionRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * AI 제안 전체 흐름의 실제 MySQL 통합 테스트(실제 HTTP·필터체인·JWT/Bearer 인증, 실제 Proposal 구현체).
 *
 * <p>검증: 토큰 발급/인증/폐기, Agent 권한 경계, 학원 격리(404), 승인 저장(APPLIED 원자성), 검증 실패 롤백,
 * 중복 적용 방지, 거절·만료·STALE, AI 수정 요청, 승인 전 공개 페이지·게시본 불변.
 * 클래스에 @Transactional이 없다(롤백/동시성을 실제로 보기 위해). 데이터는 UUID로 격리하고 직접 정리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("시각 편집기 AI 제안 전체 흐름 (격리 MySQL, 실제 HTTP)")
class VisualEditorProposalFlowMySqlTest {

  private static final String VE = "/api/tutor/academy/page-settings/visual-editor";
  private static final String AGENT = "/api/agent/academy/visual-editor";
  private static final ObjectMapper M = new ObjectMapper();

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider tokenProvider;
  @Autowired private AcademyService academyService;
  @Autowired private UserRepository userRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private AiProvider aiProvider;

  /** 한 학원(교육자 + 쿠키). */
  private record Edu(User user, Cookie cookie, long academyId, String slug) {
  }

  private final List<Edu> created = new ArrayList<>();
  private Edu a;
  private Edu b;

  @BeforeEach
  void setUp() {
    a = newEdu();
    b = newEdu();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    for (Edu e : created) {
      jdbc.update("DELETE FROM academies WHERE tutor_user_id = ?", e.user().getUserId());
      jdbc.update("DELETE FROM users WHERE user_id = ?", e.user().getUserId());
    }
  }

  private Edu newEdu() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    User tutor = userRepository.save(new User(
        "vf_" + suffix, "pw", "강사" + suffix, "별명", null, null, Role.TUTOR));
    var principal = new JwtTokenProvider.AuthenticatedPrincipal(
        tutor.getUserId(), tutor.getLoginId(), "TUTOR");
    Subject subject = subjectRepository.findByNameKo("영어")
        .orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    academyService.createDraft(principal,
        new AcademyUpsertRequest("흐름테스트학원" + suffix, null, "소개문",
            Set.of(subject.getSubjectId()), Set.of(TargetGroup.MIDDLE),
            List.of(new AcademyRegionRequest("서울특별시", "강남구")),
            new AcademyPageSettings(null, null, null, null, null, null,
                Map.of("designPreset", "ENGLISH"), null)),
        "vf-key-" + suffix);
    long id = jdbc.queryForObject(
        "SELECT academy_id FROM academies WHERE tutor_user_id = ?", Long.class, tutor.getUserId());
    jdbc.update("UPDATE academies SET status = 'PUBLISHED', is_visible = true WHERE academy_id = ?", id);
    String slug = jdbc.queryForObject("SELECT slug FROM academies WHERE academy_id = ?", String.class, id);
    Edu edu = new Edu(tutor,
        new Cookie("ACCESS_TOKEN", tokenProvider.issue(tutor.getUserId(), tutor.getLoginId(), "TUTOR")),
        id, slug);
    created.add(edu);
    SecurityContextHolder.clearContext();
    return edu;
  }

  // ───────── helpers ─────────

  private MvcResult call(MockHttpServletRequestBuilder req) throws Exception {
    return mockMvc.perform(req).andReturn();
  }

  private MockHttpServletRequestBuilder asEdu(MockHttpServletRequestBuilder r, Edu e, Object body)
      throws Exception {
    r.cookie(e.cookie()).contentType(MediaType.APPLICATION_JSON);
    if (body != null) {
      r.content(body instanceof String s ? s : M.writeValueAsString(body));
    }
    return r;
  }

  private MockHttpServletRequestBuilder asAgent(MockHttpServletRequestBuilder r, String token, Object body)
      throws Exception {
    r.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    if (body != null) {
      r.content(M.writeValueAsString(body));
    }
    return r;
  }

  private JsonNode json(MvcResult r) throws Exception {
    String s = r.getResponse().getContentAsString();
    return s.isBlank() ? M.createObjectNode() : M.readTree(s);
  }

  /** 교육자 쿠키로 토큰을 발급받는다(평문 반환). */
  private String issueToken(Edu e) throws Exception {
    MvcResult r = call(asEdu(post(VE + "/agent-tokens"), e, Map.of("label", "테스트 에이전트")));
    assertThat(r.getResponse().getStatus()).isEqualTo(201);
    return json(r).get("token").asText();
  }

  private Map<String, Object> draftBody(ObjectNode pd, Long revision) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("html", ProposalFixtures.html());
    body.put("css", ProposalFixtures.css());
    body.put("projectData", ProposalFixtures.json(pd));
    if (revision != null) {
      body.put("revision", revision);
    }
    return body;
  }

  /** 기준 초안(fixture)을 저장하고 revision을 돌려준다. */
  private long saveBaseDraft(Edu e) throws Exception {
    MvcResult r = call(asEdu(put(VE + "/draft"), e, draftBody(ProposalFixtures.projectData(), null)));
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return json(r).get("revision").asLong();
  }

  private Map<String, Object> titleProposal(String text) {
    return Map.of("summary", "제목 변경", "changes", List.of(
        Map.of("componentId", ProposalFixtures.TITLE_ID, "type", "text", "after", text)));
  }

  private long propose(String token, String text) throws Exception {
    MvcResult r = call(asAgent(post(AGENT + "/proposals"), token, titleProposal(text)));
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
    return json(r).get("proposalId").asLong();
  }

  private MvcResult putApplied(Edu e, String titleText, long revision, long proposalId) throws Exception {
    ObjectNode pd = ProposalFixtures.projectData();
    ProposalFixtures.setTitleText(pd, titleText);
    Map<String, Object> body = draftBody(pd, revision);
    body.put("appliedProposalId", proposalId);
    return call(asEdu(put(VE + "/draft"), e, body));
  }

  private String status(long proposalId) {
    return jdbc.queryForObject(
        "SELECT status FROM academy_visual_editor_proposal WHERE proposal_id = ?", String.class, proposalId);
  }

  private long draftRevision(Edu e) {
    return jdbc.queryForObject(
        "SELECT revision FROM academy_visual_editor_draft WHERE academy_id = ?", Long.class, e.academyId());
  }

  private String draftProjectData(Edu e) {
    return jdbc.queryForObject(
        "SELECT project_data FROM academy_visual_editor_draft WHERE academy_id = ?", String.class, e.academyId());
  }

  private int publishedRows(Edu e) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_visual_editor_published WHERE academy_id = ?", Integer.class, e.academyId());
  }

  // ───────── 토큰 ─────────

  @Test
  @DisplayName("given 교육자 when 토큰 발급 then 평문은 응답에만 있고 DB·목록에는 해시/메타만 있다")
  void token_issueListRevoke() throws Exception {
    String token = issueToken(a);

    String hash = jdbc.queryForObject(
        "SELECT token_hash FROM academy_visual_editor_agent_token WHERE academy_id = ?", String.class, a.academyId());
    assertThat(hash).isNotEqualTo(token).hasSize(64);
    MvcResult list = call(asEdu(get(VE + "/agent-tokens"), a, null));
    assertThat(list.getResponse().getContentAsString()).doesNotContain(token).doesNotContain(hash);
    long tokenId = json(list).get(0).get("tokenId").asLong();

    assertThat(call(asAgent(get(AGENT + "/context"), token, null)).getResponse().getStatus()).isEqualTo(200);
    assertThat(call(asEdu(delete(VE + "/agent-tokens/" + tokenId), a, null)).getResponse().getStatus())
        .isEqualTo(200);
    assertThat(call(asAgent(get(AGENT + "/context"), token, null)).getResponse().getStatus())
        .as("폐기된 토큰").isIn(401, 403);
  }

  @Test
  @DisplayName("given 다른 학원의 토큰 id when 폐기 then 404이고 토큰은 살아 있다")
  void token_revokeOtherAcademy() throws Exception {
    String tokenOfA = issueToken(a);
    long idOfA = jdbc.queryForObject(
        "SELECT token_id FROM academy_visual_editor_agent_token WHERE academy_id = ?", Long.class, a.academyId());

    assertThat(call(asEdu(delete(VE + "/agent-tokens/" + idOfA), b, null)).getResponse().getStatus())
        .isEqualTo(404);
    assertThat(call(asAgent(get(AGENT + "/context"), tokenOfA, null)).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("given 활성 토큰 5개 when 6번째 발급 then 409")
  void token_limit() throws Exception {
    for (int i = 0; i < 5; i++) {
      issueToken(a);
    }
    MvcResult r = call(asEdu(post(VE + "/agent-tokens"), a, Map.of("label", "6번째")));
    assertThat(r.getResponse().getStatus()).isEqualTo(409);
  }

  // ───────── Agent 권한 경계 ─────────

  @Test
  @DisplayName("given 무효·없는 토큰, 교육자 쿠키 when Agent API then 거부(인증 실패 사유는 구분하지 않음)")
  void agent_authBoundary() throws Exception {
    assertThat(call(get(AGENT + "/context")).getResponse().getStatus()).isIn(401, 403);
    assertThat(call(asAgent(get(AGENT + "/context"), "vea_nope", null)).getResponse().getStatus())
        .isIn(401, 403);
    assertThat(call(asAgent(get(AGENT + "/context"), "not-a-vea-token", null)).getResponse().getStatus())
        .isIn(401, 403);
    // 교육자 쿠키(TUTOR)도 Agent 경로를 쓸 수 없다.
    assertThat(call(asEdu(get(AGENT + "/context"), a, null)).getResponse().getStatus()).isEqualTo(403);
    assertThat(call(asEdu(post(AGENT + "/proposals"), a, titleProposal("x"))).getResponse().getStatus())
        .isEqualTo(403);
  }

  @Test
  @DisplayName("given Agent 토큰 when 초안·게시·토큰발급·제안거절 등 쓰기 경로 then 접근 불가")
  void agent_cannotReachOtherEndpoints() throws Exception {
    String token = issueToken(a);
    saveBaseDraft(a);

    for (MockHttpServletRequestBuilder req : List.of(
        put(VE + "/draft"), post(VE + "/publish"), post(VE + "/unpublish"),
        post(VE + "/agent-tokens"), post(VE + "/proposals/1/reject"), post(VE + "/ai-requests"))) {
      MvcResult r = call(asAgent(req, token, Map.of("html", "<p>x</p>", "revision", 0)));
      assertThat(r.getResponse().getStatus()).as(req.toString()).isIn(401, 403);
    }
    assertThat(call(asAgent(get(VE + "/draft"), token, null)).getResponse().getStatus()).isIn(401, 403);
    assertThat(draftProjectData(a)).contains(ProposalFixtures.TITLE_TEXT);
    assertThat(publishedRows(a)).isZero();
  }

  // ───────── 제안 생성 / 승인 전 불변 ─────────

  @Test
  @DisplayName("given Agent 제안 when 생성 then PENDING이고 초안·게시본·학원 설정·공개 응답이 전혀 바뀌지 않는다")
  void propose_doesNotTouchHomepage() throws Exception {
    String token = issueToken(a);
    long rev = saveBaseDraft(a);
    String pageSettingsBefore = jdbc.queryForObject(
        "SELECT CAST(page_settings AS CHAR) FROM academies WHERE academy_id = ?", String.class, a.academyId());
    String publicBefore = call(get("/api/academies/" + a.slug())).getResponse().getContentAsString();
    String projectBefore = draftProjectData(a);

    long pid = propose(token, "AI가 제안한 제목");

    assertThat(status(pid)).isEqualTo("PENDING");
    assertThat(draftRevision(a)).isEqualTo(rev);
    assertThat(draftProjectData(a)).isEqualTo(projectBefore);
    assertThat(publishedRows(a)).isZero();
    assertThat(jdbc.queryForObject("SELECT CAST(page_settings AS CHAR) FROM academies WHERE academy_id = ?",
        String.class, a.academyId())).isEqualTo(pageSettingsBefore);
    String publicAfter = call(get("/api/academies/" + a.slug())).getResponse().getContentAsString();
    assertThat(publicBefore).isNotBlank().isEqualTo(publicAfter);
  }

  @Test
  @DisplayName("given 저장된 초안 when Agent context then 토큰 학원의 id 있는 요소 목록을 돌려준다")
  void agent_context() throws Exception {
    String token = issueToken(a);
    saveBaseDraft(a);

    JsonNode ctx = json(call(asAgent(get(AGENT + "/context"), token, null)));

    assertThat(ctx.get("components").toString()).contains(ProposalFixtures.TITLE_ID, ProposalFixtures.TITLE_TEXT);
    assertThat(ctx.get("revision").asLong()).isEqualTo(draftRevision(a));
  }

  @Test
  @DisplayName("given 위험한 제안 입력 when 생성 then 400이고 저장되지 않는다")
  void propose_rejectsBadInput() throws Exception {
    String token = issueToken(a);
    saveBaseDraft(a);
    for (Map<String, Object> bad : List.<Map<String, Object>>of(
        titleProposal("<script>alert(1)</script>"),
        Map.of("summary", "x", "changes", List.of()),
        Map.of("summary", " ", "changes", List.of(Map.of("componentId", "a", "type", "text", "after", "b"))),
        Map.of("summary", "x", "changes", List.of(
            Map.of("componentId", "a", "type", "image", "after", "javascript:alert(1)"))),
        Map.of("summary", "x", "changes", List.of(
            Map.of("componentId", "a", "type", "style", "styles", Map.of("background", "url(http://e/x)")))))) {
      assertThat(call(asAgent(post(AGENT + "/proposals"), token, bad)).getResponse().getStatus())
          .as(bad.toString()).isEqualTo(400);
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM academy_visual_editor_proposal WHERE academy_id = ?",
        Integer.class, a.academyId())).isZero();
  }

  @Test
  @DisplayName("given 대기 제안 5건 when 6번째 then 409")
  void propose_pendingLimit() throws Exception {
    String token = issueToken(a);
    for (int i = 0; i < 5; i++) {
      propose(token, "제목 " + i);
    }
    assertThat(call(asAgent(post(AGENT + "/proposals"), token, titleProposal("6번째"))).getResponse().getStatus())
        .isEqualTo(409);
  }

  // ───────── 승인(저장 트랜잭션) ─────────

  @Test
  @DisplayName("given 제안 when 편집기 승인 저장 then 초안·APPLIED가 함께 커밋되고 승인 전까지 공개 페이지는 그대로다")
  void approve_fullFlow() throws Exception {
    String token = issueToken(a);
    long rev = saveBaseDraft(a);
    long pid = propose(token, "승인된 새 제목");

    JsonNode list = json(call(asEdu(get(VE + "/proposals"), a, null)));
    assertThat(list).hasSize(1);
    assertThat(list.get(0).get("status").asText()).isEqualTo("PENDING");
    assertThat(list.get(0).get("applicable").asBoolean()).isTrue();

    MvcResult r = putApplied(a, "승인된 새 제목", rev, pid);

    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    JsonNode body = json(r);
    assertThat(body.get("appliedProposal").get("status").asText()).isEqualTo("APPLIED");
    long dbRev = draftRevision(a);
    assertThat(body.get("revision").asLong()).isEqualTo(dbRev);
    assertThat(body.get("appliedProposal").get("appliedRevision").asLong()).isEqualTo(dbRev);
    assertThat(status(pid)).isEqualTo("APPLIED");
    assertThat(jdbc.queryForObject("SELECT applied_by FROM academy_visual_editor_proposal WHERE proposal_id = ?",
        Long.class, pid)).isEqualTo(a.user().getUserId());
    assertThat(draftProjectData(a)).contains("승인된 새 제목");
    assertThat(publishedRows(a)).as("승인(저장)만으로는 게시되지 않는다").isZero();
    assertThat(json(call(asEdu(get(VE + "/proposals"), a, null)))).as("처리된 제안은 목록에서 빠진다").isEmpty();
  }

  @Test
  @DisplayName("given 이미 적용된 제안 when 다시 적용 then 409 PROPOSAL_NOT_PENDING이고 초안은 그대로다")
  void approve_duplicateIsRejected() throws Exception {
    String token = issueToken(a);
    long rev = saveBaseDraft(a);
    long pid = propose(token, "한 번만");
    long afterFirst = json(putApplied(a, "한 번만", rev, pid)).get("revision").asLong();

    MvcResult second = putApplied(a, "한 번만 (재시도)", afterFirst, pid);

    assertThat(second.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(second).get("code").asText()).isEqualTo("PROPOSAL_NOT_PENDING");
    assertThat(draftRevision(a)).isEqualTo(afterFirst);
    assertThat(draftProjectData(a)).doesNotContain("재시도");
  }

  @Test
  @DisplayName("given 제안과 다른 내용으로 저장 when 승인 then 422 TEXT_MISMATCH, 초안 롤백, 제안 PENDING 유지")
  void approve_mismatchRollsBack() throws Exception {
    String token = issueToken(a);
    long rev = saveBaseDraft(a);
    long pid = propose(token, "제안 텍스트");
    String before = draftProjectData(a);

    MvcResult r = putApplied(a, "사용자가 다시 고친 텍스트", rev, pid);

    assertThat(r.getResponse().getStatus()).isEqualTo(422);
    assertThat(json(r).get("code").asText()).isEqualTo("PROPOSAL_VERIFICATION_FAILED");
    assertThat(json(r).get("details").get(0).asText()).startsWith("TEXT_MISMATCH");
    assertThat(status(pid)).isEqualTo("PENDING");
    assertThat(draftRevision(a)).isEqualTo(rev);
    assertThat(draftProjectData(a)).isEqualTo(before);
  }

  @Test
  @DisplayName("given revision 충돌 when 승인 then 409 REVISION_CONFLICT이고 제안은 PENDING (STALE 아님)")
  void approve_revisionConflictKeepsPending() throws Exception {
    String token = issueToken(a);
    long rev = saveBaseDraft(a);
    long pid = propose(token, "충돌 제안");

    MvcResult r = putApplied(a, "충돌 제안", rev + 5, pid);

    assertThat(r.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(r).get("code").asText()).isEqualTo("REVISION_CONFLICT");
    assertThat(status(pid)).isEqualTo("PENDING");
    assertThat(draftRevision(a)).isEqualTo(rev);
  }

  @Test
  @DisplayName("given 같은 제안·같은 revision의 동시 승인 2건 when 실행 then 정확히 1건만 성공")
  void approve_concurrentExactlyOne() throws Exception {
    String token = issueToken(a);
    long rev = saveBaseDraft(a);
    long pid = propose(token, "동시 승인");
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<Integer>> fs = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      fs.add(pool.submit(() -> {
        go.await();
        return putApplied(a, "동시 승인", rev, pid).getResponse().getStatus();
      }));
    }
    go.countDown();
    List<Integer> codes = new ArrayList<>();
    for (Future<Integer> f : fs) {
      codes.add(f.get(60, TimeUnit.SECONDS));
    }
    pool.shutdown();

    assertThat(codes.stream().filter(c -> c == 200).count()).isEqualTo(1);
    assertThat(codes.stream().filter(c -> c == 409).count()).isEqualTo(1);
    assertThat(status(pid)).isEqualTo("APPLIED");
    assertThat(draftRevision(a)).isEqualTo(rev + 1);
  }

  // ───────── 학원 격리 ─────────

  @Test
  @DisplayName("given 학원 A의 제안 when 학원 B가 조회·거절·승인 시도 then 모두 404이고 A의 제안·B의 초안은 불변")
  void isolation_acrossAcademies() throws Exception {
    String tokenA = issueToken(a);
    String tokenB = issueToken(b);
    saveBaseDraft(a);
    long revB = saveBaseDraft(b);
    long pidA = propose(tokenA, "A의 제안");
    String bBefore = draftProjectData(b);

    assertThat(call(asAgent(get(AGENT + "/proposals/" + pidA), tokenB, null)).getResponse().getStatus())
        .isEqualTo(404);
    assertThat(call(asEdu(post(VE + "/proposals/" + pidA + "/reject"), b, null)).getResponse().getStatus())
        .isEqualTo(404);
    MvcResult apply = putApplied(b, "A의 제안", revB, pidA);
    assertThat(apply.getResponse().getStatus()).isEqualTo(404);
    assertThat(json(apply).get("code").asText()).isEqualTo("PROPOSAL_NOT_FOUND");

    assertThat(status(pidA)).isEqualTo("PENDING");
    assertThat(draftProjectData(b)).isEqualTo(bBefore);
    assertThat(draftRevision(b)).isEqualTo(revB);
    assertThat(json(call(asEdu(get(VE + "/proposals"), b, null)))).isEmpty();
    // 존재하지 않는 id와 구분되지 않는다.
    MvcResult none = putApplied(b, "x", revB, 987654321L);
    assertThat(none.getResponse().getContentAsString())
        .isEqualTo(apply.getResponse().getContentAsString());
  }

  // ───────── 거절 / 만료 / STALE ─────────

  @Test
  @DisplayName("given 제안 when 거절 then REJECTED, 이후 승인·재거절은 409")
  void reject_flow() throws Exception {
    String token = issueToken(a);
    long rev = saveBaseDraft(a);
    long pid = propose(token, "거절될 제안");

    assertThat(call(asEdu(post(VE + "/proposals/" + pid + "/reject"), a, null)).getResponse().getStatus())
        .isEqualTo(200);
    assertThat(status(pid)).isEqualTo("REJECTED");
    assertThat(call(asEdu(post(VE + "/proposals/" + pid + "/reject"), a, null)).getResponse().getStatus())
        .isEqualTo(409);
    MvcResult r = putApplied(a, "거절될 제안", rev, pid);
    assertThat(r.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(r).get("code").asText()).isEqualTo("PROPOSAL_NOT_PENDING");
    assertThat(draftRevision(a)).isEqualTo(rev);
  }

  @Test
  @DisplayName("given 14일 지난 제안 when 승인 then 409이고, 목록 조회 시 EXPIRED로 정리된다")
  void expired() throws Exception {
    String token = issueToken(a);
    long rev = saveBaseDraft(a);
    long pid = propose(token, "오래된 제안");
    jdbc.update("UPDATE academy_visual_editor_proposal SET expires_at = ? WHERE proposal_id = ?",
        java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(1)), pid);

    MvcResult r = putApplied(a, "오래된 제안", rev, pid);
    assertThat(r.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(r).get("code").asText()).isEqualTo("PROPOSAL_NOT_PENDING");

    assertThat(json(call(asEdu(get(VE + "/proposals"), a, null)))).isEmpty();
    assertThat(status(pid)).isEqualTo("EXPIRED");
  }

  @Test
  @DisplayName("given 대상 요소가 초안에서 사라짐 when 목록 then STALE로 표시(대상이 없을 때만)")
  void stale_onlyWhenTargetMissing() throws Exception {
    String token = issueToken(a);
    long rev = saveBaseDraft(a);
    long pidTitle = propose(token, "제목 제안");
    long pidPara = call(asAgent(post(AGENT + "/proposals"), token, Map.of("summary", "문단", "changes",
        List.of(Map.of("componentId", ProposalFixtures.PARA_ID, "type", "text", "after", "문단 제안")))))
        .getResponse().getStatus() == 201 ? lastProposalId(a) : -1;
    // 문단(i5fj) 요소를 지운 초안을 저장한다.
    ObjectNode pd = ProposalFixtures.projectData();
    ObjectNode section = ProposalFixtures.component(pd, "ikua");
    var kids = (com.fasterxml.jackson.databind.node.ArrayNode) section.get("components");
    for (int i = kids.size() - 1; i >= 0; i--) {
      if (kids.get(i).path("attributes").path("id").asText("").equals(ProposalFixtures.PARA_ID)) {
        kids.remove(i);
      }
    }
    assertThat(call(asEdu(put(VE + "/draft"), a, draftBody(pd, rev))).getResponse().getStatus()).isEqualTo(200);

    JsonNode list = json(call(asEdu(get(VE + "/proposals"), a, null)));

    assertThat(status(pidTitle)).isEqualTo("PENDING");
    assertThat(status(pidPara)).isEqualTo("STALE");
    assertThat(list).hasSize(2);
  }

  private long lastProposalId(Edu e) {
    return jdbc.queryForObject(
        "SELECT MAX(proposal_id) FROM academy_visual_editor_proposal WHERE academy_id = ?", Long.class, e.academyId());
  }

  // ───────── AI 수정 요청 ─────────

  @Test
  @DisplayName("given AI 응답 when 수정 요청 then 제안(PENDING)만 만들고 초안·게시본은 불변")
  void aiRequest_createsProposalOnly() throws Exception {
    long rev = saveBaseDraft(a);
    String projectBefore = draftProjectData(a);
    when(aiProvider.complete(anyString(), any())).thenReturn(AiProvider.AiCompletion.ok(
        "{\"summary\":\"제목 변경\",\"changes\":[{\"componentId\":\"" + ProposalFixtures.TITLE_ID
            + "\",\"type\":\"text\",\"after\":\"AI가 쓴 제목\"},{\"componentId\":\"ghost\",\"type\":\"text\","
            + "\"after\":\"무시됨\"}]}"));

    MvcResult r = call(asEdu(post(VE + "/ai-requests"), a, Map.of("instruction", "제목을 바꿔줘")));

    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
    JsonNode v = json(r);
    assertThat(v.get("status").asText()).isEqualTo("PENDING");
    assertThat(v.get("createdByKind").asText()).isEqualTo("AI");
    assertThat(v.get("changes")).hasSize(1);
    assertThat(draftRevision(a)).isEqualTo(rev);
    assertThat(draftProjectData(a)).isEqualTo(projectBefore);
    assertThat(publishedRows(a)).isZero();
  }

  @Test
  @DisplayName("given AI 공급자 실패 when 수정 요청 then 503이고 제안·초안 모두 그대로, 편집기 API는 정상")
  void aiRequest_providerFailureIsHarmless() throws Exception {
    long rev = saveBaseDraft(a);
    when(aiProvider.complete(anyString(), any())).thenReturn(AiProvider.AiCompletion.failed("AI 서버 미구성"));

    MvcResult r = call(asEdu(post(VE + "/ai-requests"), a, Map.of("instruction", "제목을 바꿔줘")));

    assertThat(r.getResponse().getStatus()).isEqualTo(503);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM academy_visual_editor_proposal WHERE academy_id = ?",
        Integer.class, a.academyId())).isZero();
    assertThat(draftRevision(a)).isEqualTo(rev);
    // 기존 저장 기능은 영향이 없다.
    MvcResult save = call(asEdu(put(VE + "/draft"), a, draftBody(ProposalFixtures.projectData(), rev)));
    assertThat(save.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("given 초안 없음 또는 빈 요청 when 수정 요청 then 400")
  void aiRequest_guards() throws Exception {
    assertThat(call(asEdu(post(VE + "/ai-requests"), a, Map.of("instruction", "수정"))).getResponse().getStatus())
        .isEqualTo(400);
    saveBaseDraft(a);
    assertThat(call(asEdu(post(VE + "/ai-requests"), a, Map.of("instruction", " "))).getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  @DisplayName("given AI 공급자 실패 + 규칙으로 해석 가능한 요청 when 수정 요청 then 규칙 기반 제안(PENDING)이 만들어지고 승인 저장까지 된다")
  void aiRequest_ruleFallbackThenApprove() throws Exception {
    long rev = saveBaseDraft(a);
    when(aiProvider.complete(anyString(), any())).thenReturn(AiProvider.AiCompletion.failed("AI 서버 미구성"));

    MvcResult r = call(asEdu(post(VE + "/ai-requests"), a,
        Map.of("instruction", "제목을 '규칙으로 만든 제목'으로 바꿔줘")));

    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
    JsonNode v = json(r);
    assertThat(v.get("createdByKind").asText()).isEqualTo("RULE");
    assertThat(v.get("changes").get(0).get("componentId").asText()).isEqualTo(ProposalFixtures.TITLE_ID);
    assertThat(draftRevision(a)).isEqualTo(rev);
    assertThat(publishedRows(a)).isZero();

    MvcResult applied = putApplied(a, "규칙으로 만든 제목", rev, v.get("proposalId").asLong());
    assertThat(applied.getResponse().getStatus()).isEqualTo(200);
    assertThat(status(v.get("proposalId").asLong())).isEqualTo("APPLIED");
  }

  @Test
  @DisplayName("given 비로그인 when 교육자 제안 API then 401")
  void anonymousRejected() throws Exception {
    assertThat(call(get(VE + "/proposals").accept(MediaType.APPLICATION_JSON)).getResponse().getStatus())
        .isEqualTo(401);
    assertThat(call(post(VE + "/ai-requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .getResponse().getStatus()).isEqualTo(401);
  }
}
