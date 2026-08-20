package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.CreateStudentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.CreateTutorRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.UserResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request.UpdateMyProfileRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request.WithdrawRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.MyProfileResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.StudentSummaryResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class UserServiceTest {

  private UserRepository userRepository;
  private StudentRepository studentRepository;
  private PasswordEncoder passwordEncoder;
  private EntityManager entityManager;
  private UserService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    studentRepository = mock(StudentRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    entityManager = mock(EntityManager.class);
    service = new UserService(userRepository, studentRepository, passwordEncoder);
    ReflectionTestUtils.setField(service, "entityManager", entityManager);
  }

  @Test
  @DisplayName("createTutor — TUTOR role 사용자만 생성하고 학생 row는 만들지 않는다")
  void createTutor_creates_tutor_user_without_student_profile() {
    // given
    CreateTutorRequest request = new CreateTutorRequest(
        "tutor1",
        "plainPw",
        "김강사",
        "김강사",
        "tutor@example.com",
        "010-1111-2222");
    when(userRepository.existsByLoginId("tutor1")).thenReturn(false);
    when(passwordEncoder.encode("plainPw")).thenReturn("encodedPw");

    // when
    UserResponse response = service.createTutor(request);

    // then
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.getLoginId()).isEqualTo("tutor1");
    assertThat(saved.getPassword()).isEqualTo("encodedPw");
    assertThat(saved.getRole()).isEqualTo(Role.TUTOR);
    assertThat(saved.getNickname()).isEqualTo("김강사");
    assertThat(response.nickname()).isEqualTo("김강사");
    assertThat(response.displayName()).isEqualTo("김강사");
    assertThat(response.role()).isEqualTo(Role.TUTOR.name());
  }

  @Test
  @DisplayName("createTutor — loginId 중복이면 저장하지 않고 예외")
  void createTutor_throws_when_login_id_already_exists() {
    // given
    CreateTutorRequest request = new CreateTutorRequest(
        "tutor1",
        "plainPw",
        "김강사",
        "김강사",
        "tutor@example.com",
        "010-1111-2222");
    when(userRepository.existsByLoginId("tutor1")).thenReturn(true);

    // when & then
    assertThatThrownBy(() -> service.createTutor(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미 사용 중인 아이디");
  }


  @Test
  @DisplayName("createTutor — 닉네임은 trim 후 저장하고 중복 여부를 검사하지 않는다")
  void createTutor_trims_nickname_and_allows_duplicates() {
    // given
    CreateTutorRequest request = new CreateTutorRequest(
        "tutor2",
        "plainPw",
        "김강사",
        "  중복 닉네임  ",
        "tutor2@example.com",
        "010-2222-3333");
    when(userRepository.existsByLoginId("tutor2")).thenReturn(false);
    when(passwordEncoder.encode("plainPw")).thenReturn("encodedPw");

    // when
    UserResponse response = service.createTutor(request);

    // then
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    assertThat(captor.getValue().getNickname()).isEqualTo("중복 닉네임");
    assertThat(response.nickname()).isEqualTo("중복 닉네임");
    verify(userRepository, never()).existsByLoginId("중복 닉네임");
  }

  @Test
  @DisplayName("createStudent — 닉네임이 유효하지 않으면 저장하지 않는다")
  void createStudent_rejects_invalid_nickname() {
    // given
    CreateStudentRequest request = new CreateStudentRequest(
        "std2",
        "plainPw",
        "홍길동",
        "!",
        "std2@example.com",
        "010-2222-3333",
        "00중학교",
        (byte) 1,
        null,
        null);
    when(userRepository.existsByLoginId("std2")).thenReturn(false);

    // when & then
    assertThatThrownBy(() -> service.createStudent(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("닉네임");
    verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    verify(studentRepository, never()).save(org.mockito.ArgumentMatchers.any(Student.class));
  }

  @Test
  @DisplayName("isLoginIdAvailable — 사용 중인 loginId면 false")
  void isLoginIdAvailable_returns_false_when_login_id_exists() {
    // given
    when(userRepository.existsByLoginId("student")).thenReturn(true);

    // when
    boolean result = service.isLoginIdAvailable("student");

    // then
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("isLoginIdAvailable — 미사용 loginId면 true")
  void isLoginIdAvailable_returns_true_when_login_id_does_not_exist() {
    // given
    when(userRepository.existsByLoginId("newuser")).thenReturn(false);

    // when
    boolean result = service.isLoginIdAvailable("newuser");

    // then
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("getStudentByUserId — userId 로 학생 본인 상세 조회")
  void returns_student_summary_for_existing_user() {
    // given
    User user = User.builder()
        .loginId("std1")
        .password("pw")
        .name("홍길동")
        .phone("010")
        .role(Role.STUDENT)
        .build();
    ReflectionTestUtils.setField(user, "userId", 12L);

    Student student = Student.builder()
        .user(user)
        .schoolName("00중학교")
        .grade((byte) 3)
        .parentName("홍부모")
        .parentPhone("010-9999-8888")
        .build();
    ReflectionTestUtils.setField(student, "studentId", 5L);

    when(studentRepository.findByUser_UserId(12L)).thenReturn(Optional.of(student));

    // when
    StudentSummaryResponse result = service.getStudentByUserId(12L);

    // then
    assertThat(result.userId()).isEqualTo(12L);
    assertThat(result.studentId()).isEqualTo(5L);
    assertThat(result.name()).isEqualTo("홍길동");
    assertThat(result.schoolName()).isEqualTo("00중학교");
    assertThat(result.grade()).isEqualTo((byte) 3);
    assertThat(result.parentName()).isEqualTo("홍부모");
  }

  @Test
  @DisplayName("getStudentByUserId — 존재하지 않는 userId 면 IllegalArgumentException")
  void throws_when_user_not_found() {
    // given
    when(studentRepository.findByUser_UserId(999L)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.getStudentByUserId(999L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("학생 정보를 찾을 수 없습니다");
  }

  @Test
  @DisplayName("getMyProfile — STUDENT는 학생 상세까지 반환한다")
  void getMyProfile_returns_student_profile() {
    // given
    User user = studentUser(12L);
    Student student = student(user, 5L);
    when(userRepository.findById(12L)).thenReturn(Optional.of(user));
    when(studentRepository.findByUser_UserId(12L)).thenReturn(Optional.of(student));

    // when
    MyProfileResponse result = service.getMyProfile(12L);

    // then
    assertThat(result.userId()).isEqualTo(12L);
    assertThat(result.role()).isEqualTo(Role.STUDENT.name());
    assertThat(result.studentId()).isEqualTo(5L);
    assertThat(result.schoolName()).isEqualTo("00중학교");
  }

  @Test
  @DisplayName("getMyProfile — TUTOR는 학생 필드 없이 반환한다")
  void getMyProfile_returns_tutor_profile_without_student_fields() {
    // given
    User user = tutorUser(21L);
    when(userRepository.findById(21L)).thenReturn(Optional.of(user));

    // when
    MyProfileResponse result = service.getMyProfile(21L);

    // then
    assertThat(result.role()).isEqualTo(Role.TUTOR.name());
    assertThat(result.studentId()).isNull();
  }

  @Test
  @DisplayName("getMyProfile — ADMIN은 접근할 수 없다")
  void getMyProfile_rejects_admin() {
    // given
    User user = User.builder()
        .loginId("admin")
        .password("pw")
        .name("관리자")
        .role(Role.ADMIN)
        .build();
    ReflectionTestUtils.setField(user, "userId", 1L);
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    // when & then
    assertThatThrownBy(() -> service.getMyProfile(1L))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("updateMyProfile — 공통 필드만 수정한다")
  void updateMyProfile_updates_common_fields() {
    // given
    User user = tutorUser(21L);
    when(userRepository.findById(21L)).thenReturn(Optional.of(user));
    UpdateMyProfileRequest request = new UpdateMyProfileRequest(
        "새강사", "new@example.com", "010-2222-3333", null, null,
        null, null, null, null);

    // when
    MyProfileResponse result = service.updateMyProfile(21L, request);

    // then
    assertThat(user.getName()).isEqualTo("새강사");
    assertThat(user.getEmail()).isEqualTo("new@example.com");
    assertThat(user.getPhone()).isEqualTo("010-2222-3333");
    assertThat(result.name()).isEqualTo("새강사");
  }

  @Test
  @DisplayName("updateMyProfile — STUDENT 전용 필드를 수정한다")
  void updateMyProfile_updates_student_fields() {
    // given
    User user = studentUser(12L);
    Student student = student(user, 5L);
    when(userRepository.findById(12L)).thenReturn(Optional.of(user));
    when(studentRepository.findByUser_UserId(12L)).thenReturn(Optional.of(student));
    UpdateMyProfileRequest request = new UpdateMyProfileRequest(
        null, null, null, null, null,
        "새학교", (byte) 2, "새부모", "010-7777-8888");

    // when
    MyProfileResponse result = service.updateMyProfile(12L, request);

    // then
    assertThat(student.getSchoolName()).isEqualTo("새학교");
    assertThat(student.getGrade()).isEqualTo((byte) 2);
    assertThat(student.getParentName()).isEqualTo("새부모");
    assertThat(result.parentPhone()).isEqualTo("010-7777-8888");
  }

  @Test
  @DisplayName("updateMyProfile — 새 비밀번호가 있으면 현재 비밀번호를 검증하고 인코딩한다")
  void updateMyProfile_changes_password_with_current_password() {
    // given
    User user = tutorUser(21L);
    when(userRepository.findById(21L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("plain-current", "encodedPw")).thenReturn(true);
    when(passwordEncoder.encode("plain-new")).thenReturn("encodedNew");
    UpdateMyProfileRequest request = new UpdateMyProfileRequest(
        null, null, null, "plain-current", "plain-new",
        null, null, null, null);

    // when
    service.updateMyProfile(21L, request);

    // then
    assertThat(user.getPassword()).isEqualTo("encodedNew");
  }

  @Test
  @DisplayName("updateMyProfile — 현재 비밀번호가 틀리면 새 비밀번호를 인코딩하지 않는다")
  void updateMyProfile_rejects_wrong_current_password() {
    // given
    User user = tutorUser(21L);
    when(userRepository.findById(21L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "encodedPw")).thenReturn(false);
    UpdateMyProfileRequest request = new UpdateMyProfileRequest(
        null, null, null, "wrong", "plain-new",
        null, null, null, null);

    // when & then
    assertThatThrownBy(() -> service.updateMyProfile(21L, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("현재 비밀번호");
    verify(passwordEncoder, never()).encode("plain-new");
  }


  @Test
  @DisplayName("updateMyProfile — 닉네임만 변경할 때 현재 비밀번호를 요구하지 않는다")
  void updateMyProfile_updates_nickname_without_current_password() {
    // given
    User user = tutorUser(21L);
    when(userRepository.findById(21L)).thenReturn(Optional.of(user));
    UpdateMyProfileRequest request = new UpdateMyProfileRequest(
        null, " 새닉네임 ", null, null, null,
        null, null, null, null, null);

    // when
    MyProfileResponse result = service.updateMyProfile(21L, request);

    // then
    assertThat(user.getNickname()).isEqualTo("새닉네임");
    assertThat(result.nickname()).isEqualTo("새닉네임");
    assertThat(result.displayName()).isEqualTo("새닉네임");
    verify(passwordEncoder, never()).matches(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("User displayName — ADMIN은 항상 관리자, 일반 사용자는 닉네임")
  void userDisplayName_uses_admin_label_or_nickname() {
    // given
    User admin = User.builder()
        .loginId("admin")
        .password("pw")
        .name("실명관리자")
        .nickname("관리닉")
        .role(Role.ADMIN)
        .build();
    User student = User.builder()
        .loginId("std")
        .password("pw")
        .name("홍길동")
        .nickname("길동닉")
        .role(Role.STUDENT)
        .build();

    // when & then
    assertThat(admin.getDisplayName()).isEqualTo("관리자");
    assertThat(student.getDisplayName()).isEqualTo("길동닉");
  }

  @Test
  @DisplayName("withdraw — 현재 비밀번호 검증 후 사용자 삭제와 flush를 수행한다")
  void withdraw_deletes_user_and_flushes() {
    // given
    User user = tutorUser(21L);
    when(userRepository.findById(21L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("plain-current", "encodedPw")).thenReturn(true);

    // when
    service.withdraw(21L, new WithdrawRequest("plain-current"));

    // then
    verify(userRepository).delete(user);
    verify(entityManager).flush();
  }

  @Test
  @DisplayName("withdraw — 현재 비밀번호가 틀리면 삭제하지 않는다")
  void withdraw_rejects_wrong_password() {
    // given
    User user = tutorUser(21L);
    when(userRepository.findById(21L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "encodedPw")).thenReturn(false);

    // when & then
    assertThatThrownBy(() -> service.withdraw(21L, new WithdrawRequest("wrong")))
        .isInstanceOf(IllegalArgumentException.class);
    verify(userRepository, never()).delete(user);
    verify(entityManager, never()).flush();
  }

  @Test
  @DisplayName("withdraw — ADMIN은 탈퇴할 수 없다")
  void withdraw_rejects_admin() {
    // given
    User user = User.builder()
        .loginId("admin")
        .password("pw")
        .name("관리자")
        .role(Role.ADMIN)
        .build();
    ReflectionTestUtils.setField(user, "userId", 1L);
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    // when & then
    assertThatThrownBy(() -> service.withdraw(1L, new WithdrawRequest("pw")))
        .isInstanceOf(AccessDeniedException.class);
    verify(userRepository, never()).delete(user);
  }

  private User studentUser(Long userId) {
    User user = User.builder()
        .loginId("std1")
        .password("encodedPw")
        .name("홍길동")
        .email("std@example.com")
        .phone("010")
        .role(Role.STUDENT)
        .build();
    ReflectionTestUtils.setField(user, "userId", userId);
    return user;
  }

  private User tutorUser(Long userId) {
    User user = User.builder()
        .loginId("tutor1")
        .password("encodedPw")
        .name("김강사")
        .email("tutor@example.com")
        .phone("010")
        .role(Role.TUTOR)
        .build();
    ReflectionTestUtils.setField(user, "userId", userId);
    return user;
  }

  private Student student(User user, Long studentId) {
    Student student = Student.builder()
        .user(user)
        .schoolName("00중학교")
        .grade((byte) 3)
        .parentName("홍부모")
        .parentPhone("010-9999-8888")
        .build();
    ReflectionTestUtils.setField(student, "studentId", studentId);
    return student;
  }
}
