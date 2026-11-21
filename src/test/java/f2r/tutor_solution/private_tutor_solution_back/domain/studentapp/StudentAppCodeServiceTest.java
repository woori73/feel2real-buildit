package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentAppCode;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository.StudentAppCodeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppCodeService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 재원생 앱 접속 코드: 발급·재발급·폐기·로그인 규칙(무DB, 레포는 mock). */
class StudentAppCodeServiceTest {

  private final StudentAppCodeRepository codes = mock(StudentAppCodeRepository.class);
  private final EasyStudentRepository students = mock(EasyStudentRepository.class);
  private final StudentAppCodeService service = new StudentAppCodeService(codes, students);

  private EasyStudent student(long id, EasyStudentStatus status) {
    EasyStudent s = EasyStudent.builder().name("가명 학생A").status(status).build();
    ReflectionTestUtils.setField(s, "studentId", id);
    when(students.findById(id)).thenReturn(Optional.of(s));
    return s;
  }

  private StudentAppCode code(long codeId, long studentId) {
    StudentAppCode c = new StudentAppCode(1L, studentId, "h".repeat(64), "QT", 9L, LocalDateTime.now());
    ReflectionTestUtils.setField(c, "codeId", codeId);
    return c;
  }

  @Test
  @DisplayName("given 이미 쓰는 코드가 있는 재원생 when 발급하면 then 이전 코드는 폐기되고 새 8글자 코드(4-4 표시·#code 주소)가 해시로만 저장된다")
  void issue_revokesPreviousAndStoresHashOnly() {
    // given
    student(1L, EasyStudentStatus.ENROLLED);
    StudentAppCode old = code(5L, 1L);
    when(codes.findByBusinessIdAndStudentIdAndStatus(1L, 1L, StudentAppCode.ACTIVE)).thenReturn(List.of(old));
    when(codes.findByCodeHash(anyString())).thenReturn(Optional.empty());
    when(codes.save(any(StudentAppCode.class))).thenAnswer(i -> i.getArgument(0));
    // when
    StudentAppCodeService.IssuedCode issued = service.issue(1L, 1L, 9L);
    // then
    assertThat(old.isActive()).isFalse();
    assertThat(issued.code()).hasSize(8).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}");
    assertThat(issued.display()).isEqualTo(issued.code().substring(0, 4) + "-" + issued.code().substring(4));
    assertThat(issued.appUrl()).isEqualTo("/naesintest/app#code=" + issued.code());
    assertThat(issued.status().active()).isTrue();
    assertThat(issued.status().tail()).isEqualTo(issued.code().substring(6));
  }

  @Test
  @DisplayName("given 퇴원한 학생 when 코드를 발급하면 then 거절한다")
  void issue_rejectsWithdrawn() {
    // given
    student(2L, EasyStudentStatus.WITHDRAWN);
    // when / then
    assertThatThrownBy(() -> service.issue(1L, 2L, 9L)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("재원 중인 학생");
  }

  @Test
  @DisplayName("given 소문자·하이픈·공백이 섞인 코드 when 정리하면 then 대문자 8글자, 틀린 글자(0·O·1·I)나 길이는 null")
  void normalize_rules() {
    // given / when / then
    assertThat(ReflectionTestUtils.<String>invokeMethod(StudentAppCodeService.class, "normalize", " ab2c-dE3f ")).isEqualTo("AB2CDE3F");
    assertThat(ReflectionTestUtils.<String>invokeMethod(StudentAppCodeService.class, "normalize", "AB0CDE3F")).isNull();
    assertThat(ReflectionTestUtils.<String>invokeMethod(StudentAppCodeService.class, "normalize", "ABCD")).isNull();
    assertThat(ReflectionTestUtils.<String>invokeMethod(StudentAppCodeService.class, "normalize", (Object) null)).isNull();
  }

  @Test
  @DisplayName("given 사용 중 코드 when 로그인하면 then 통과하고 마지막 사용 시각이 남는다 / 폐기된 코드·퇴원 학생·형식 오류는 빈 값")
  void login_acceptsOnlyActiveEnrolled() {
    // given
    student(1L, EasyStudentStatus.ENROLLED);
    student(2L, EasyStudentStatus.WITHDRAWN);
    StudentAppCode active = code(1L, 1L);
    StudentAppCode revoked = code(2L, 1L);
    revoked.revoke(LocalDateTime.now());
    StudentAppCode withdrawn = code(3L, 2L);
    String h1 = ReflectionTestUtils.invokeMethod(StudentAppCodeService.class, "hash", "AAAAAAAA");
    String h2 = ReflectionTestUtils.invokeMethod(StudentAppCodeService.class, "hash", "BBBBBBBB");
    String h3 = ReflectionTestUtils.invokeMethod(StudentAppCodeService.class, "hash", "CCCCCCCC");
    when(codes.findByCodeHash(h1)).thenReturn(Optional.of(active));
    when(codes.findByCodeHash(h2)).thenReturn(Optional.of(revoked));
    when(codes.findByCodeHash(h3)).thenReturn(Optional.of(withdrawn));
    // when / then
    assertThat(service.login("aaaa-aaaa")).contains(active);
    assertThat(active.getLastUsedAt()).isNotNull();
    assertThat(service.login("BBBBBBBB")).isEmpty();
    assertThat(service.login("CCCCCCCC")).isEmpty();
    assertThat(service.login("not-a-code")).isEmpty();
  }

  @Test
  @DisplayName("given 코드 번호와 다른 학생 번호 when 세션을 확인하면 then 빈 값(토큰을 다른 학생 것으로 바꿔도 통과 못 함)")
  void activeSession_requiresSameStudent() {
    // given
    student(1L, EasyStudentStatus.ENROLLED);
    when(codes.findById(1L)).thenReturn(Optional.of(code(1L, 1L)));
    // when / then
    assertThat(service.activeSession(1L, 1L)).isPresent();
    assertThat(service.activeSession(1L, 2L)).isEmpty();
    assertThat(service.activeSession(null, 1L)).isEmpty();
  }

  @Test
  @DisplayName("given 코드 when 폐기하면 then 사용 중 코드가 모두 REVOKED가 된다")
  void revoke_marksRevoked() {
    // given
    StudentAppCode c = code(1L, 1L);
    when(codes.findByBusinessIdAndStudentIdAndStatus(1L, 1L, StudentAppCode.ACTIVE)).thenReturn(List.of(c));
    // when
    StudentAppCodeService.CodeStatus status = service.revoke(1L, 1L);
    // then
    assertThat(c.getStatus()).isEqualTo(StudentAppCode.REVOKED);
    assertThat(c.getRevokedAt()).isNotNull();
    assertThat(status.active()).isFalse();
  }
}
