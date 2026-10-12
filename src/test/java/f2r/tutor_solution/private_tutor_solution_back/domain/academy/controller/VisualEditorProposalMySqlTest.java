package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyRegionRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorProposalPort;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 저장 + Proposal APPLIED의 <b>원자성</b>을 실제 MySQL로 검증한다.
 *
 * <p>Proposal 도메인(뮤즈) 구현체 대신, 같은 계약을 따르는 테스트 전용 포트를 쓴다.
 * 이 포트는 별도 스크래치 테이블(ve_fake_proposal)을 JDBC로 갱신하므로, 저장 트랜잭션에
 * 참여하지 않으면(= 원자적이지 않으면) 롤백 후 상태가 어긋나 테스트가 실패한다.
 * 클래스에 @Transactional이 없다: 롤백이 실제로 일어나는지 봐야 하기 때문이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("시각 편집기 저장+Proposal 원자성 (격리 MySQL)")
class VisualEditorProposalMySqlTest {

  private static final String URL = "/api/tutor/academy/page-settings/visual-editor/draft";
  private static final String MARK = "VE_PROPOSAL_MARK";
  /** 정제를 통과하는 최소 projectData (제안 검증용 표식 포함). */
  private static final String PD_WITH_MARK =
      "{\"pages\":[{\"type\":\"main\",\"frames\":[{\"component\":{\"type\":\"wrapper\","
          + "\"components\":[{\"tagName\":\"h1\",\"type\":\"text\",\"attributes\":{\"id\":\"ve5\"},"
          + "\"components\":[{\"type\":\"textnode\",\"content\":\"" + MARK + "\"}]}]}}]}]}";

  /** 계약을 따르는 테스트 전용 포트: 행 잠금·학원 조건·상태·검증·DB revision 기록. */
  @TestConfiguration
  static class FakePortConfig {
    @Bean
    @org.springframework.context.annotation.Primary
    VisualEditorProposalPort fakeProposalPort(JdbcTemplate jdbc) {
      return (academyId, userId, proposalId, savedRevision, projectData) -> {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
          throw new IllegalStateException("저장 트랜잭션 밖에서 호출됨");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT status, expect FROM ve_fake_proposal WHERE id = ? AND academy_id = ? FOR UPDATE",
            proposalId, academyId);
        if (rows.isEmpty()) {
          throw VisualEditorProposalPort.ProposalException.notFound();
        }
        if (!"PENDING".equals(rows.get(0).get("status"))) {
          throw VisualEditorProposalPort.ProposalException.notPending();
        }
        String expect = (String) rows.get(0).get("expect");
        if (projectData == null || !projectData.contains(expect)) {
          throw VisualEditorProposalPort.ProposalException.verificationFailed(
              List.of("TEXT_MISMATCH:ve5"));
        }
        jdbc.update("UPDATE ve_fake_proposal SET status='APPLIED', applied_revision=? WHERE id=?",
            savedRevision, proposalId);
        return new VisualEditorProposalPort.ProposalApplyResult(proposalId, "APPLIED", savedRevision);
      };
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider tokenProvider;
  @Autowired private AcademyService academyService;
  @Autowired private UserRepository userRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private JdbcTemplate jdbc;

  private User tutor;
  private Cookie cookie;
  private long academyId;
  private final List<Long> otherAcademyIds = new ArrayList<>();

  @BeforeEach
  void setUp() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS ve_fake_proposal ("
        + "id BIGINT AUTO_INCREMENT PRIMARY KEY, academy_id BIGINT NOT NULL, "
        + "status VARCHAR(20) NOT NULL, expect VARCHAR(100) NOT NULL, applied_revision BIGINT NULL"
        + ") ENGINE=InnoDB");
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    tutor = userRepository.save(new User(
        "vp_test_" + suffix, "pw", "테스트강사", "별명", null, null, Role.TUTOR));
    JwtTokenProvider.AuthenticatedPrincipal principal = new JwtTokenProvider.AuthenticatedPrincipal(
        tutor.getUserId(), tutor.getLoginId(), "TUTOR");
    cookie = new Cookie("ACCESS_TOKEN",
        tokenProvider.issue(tutor.getUserId(), tutor.getLoginId(), "TUTOR"));
    Subject subject = subjectRepository.findByNameKo("영어")
        .orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    academyService.createDraft(principal,
        new AcademyUpsertRequest("제안테스트학원", null, "소개문",
            Set.of(subject.getSubjectId()), Set.of(TargetGroup.MIDDLE),
            List.of(new AcademyRegionRequest("서울특별시", "강남구")),
            new AcademyPageSettings(null, null, null, null, null, null,
                Map.of("designPreset", "ENGLISH"), null)),
        "vp-key-" + suffix);
    academyId = jdbc.queryForObject(
        "SELECT academy_id FROM academies WHERE tutor_user_id = ?", Long.class, tutor.getUserId());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    jdbc.update("DELETE FROM ve_fake_proposal WHERE academy_id = ?", academyId);
    for (Long id : otherAcademyIds) {
      jdbc.update("DELETE FROM ve_fake_proposal WHERE academy_id = ?", id);
    }
    jdbc.update("DELETE FROM academies WHERE tutor_user_id = ?", tutor.getUserId());
    jdbc.update("DELETE FROM users WHERE user_id = ?", tutor.getUserId());
  }

  private long proposal(long forAcademy, String status, String expect) {
    jdbc.update("INSERT INTO ve_fake_proposal (academy_id, status, expect) VALUES (?,?,?)",
        forAcademy, status, expect);
    return jdbc.queryForObject("SELECT MAX(id) FROM ve_fake_proposal", Long.class);
  }

  private MvcResult putDraft(String html, String projectData, Long revision, Long proposalId)
      throws Exception {
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("html", html);
    body.put("css", "");
    body.put("projectData", projectData);
    if (revision != null) {
      body.put("revision", revision);
    }
    if (proposalId != null) {
      body.put("appliedProposalId", proposalId);
    }
    return mockMvc.perform(put(URL).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body))).andReturn();
  }

  private JsonNode json(MvcResult r) throws Exception {
    return objectMapper.readTree(r.getResponse().getContentAsString());
  }

  /** 기준 초안(revision 0)을 만들고 그 revision을 돌려준다. */
  private long baseDraft() throws Exception {
    MvcResult r = putDraft("<p>base</p>", "{}", null, null);
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json(r).get("revision").asLong();
  }

  private String draftHtml() {
    return jdbc.queryForObject(
        "SELECT html FROM academy_visual_editor_draft WHERE academy_id = ?", String.class, academyId);
  }

  private long draftRevision() {
    return jdbc.queryForObject(
        "SELECT revision FROM academy_visual_editor_draft WHERE academy_id = ?", Long.class, academyId);
  }

  private String status(long id) {
    return jdbc.queryForObject("SELECT status FROM ve_fake_proposal WHERE id = ?", String.class, id);
  }

  @Test
  @DisplayName("given 유효한 제안 when 적용 저장 then 초안 갱신과 APPLIED가 함께 커밋되고 revision이 DB 값과 같다")
  void apply_commitsDraftAndProposalTogether() throws Exception {
    long rev = baseDraft();
    long pid = proposal(academyId, "PENDING", MARK);

    MvcResult r = putDraft("<h1>제안 적용</h1>", PD_WITH_MARK, rev, pid);

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = json(r);
    assertThat(body.get("appliedProposal").get("status").asText()).isEqualTo("APPLIED");
    assertThat(status(pid)).isEqualTo("APPLIED");
    long dbRev = draftRevision();
    assertThat(body.get("revision").asLong()).isEqualTo(dbRev);
    assertThat(body.get("appliedProposal").get("appliedRevision").asLong()).isEqualTo(dbRev);
    assertThat(jdbc.queryForObject("SELECT applied_revision FROM ve_fake_proposal WHERE id=?",
        Long.class, pid)).isEqualTo(dbRev);
  }

  @Test
  @DisplayName("given 제안 없이 저장 when 저장 then appliedProposal 키가 없고 기존처럼 저장된다")
  void withoutProposal_behavesAsBefore() throws Exception {
    long rev = baseDraft();

    MvcResult r = putDraft("<p>plain</p>", "{}", rev, null);

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(r).has("appliedProposal")).isFalse();
    assertThat(draftHtml()).contains("plain");
  }

  @Test
  @DisplayName("given revision 불일치 when 적용 저장 then 409 REVISION_CONFLICT, 초안 불변, 제안 PENDING 유지")
  void revisionConflict_rollsBackAndKeepsPending() throws Exception {
    long rev = baseDraft();
    long pid = proposal(academyId, "PENDING", MARK);

    MvcResult r = putDraft("<h1>x</h1>", PD_WITH_MARK, rev + 7, pid);

    assertThat(r.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(r).get("code").asText()).isEqualTo("REVISION_CONFLICT");
    assertThat(status(pid)).isEqualTo("PENDING");
    assertThat(draftHtml()).contains("base");
    assertThat(draftRevision()).isEqualTo(rev);
  }

  @Test
  @DisplayName("given 검증 실패 when 적용 저장 then 422이고 초안 갱신까지 롤백, 제안 PENDING")
  void verificationFailure_rollsBackDraftUpdate() throws Exception {
    long rev = baseDraft();
    long pid = proposal(academyId, "PENDING", MARK);

    MvcResult r = putDraft("<h1>변경</h1>", "{}", rev, pid);

    assertThat(r.getResponse().getStatus()).isEqualTo(422);
    JsonNode body = json(r);
    assertThat(body.get("code").asText()).isEqualTo("PROPOSAL_VERIFICATION_FAILED");
    assertThat(body.get("details").get(0).asText()).startsWith("TEXT_MISMATCH");
    assertThat(status(pid)).isEqualTo("PENDING");
    assertThat(draftHtml()).contains("base").doesNotContain("변경");
    assertThat(draftRevision()).isEqualTo(rev);
  }

  @Test
  @DisplayName("given 다른 학원의 제안 id when 적용 저장 then 404이고 상대 제안·내 초안 모두 불변")
  void otherAcademyProposal_is404AndUntouched() throws Exception {
    long rev = baseDraft();
    long foreignAcademy = academyId + 1_000_000L;
    otherAcademyIds.add(foreignAcademy);
    long foreign = proposal(foreignAcademy, "PENDING", MARK);

    MvcResult r = putDraft("<h1>x</h1>", PD_WITH_MARK, rev, foreign);

    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    assertThat(json(r).get("code").asText()).isEqualTo("PROPOSAL_NOT_FOUND");
    assertThat(status(foreign)).isEqualTo("PENDING");
    assertThat(draftHtml()).contains("base");
  }

  @Test
  @DisplayName("given 존재하지 않는 제안 id when 적용 저장 then 타 학원 제안과 같은 404")
  void unknownProposal_isSame404() throws Exception {
    long rev = baseDraft();

    MvcResult r = putDraft("<h1>x</h1>", PD_WITH_MARK, rev, 987_654_321L);

    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    assertThat(json(r).get("code").asText()).isEqualTo("PROPOSAL_NOT_FOUND");
  }

  @Test
  @DisplayName("given 이미 처리된 제안 when 적용 저장 then 409 PROPOSAL_NOT_PENDING, 초안 불변")
  void nonPending_is409() throws Exception {
    long rev = baseDraft();
    long pid = proposal(academyId, "REJECTED", MARK);

    MvcResult r = putDraft("<h1>x</h1>", PD_WITH_MARK, rev, pid);

    assertThat(r.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(r).get("code").asText()).isEqualTo("PROPOSAL_NOT_PENDING");
    assertThat(status(pid)).isEqualTo("REJECTED");
    assertThat(draftHtml()).contains("base");
  }

  @Test
  @DisplayName("given 같은 제안·같은 revision의 동시 저장 2건 when 실행 then 정확히 1건만 성공하고 APPLIED는 1번")
  void concurrentApply_exactlyOneWins() throws Exception {
    long rev = baseDraft();
    long pid = proposal(academyId, "PENDING", MARK);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      final int n = i;
      results.add(pool.submit(() -> {
        start.await();
        return putDraft("<h1>동시" + n + "</h1>", PD_WITH_MARK, rev, pid).getResponse().getStatus();
      }));
    }
    start.countDown();
    List<Integer> codes = new ArrayList<>();
    for (Future<Integer> f : results) {
      codes.add(f.get(60, TimeUnit.SECONDS));
    }
    pool.shutdown();
    pool.awaitTermination(10, TimeUnit.SECONDS);

    assertThat(codes.stream().filter(c -> c == 200).count()).isEqualTo(1);
    assertThat(codes.stream().filter(c -> c == 409).count()).isEqualTo(1);
    assertThat(status(pid)).isEqualTo("APPLIED");
    assertThat(draftRevision()).isEqualTo(rev + 1);
  }
}
