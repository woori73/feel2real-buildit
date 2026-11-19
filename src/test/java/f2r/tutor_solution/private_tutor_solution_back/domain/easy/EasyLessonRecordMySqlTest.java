package f2r.tutor_solution.private_tutor_solution_back.domain.easy;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.GradingForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.LessonForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.WrongItem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** V1124 학생부 테이블: 수업 기록·채점 기록·사진 저장·학생부 조회·삭제를 실제 MySQL에서 확인(롤백). 학생은 가짜 이름. */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("학생부 MySQL — 수업 기록·채점 기록·답지 사진·오답 집계·삭제")
class EasyLessonRecordMySqlTest {

  @Autowired EasyLessonRecordService service;
  @Autowired EasyStudentRepository students;
  @Autowired EasyBusinessRepository businesses;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entityManager;

  static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};

  @Test
  @DisplayName("given 수업·채점·사진 when 학생부 조회 후 수업 삭제·채점 삭제 then 채점은 연결만 끊기고, 채점 삭제는 사진 행도 지운다")
  void fullFlow() throws Exception {
    // given
    Long bid = businesses.findAllByActiveTrueOrderBySortOrderAsc().get(0).getBusinessId();
    Long sid = students.save(EasyStudent.builder().name("학생부시험학생").grade((byte) 2).status(EasyStudentStatus.ENROLLED).build())
        .getStudentId();

    // when
    var lesson = service.createLesson(bid, sid, new LessonForm(null, "2026-11-20", "능률 3과", "PARTIAL", "p.30", "p.36", null), "MCP");
    var grading = service.createGrading(bid, sid, new GradingForm(lesson.lessonRecordId(), "2026-11-20", "문법", "능률 3과",
        20, 18, List.of(new WrongItem("3", "관계대명사", "what/that"), new WrongItem("7", "관계대명사", "생략")), "복습"), "MCP");
    var photo = service.addPhoto(bid, grading.gradingId(), JPEG, 1L);
    entityManager.flush();
    Map<String, Object> book = service.studentBook(bid, sid);
    byte[] read = service.readPhoto(bid, photo.photoId()).bytes();
    service.deleteLesson(bid, lesson.lessonRecordId());
    entityManager.flush();
    Long linkAfterLessonDelete = jdbc.queryForObject("SELECT lesson_record_id FROM easy_grading_records WHERE grading_id=?",
        Long.class, grading.gradingId());
    service.deleteGrading(bid, grading.gradingId());
    entityManager.flush();

    // then
    assertThat((List<?>) book.get("lessons")).hasSize(1);
    assertThat(book.get("wrongTopics").toString()).contains("관계대명사").contains("count=2");
    assertThat(read).isEqualTo(JPEG);
    assertThat(linkAfterLessonDelete).isNull();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM easy_grading_records WHERE student_id=?", Integer.class, sid)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM easy_grading_photos WHERE photo_id=?", Integer.class, photo.photoId())).isZero();
  }
}
