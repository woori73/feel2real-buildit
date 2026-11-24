package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentExamResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentExamViewer;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository.StudentExamAssignmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository.StudentExamResultRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository.StudentExamViewerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppCodeService;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentExamAdminService;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpQuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/** 재원생 앱 관리: 볼 사람 지정·배정·점검 규칙(무DB). */
class StudentExamAdminServiceTest {

  private final StudentExamAssignmentRepository assignments = mock(StudentExamAssignmentRepository.class);
  private final StudentExamResultRepository results = mock(StudentExamResultRepository.class);
  private final StudentExamViewerRepository viewers = mock(StudentExamViewerRepository.class);
  private final EasyStudentRepository students = mock(EasyStudentRepository.class);
  private final TpExamRepository exams = mock(TpExamRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final StudentExamAdminService service = new StudentExamAdminService(assignments, results, viewers,
      mock(StudentAppCodeService.class), students, exams, mock(TpQuestionRepository.class), users, new ObjectMapper());

  private static AuthenticatedPrincipal who(long id, String role) {
    return new AuthenticatedPrincipal(id, "u" + id, role, null);
  }

  @Test
  @DisplayName("given 원장·지정 강사·지정 안 된 강사·해제된 강사 when 응시 기록 권한을 보면 then 원장과 지정 강사만 통과")
  void canView_onlyAdminAndDesignated() {
    // given
    when(viewers.findByBusinessIdAndUserId(1L, 20L)).thenReturn(Optional.of(new StudentExamViewer(1L, 20L, 1L, LocalDateTime.now())));
    StudentExamViewer revoked = new StudentExamViewer(1L, 22L, 1L, LocalDateTime.now());
    revoked.revoke(LocalDateTime.now());
    when(viewers.findByBusinessIdAndUserId(1L, 22L)).thenReturn(Optional.of(revoked));
    when(viewers.findByBusinessIdAndUserId(1L, 21L)).thenReturn(Optional.empty());
    // when / then
    assertThat(service.canView(who(1, "ADMIN"), 1L)).isTrue();
    assertThat(service.canView(who(20, "TUTOR"), 1L)).isTrue();
    assertThat(service.canView(who(21, "TUTOR"), 1L)).isFalse();
    assertThat(service.canView(who(22, "TUTOR"), 1L)).isFalse();
    assertThat(service.canView(null, 1L)).isFalse();
    assertThatThrownBy(() -> service.requireViewer(who(21, "TUTOR"), 1L)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given 학생 계정 아이디 when 볼 사람으로 지정하면 then 거절(강사·관리자만)")
  void addViewer_rejectsStudentAccount() {
    // given
    User u = mock(User.class);
    when(u.getRole()).thenReturn(Role.STUDENT);
    when(users.findByLoginId("kid")).thenReturn(Optional.of(u));
    // when / then
    assertThatThrownBy(() -> service.addViewer(1L, "kid", 1L)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("강사");
    verify(viewers, never()).save(any());
  }

  private EasyStudent enrolled(long id) {
    EasyStudent s = EasyStudent.builder().name("가명").status(EasyStudentStatus.ENROLLED).build();
    ReflectionTestUtils.setField(s, "studentId", id);
    when(students.findById(id)).thenReturn(Optional.of(s));
    return s;
  }

  private static TpExam exam(long id, String code, String kind, String title) {
    TpExam e = mock(TpExam.class);
    when(e.getExamId()).thenReturn(id);
    when(e.getExamCode()).thenReturn(code);
    when(e.getKind()).thenReturn(kind);
    when(e.getTitle()).thenReturn(title);
    return e;
  }

  @Test
  @DisplayName("given 공개된 TOEIC 회차 when 재원생에게 배정하면 then 배정이 저장된다(내신만 되던 제한을 넓힘)")
  void assign_acceptsToeic() {
    // given
    enrolled(7L);
    TpExam toeic = exam(30L, "TP-TOEIC-2", "TOEIC", "TOEIC 2회");
    when(exams.findByExamCodeAndStatus("TP-TOEIC-2", "PUBLISHED")).thenReturn(Optional.of(toeic));
    when(assignments.findByBusinessIdAndStudentIdAndExamCode(1L, 7L, "TP-TOEIC-2")).thenReturn(Optional.empty());
    // when
    service.assign(1L, 7L, "TP-TOEIC-2", 1L);
    // then
    verify(assignments).save(any());
  }

  @Test
  @DisplayName("given 배정 목록에 없는 종류의 시험 when 배정하면 then 거절")
  void assign_rejectsUnknownKind() {
    // given
    enrolled(7L);
    TpExam other = exam(31L, "TP-X-1", "SPEAKING_LAB", "연습");
    when(exams.findByExamCodeAndStatus("TP-X-1", "PUBLISHED")).thenReturn(Optional.of(other));
    // when / then
    assertThatThrownBy(() -> service.assign(1L, 7L, "TP-X-1", 1L)).isInstanceOf(IllegalArgumentException.class);
    verify(assignments, never()).save(any());
  }

  @Test
  @DisplayName("given 내신·TOEIC·iBT·어휘·SAT 공개 회차 when 배정 목록을 보면 then 내신→TOEIC→iBT→어휘→SAT 순으로 종류 이름과 함께 나온다")
  void exams_listsAllKindsInOrderWithLabels() {
    // given
    TpExam sat = exam(5L, "S1", "SAT", "SAT 1");
    TpExam toeic = exam(4L, "T1", "TOEIC", "TOEIC 1");
    TpExam naesin = exam(3L, "N1", "NAESIN", "내신 1");
    TpExam ibt = exam(1L, "P1", "PRACTICE", "iBT 1");
    TpExam vocab = exam(2L, "V1", "VOCAB", "어휘 1");
    when(exams.findByKindAndStatusOrderByExamIdAsc("SAT", "PUBLISHED")).thenReturn(java.util.List.of(sat));
    when(exams.findByKindAndStatusOrderByExamIdAsc("TOEIC", "PUBLISHED")).thenReturn(java.util.List.of(toeic));
    when(exams.findByKindAndStatusOrderByExamIdAsc("NAESIN", "PUBLISHED")).thenReturn(java.util.List.of(naesin));
    when(exams.findByKindAndStatusOrderByExamIdAsc("PRACTICE", "PUBLISHED")).thenReturn(java.util.List.of(ibt));
    when(exams.findByKindAndStatusOrderByExamIdAsc("VOCAB", "PUBLISHED")).thenReturn(java.util.List.of(vocab));
    // when
    var list = service.exams();
    // then
    assertThat(list).extracting(StudentExamAdminService.ExamOption::examCode).containsExactly("N1", "T1", "P1", "V1", "S1");
    assertThat(list).extracting(StudentExamAdminService.ExamOption::kindLabel).containsExactly("내신", "TOEIC", "iBT", "어휘", "SAT");
  }

  @Test
  @DisplayName("given 다른 사업체의 응시 기록 번호 when 점검하면 then 찾을 수 없음(사업체를 넘어 점검 못 함)")
  void check_scopedToBusiness() {
    // given
    StudentExamResult r = StudentExamResult.builder().businessId(2L).studentId(7L).attemptId(1L).examCode("X").examTitle("t")
        .attemptNo(1).startedAt(LocalDateTime.now()).submittedAt(LocalDateTime.now()).durationSec(1).totalCount(1).correctCount(1)
        .createdAt(LocalDateTime.now()).build();
    when(results.findById(5L)).thenReturn(Optional.of(r));
    // when / then
    assertThatThrownBy(() -> service.check(1L, 5L, 1L)).isInstanceOf(IllegalArgumentException.class);
    assertThat(r.getCheckedAt()).isNull();
  }
}
