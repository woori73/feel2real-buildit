package f2r.tutor_solution.private_tutor_solution_back.domain.easy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAttendanceEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAttendanceEventRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.ClassForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.CorrectForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.RecordForm;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** V1106 출결부 테이블·JPA 매핑·조회를 실제 MySQL에서 검증한다(롤백 트랜잭션). 이름은 가짜다. */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("F2R 출결부 MySQL — 반·배정·출결·정정·월별·가져오기 중복 키")
class EasyAttendanceMySqlTest {

  @Autowired EasyAttendanceService service;
  @Autowired EasyStudentRepository students;
  @Autowired EasyBusinessRepository businesses;
  @Autowired EasyAttendanceEventRepository events;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entityManager;

  private Long academyId() {
    return businesses.findAllByActiveTrueOrderBySortOrderAsc().get(0).getBusinessId();
  }

  private Long student(String name) {
    return students.save(EasyStudent.builder().name(name).grade((byte) 0).status(EasyStudentStatus.ENROLLED).build())
        .getStudentId();
  }

  @Test
  @DisplayName("given 반·학생 when 배정·등원·정정·월별 then 정정 전 행은 superseded로 남고 월별에는 정정 후 구분만 센다")
  void fullFlow() {
    // given
    Long biz = academyId();
    Long classId = service.createClass(biz, new ClassForm("MySQL 확인반", "가나", null, List.of("MON"), "15:00", "16:00", null, 5)).classId();
    Long sid = student("가짜학생");
    service.assignOn(biz, classId, sid, LocalDate.of(2026, 10, 1));

    // when
    var first = service.record(biz, new RecordForm(classId, sid, "CHECK_IN", "2026-10-05T15:00", "정시"));
    var fixed = service.correct(biz, first.eventId(), new CorrectForm("LATE", "10분 늦음", null));
    Map<String, Object> month = service.monthly(biz, classId, "2026-10");
    entityManager.flush();

    // then
    Integer superseded = jdbc.queryForObject(
        "SELECT superseded FROM easy_attendance_events WHERE event_id=?", Integer.class, first.eventId());
    assertThat(superseded).isEqualTo(1);
    assertThat(fixed.correctsEventId()).isEqualTo(first.eventId());
    @SuppressWarnings("unchecked")
    var row = ((List<Map<String, Object>>) month.get("students")).get(0);
    assertThat(row.get("counts")).isEqualTo(Map.of("LATE", 1));
    assertThat(service.sheet(biz, classId, LocalDate.of(2026, 10, 5)).get(0).events()).hasSize(1);
  }

  @Test
  @DisplayName("given 같은 가져오기 키 when 두 번 저장 then DB 고유 키가 막는다")
  void sourceKeyIsUnique() {
    // given
    var biz = businesses.findById(academyId()).orElseThrow();
    var s = students.findById(student("가짜학생2")).orElseThrow();
    events.saveAndFlush(EasyAttendanceEvent.builder().business(biz).student(s)
        .occurredAt(LocalDate.of(2026, 3, 2).atStartOfDay()).eventType(EasyAttendanceEvent.Type.CHECK_IN)
        .source(EasyAttendanceEvent.Source.ROSTER_IMPORT).sourceKey("ROSTER:test:dup").build());

    // when / then
    assertThatThrownBy(() -> events.saveAndFlush(EasyAttendanceEvent.builder().business(biz).student(s)
        .occurredAt(LocalDate.of(2026, 3, 2).atStartOfDay()).eventType(EasyAttendanceEvent.Type.CHECK_IN)
        .source(EasyAttendanceEvent.Source.ROSTER_IMPORT).sourceKey("ROSTER:test:dup").build()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("given 반 종료 when 다시 조회 then 종료 상태와 배정 해제가 저장된다")
  void endClassPersists() {
    // given
    Long biz = academyId();
    Long classId = service.createClass(biz, new ClassForm("종료 확인반", null, null, null, null, null, null, null)).classId();
    service.assign(biz, classId, student("가짜학생3"));

    // when
    service.endClass(biz, classId);
    entityManager.flush();

    // then
    assertThat(jdbc.queryForObject("SELECT status FROM easy_classes WHERE class_id=?", String.class, classId)).isEqualTo("ENDED");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM easy_class_members WHERE class_id=? AND released_on IS NULL",
        Integer.class, classId)).isZero();
  }
}
