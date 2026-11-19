package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** V1123 코디네이터 테이블과 출결 초안 → 승인 → 기록을 실제 MySQL에서 확인한다(롤백). 학생은 가짜 이름. */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("AI 코디네이터 MySQL — 출결 초안·승인·중복 방지")
class CoordinatorMySqlTest {

  @Autowired CoordinatorService service;
  @Autowired EasyStudentRepository students;
  @Autowired EasyBusinessRepository businesses;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entityManager;

  static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(1L, "owner", "ADMIN");

  private Long student(String name) {
    return students.save(EasyStudent.builder().name(name).grade((byte) 2).status(EasyStudentStatus.ENROLLED).build())
        .getStudentId();
  }

  private Map<String, Object> mark(Long bid, Long sid, String requestId) {
    return service.requestAttendance(ADMIN, "MCP", "grant", new CoordinatorService.AttendanceRequest(
        requestId, bid, sid, "CHECK_IN", null, null), "https://f2r.test");
  }

  @Test
  @DisplayName("given 가짜 학생 when 검색·초안·승인 then 출결 1행, 같은 날 다시 요청하면 ALREADY_RECORDED")
  void draftApproveRecordThenDuplicateBlocked() {
    // given
    Long bid = businesses.findAllByActiveTrueOrderBySortOrderAsc().get(0).getBusinessId();
    Long sid = student("코디시험민수");
    student("코디시험민수");

    // when
    Map<String, Object> found = service.searchStudents(ADMIN, bid, "코디시험민수");
    Map<String, Object> draft = mark(bid, sid, UUID.randomUUID().toString());
    String jobId = (String) draft.get("jobId");
    Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM easy_attendance_events WHERE student_id=?", Integer.class, sid);
    String hash = (String) service.get(ADMIN, jobId, "", true).get("planHash");
    Map<String, Object> done = service.approve(ADMIN, jobId, hash, "");
    entityManager.flush();

    // then
    assertThat(found.get("duplicateName")).isEqualTo(true);
    assertThat(draft.get("status")).isEqualTo("review_required");
    assertThat(before).isZero();
    assertThat(done.get("status")).isEqualTo("completed");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM easy_attendance_events WHERE student_id=? AND event_type='CHECK_IN'",
        Integer.class, sid)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT status FROM coord_jobs WHERE job_id=?", String.class, jobId)).isEqualTo("completed");
    assertThat(jdbc.queryForList("SELECT to_status FROM coord_job_events WHERE job_id=? ORDER BY event_id", String.class, jobId))
        .containsExactly("review_required", "approved", "completed");
    assertThat(mark(bid, sid, UUID.randomUUID().toString()).get("status")).isEqualTo("ALREADY_RECORDED");
  }

  @Test
  @DisplayName("given 같은 requestId when 두 번 초안 then coord_jobs 1행, 거절하면 기록 없음")
  void sameRequestIdAndReject() {
    // given
    Long bid = businesses.findAllByActiveTrueOrderBySortOrderAsc().get(0).getBusinessId();
    Long sid = student("코디거절학생");
    String req = UUID.randomUUID().toString();

    // when
    Object a = mark(bid, sid, req).get("jobId");
    Object b = mark(bid, sid, req).get("jobId");
    service.reject(ADMIN, (String) a, "");
    entityManager.flush();

    // then
    assertThat(b).isEqualTo(a);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM coord_jobs WHERE user_id=? AND request_id=?", Integer.class, 1L, req)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM easy_attendance_events WHERE student_id=?", Integer.class, sid)).isZero();
    assertThatThrownBy(() -> service.get(new AuthenticatedPrincipal(99L, "x", "ADMIN"), (String) a, "", true))
        .isInstanceOf(AccessDeniedException.class);
  }
}
