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
  @Test
  @DisplayName("given 초안 when 대화창 확인 후 취소 then 출결 행은 남되 superseded, 같은 날 다시 초안을 만들 수 있다")
  void chatConfirmThenUndo() {
    // given
    Long bid = businesses.findAllByActiveTrueOrderBySortOrderAsc().get(0).getBusinessId();
    Long sid = student("코디대화학생");
    Map<String, Object> draft = mark(bid, sid, UUID.randomUUID().toString());
    String jobId = (String) draft.get("jobId");

    // when
    Map<String, Object> done = service.confirmFromChat(ADMIN, jobId, (String) draft.get("planHash"), "");
    entityManager.flush();
    Map<String, Object> undone = service.undoFromChat(ADMIN, jobId, "다른 학생", "");
    entityManager.flush();

    // then
    assertThat(done.get("status")).isEqualTo("completed");
    assertThat(undone.get("status")).isEqualTo("cancelled");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM easy_attendance_events WHERE student_id=?", Integer.class, sid)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT superseded FROM easy_attendance_events WHERE student_id=?", Boolean.class, sid)).isTrue();
    assertThat(jdbc.queryForList("SELECT to_status FROM coord_job_events WHERE job_id=? ORDER BY event_id", String.class, jobId))
        .containsExactly("review_required", "approved", "completed", "cancelled");
    assertThat(mark(bid, sid, UUID.randomUUID().toString()).get("status")).isEqualTo("review_required");
  }
  private Map<String, Object> change(String area, Long bid, String action, tools.jackson.databind.JsonNode args) {
    return service.requestChange(ADMIN, "MCP", "grant", area, UUID.randomUUID().toString(), bid, action, args, "");
  }

  private Map<String, Object> confirm(Map<String, Object> draft) {
    Map<String, Object> done = service.confirmFromChat(ADMIN, (String) draft.get("jobId"), (String) draft.get("planHash"), "");
    entityManager.flush();
    return done;
  }

  @Test
  @DisplayName("given 말 명령 when 반 만들기·배정·이동 확인 후 이동 되돌리기 then 반은 남고 학생은 원래 반으로 돌아온다")
  void classCreateAssignMoveUndo() {
    // given
    var json = tools.jackson.databind.json.JsonMapper.builder().build();
    Long bid = businesses.findAllByActiveTrueOrderBySortOrderAsc().get(0).getBusinessId();
    Long sid = student("코디반학생");

    // when
    Map<String, Object> a = confirm(change("CLASS", bid, "CREATE", json.createObjectNode().put("name", "코디시험A반")
        .set("weekdays", json.createArrayNode().add("TUE").add("THU"))));
    Map<String, Object> b = confirm(change("CLASS", bid, "CREATE", json.createObjectNode().put("name", "코디시험B반")));
    Long classA = jdbc.queryForObject("SELECT class_id FROM easy_classes WHERE name='코디시험A반'", Long.class);
    Long classB = jdbc.queryForObject("SELECT class_id FROM easy_classes WHERE name='코디시험B반'", Long.class);
    Map<String, Object> assign = confirm(change("CLASS", bid, "ASSIGN", json.createObjectNode().put("classId", classA).put("studentId", sid)));
    Map<String, Object> move = confirm(change("CLASS", bid, "MOVE", json.createObjectNode().put("classId", classA)
        .put("targetClassId", classB).put("studentId", sid)));
    Integer inBBeforeUndo = jdbc.queryForObject("SELECT COUNT(*) FROM easy_class_members WHERE class_id=? AND student_id=? AND released_on IS NULL",
        Integer.class, classB, sid);
    service.undoFromChat(ADMIN, (String) move.get("jobId"), null, "");
    entityManager.flush();

    // then
    assertThat(a.get("status")).isEqualTo("completed");
    assertThat(b.get("status")).isEqualTo("completed");
    assertThat(assign.get("status")).isEqualTo("completed");
    assertThat(move.get("status")).isEqualTo("completed");
    assertThat(((Map<?, ?>) assign.get("summary")).get("title").toString()).contains("코○○○생").doesNotContain("코디반학생");
    assertThat(inBBeforeUndo).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM easy_class_members WHERE class_id=? AND student_id=? AND released_on IS NULL",
        Integer.class, classA, sid)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM easy_class_members WHERE class_id=? AND student_id=? AND released_on IS NULL",
        Integer.class, classB, sid)).isZero();
    assertThat(jdbc.queryForObject("SELECT weekdays FROM easy_classes WHERE class_id=?", String.class, classA)).contains("TUE");
  }

  @Test
  @DisplayName("given 말 명령 when 학생 등록·수정 확인 후 각각 되돌리기 then 등록은 퇴원 처리, 수정은 이전 값으로 돌아온다")
  void studentCreateUpdateUndo() {
    // given
    var json = tools.jackson.databind.json.JsonMapper.builder().build();
    Long bid = businesses.findAllByActiveTrueOrderBySortOrderAsc().get(0).getBusinessId();
    Long sid = students.save(EasyStudent.builder().name("코디수정학생").school("가짜중").grade((byte) 2)
        .parentPhone("010-0000-0000").status(EasyStudentStatus.ENROLLED).build()).getStudentId();

    // when
    Map<String, Object> created = confirm(change("STUDENT", bid, "CREATE", json.createObjectNode().put("name", "코디새학생").put("grade", 1)));
    Map<String, Object> updated = confirm(change("STUDENT", bid, "UPDATE", json.createObjectNode().put("studentId", sid).put("grade", 3)
        .put("status", "WITHDRAWN")));
    String statusAfterUpdate = jdbc.queryForObject("SELECT status FROM easy_students WHERE student_id=?", String.class, sid);
    service.undoFromChat(ADMIN, (String) created.get("jobId"), null, "");
    service.undoFromChat(ADMIN, (String) updated.get("jobId"), null, "");
    entityManager.flush();

    // then
    assertThat(statusAfterUpdate).isEqualTo("WITHDRAWN");
    assertThat(jdbc.queryForObject("SELECT status FROM easy_students WHERE name='코디새학생'", String.class)).isEqualTo("WITHDRAWN");
    assertThat(jdbc.queryForObject("SELECT memo FROM easy_students WHERE name='코디새학생'", String.class)).contains("AI 등록 취소");
    assertThat(jdbc.queryForMap("SELECT status, grade, parent_phone FROM easy_students WHERE student_id=?", sid))
        .containsEntry("status", "ENROLLED").containsEntry("parent_phone", "010-0000-0000");
    assertThat(((Number) jdbc.queryForObject("SELECT grade FROM easy_students WHERE student_id=?", Integer.class, sid)).intValue()).isEqualTo(2);
  }
  @Test
  @DisplayName("given 말 명령 when 수강료 청구·납부 확인 후 납부 되돌리기 then 납부액이 이전으로, 청구 되돌리기는 그 청구 삭제")
  void tuitionBillPayUndo() {
    // given
    var json = tools.jackson.databind.json.JsonMapper.builder().build();
    Long bid = businesses.findAllByActiveTrueOrderBySortOrderAsc().get(0).getBusinessId();
    Long sid = student("코디수납학생");

    // when
    Map<String, Object> bill = confirm(change("TUITION", bid, "BILL", json.createObjectNode().put("studentId", sid)
        .put("month", "2031-01").put("billedAmount", 300000)));
    Map<String, Object> pay = confirm(change("TUITION", bid, "PAY", json.createObjectNode().put("studentId", sid)
        .put("month", "2031-01").put("amount", 300000).put("payMethod", "계좌")));
    Long paidAfterPay = jdbc.queryForObject("SELECT paid_amount FROM easy_tuition_payments WHERE student_id=?", Long.class, sid);
    Map<String, Object> status = service.tuitionStatus(ADMIN, bid, "2031-01", null);
    service.undoFromChat(ADMIN, (String) pay.get("jobId"), null, "");
    entityManager.flush();
    Long paidAfterUndo = jdbc.queryForObject("SELECT paid_amount FROM easy_tuition_payments WHERE student_id=?", Long.class, sid);
    service.undoFromChat(ADMIN, (String) bill.get("jobId"), null, "");
    entityManager.flush();

    // then
    assertThat(bill.get("status")).isEqualTo("completed");
    assertThat(pay.get("status")).isEqualTo("completed");
    assertThat(paidAfterPay).isEqualTo(300000L);
    assertThat(status.toString()).contains("코○○○○생").doesNotContain("코디수납학생");
    assertThat(paidAfterUndo).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM easy_tuition_payments WHERE student_id=?", Integer.class, sid)).isZero();
  }
  @Test
  @DisplayName("given 말 명령 when 수업 기록·채점 기록 확인 then 학생부에 남고 채점은 사진 올리기 링크, 되돌리면 삭제")
  void lessonAndGradingByVoice() {
    // given
    var json = tools.jackson.databind.json.JsonMapper.builder().build();
    Long bid = businesses.findAllByActiveTrueOrderBySortOrderAsc().get(0).getBusinessId();
    Long sid = student("코디학생부학생");
    var gradingArgs = json.createObjectNode().put("studentId", sid).put("gradedOn", "2026-11-20").put("totalCount", 10);
    gradingArgs.putArray("wrongItems").add(json.createObjectNode().put("no", "2").put("topic", "시제"));

    // when
    Map<String, Object> lesson = confirm(change("RECORD", bid, "LESSON", json.createObjectNode().put("studentId", sid)
        .put("lessonDate", "2026-11-20").put("lessonRange", "능률 3과")));
    Map<String, Object> grading = confirm(change("RECORD", bid, "GRADING", gradingArgs));
    Map<String, Object> book = service.studentBook(ADMIN, bid, sid);
    service.undoFromChat(ADMIN, (String) grading.get("jobId"), null, "");
    entityManager.flush();

    // then
    assertThat(lesson.get("status")).isEqualTo("completed");
    assertThat(grading.get("status")).isEqualTo("completed");
    assertThat(((Map<?, ?>) grading.get("result")).get("linkUrl").toString()).contains("/easy/records?businessId=" + bid);
    assertThat(book.toString()).contains("코○○○○○생", "시제").doesNotContain("코디학생부학생");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM easy_lesson_records WHERE student_id=?", Integer.class, sid)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM easy_grading_records WHERE student_id=?", Integer.class, sid)).isZero();
  }
}
