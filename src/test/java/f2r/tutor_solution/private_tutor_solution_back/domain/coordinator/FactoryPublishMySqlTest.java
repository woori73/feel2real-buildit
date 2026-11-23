package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.TestPrepService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** V1139 시험 묶음 → 게시 승인 → tp_exams·tp_questions(PUBLISHED) → 학생 풀이 목록에 나옴 → 내림(ARCHIVED)을 실제 MySQL에서 확인한다(롤백). */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("제작 공장 게시 MySQL — 시험 묶음이 학생 풀이 페이지에 올라가고 내려간다")
class FactoryPublishMySqlTest {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(1L, "owner", "ADMIN");

  @Autowired CoordPcTaskService pcTasks;
  @Autowired CoordFactoryService factory;
  @Autowired FactoryExamPublisher publisher;
  @Autowired TestPrepService testPrep;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entityManager;

  private void pass(String jobId, String reviewer) {
    ObjectNode a = JSON.createObjectNode().put("briefHash", (String) pcTasks.brief(ADMIN, jobId).get("briefHash"))
        .put("reviewer", reviewer).put("verdict", "PASS").put("notes", "정답 직접 풀어 확인");
    ObjectNode c = a.putObject("checks");
    for (String k : CoordPcTaskService.REVIEW_CHECKS) c.put(k, true);
    pcTasks.review(ADMIN, jobId, a);
  }

  @Test
  @DisplayName("given 국어 묶음·검수 3곳·완료 when 게시 승인 then 학생 목록(NAESIN)에 국어 머리 코드로 나오고, 내리면 목록에서 빠진다")
  void koreanPackagePublishesAndWithdraws() {
    // given
    String code = "TP-NAESIN-KOR-ZZT9-2602-R01";
    ObjectNode req = JSON.createObjectNode().put("requestId", "it-" + UUID.randomUUID().toString().substring(0, 12))
        .put("track", "KOREAN").put("type", "EXPECTED").put("school", "시험중").put("grade", "중2").put("year", 2026)
        .put("semester", "2학기").put("exam", "기말").put("count", 1);
    req.putArray("ranges").add("12~45").add("72~83");
    String jobId = (String) factory.create(ADMIN, req).get("jobId");
    pcTasks.claim(ADMIN, jobId, "CLOUD", "MAKER-IT");
    ObjectNode a = JSON.createObjectNode();
    ObjectNode e = a.putObject("exam").put("examCode", code).put("title", "시험중 2학년 국어 2학기 기말 1회")
        .put("subtitle", "문학 12~45쪽, 72~83쪽 · 1문항 · 4점").put("timeLimitSec", 600);
    ObjectNode q = e.putArray("questions").addObject().put("prompt", "1~2행의 표현 효과로 알맞은 것은?")
        .put("bodyText", "첫 집이 불을 켜면 골목에 작은 창 하나 뜬다.").put("answerKey", "B").put("points", 4)
        .put("explanation", "정답 ② · 시의 표현 (4점)\n비슷한 문장 틀에서 수를 바꾸어 리듬을 만든다.");
    q.putArray("choices").add("대조").add("반복과 변화").add("말 건네기").add("청각").add("역순");
    publisher.submit(ADMIN, jobId, a);
    pass(jobId, "REVIEW-1");
    pass(jobId, "REVIEW-2");
    pass(jobId, "REVIEW-3");
    pcTasks.finish(ADMIN, jobId, JSON.createObjectNode().put("summary", "국어 1문항 시범").put("wikiRef", "it"));

    // when
    Map<String, Object> out = factory.approvePublish(ADMIN, jobId);
    entityManager.flush();

    // then
    assertThat(out).containsEntry("state", "PUBLISHED");
    assertThat(testPrep.listExams(TestPrepService.KIND_NAESIN)).anyMatch(x -> code.equals(x.examCode()));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tp_questions q JOIN tp_exams x ON x.exam_id = q.exam_id"
        + " WHERE x.exam_code = ? AND q.section = 'KO' AND q.task_type = 'NAESIN_MC5' AND q.answer_key = 'B' AND q.points = 4",
        Integer.class, code)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT exam_id IS NOT NULL FROM coord_factory_exam_packages WHERE job_id = ?",
        Boolean.class, jobId)).isTrue();

    // when — 내림
    factory.withdrawPublish(ADMIN, jobId, "시험 삼아 내림");
    entityManager.flush();

    // then
    assertThat(jdbc.queryForObject("SELECT status FROM tp_exams WHERE exam_code = ?", String.class, code)).isEqualTo("ARCHIVED");
    assertThat(testPrep.listExams(TestPrepService.KIND_NAESIN)).noneMatch(x -> code.equals(x.examCode()));
  }
}
