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

  @Test
  @DisplayName("given 내신이 아닌 시험(SAT) when 배정하면 then 거절")
  void assign_onlyNaesin() {
    // given
    EasyStudent s = EasyStudent.builder().name("가명").status(EasyStudentStatus.ENROLLED).build();
    ReflectionTestUtils.setField(s, "studentId", 7L);
    when(students.findById(7L)).thenReturn(Optional.of(s));
    TpExam sat = mock(TpExam.class);
    when(sat.getKind()).thenReturn("SAT");
    when(exams.findByExamCodeAndStatus("TP-SAT-1", "PUBLISHED")).thenReturn(Optional.of(sat));
    // when / then
    assertThatThrownBy(() -> service.assign(1L, 7L, "TP-SAT-1", 1L)).isInstanceOf(IllegalArgumentException.class);
    verify(assignments, never()).save(any());
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
