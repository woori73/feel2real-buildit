package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.CreateStudentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.CreateTutorRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.UserResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request.LoginRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request.UpdateMyProfileRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request.WithdrawRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.LoginResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.MyProfileResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.StudentSummaryResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣A-Za-z0-9 _-]{2,20}$");

  private final UserRepository userRepository;
  private final StudentRepository studentRepository;
  private final PasswordEncoder passwordEncoder;

  @PersistenceContext
  private EntityManager entityManager;

  public LoginResponse login(LoginRequest request) {
    User user = userRepository.findByLoginId(request.loginId())
        .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));
    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
    }
    return LoginResponse.from(user);
  }

  /** JWT 쿠키 기반 세션 복원용 — /api/auth/me 에서 호출. */
  public LoginResponse getLoginInfo(Long userId) {
    return userRepository.findById(userId)
        .map(LoginResponse::from)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: userId=" + userId));
  }

  public boolean isLoginIdAvailable(String loginId) {
    if (loginId == null || loginId.isBlank()) {
      return false;
    }
    return !userRepository.existsByLoginId(loginId.trim());
  }

  @Transactional
  public UserResponse createStudent(CreateStudentRequest request) {
    if (userRepository.existsByLoginId(request.loginId())) {
      throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
    }
    String nickname = requireValidNickname(request.nickname());
    try {
      AuditorContextHolder.set(request.loginId());

      User studentUser = User.builder()
          .loginId(request.loginId())
          .password(passwordEncoder.encode(request.password()))
          .name(request.name())
          .nickname(nickname)
          .email(request.email())
          .phone(request.phone())
          .role(Role.STUDENT)
          .build();
      userRepository.save(studentUser);

      Student student = Student.builder()
          .user(studentUser)
          .schoolName(request.schoolName())
          .grade(request.grade())
          .parentName(request.parentName())
          .parentPhone(request.parentPhone())
          .build();
      studentRepository.save(student);

      return UserResponse.from(studentUser);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public UserResponse createTutor(CreateTutorRequest request) {
    if (userRepository.existsByLoginId(request.loginId())) {
      throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
    }
    String nickname = requireValidNickname(request.nickname());
    try {
      AuditorContextHolder.set(request.loginId());

      User tutorUser = User.builder()
          .loginId(request.loginId())
          .password(passwordEncoder.encode(request.password()))
          .name(request.name())
          .nickname(nickname)
          .email(request.email())
          .phone(request.phone())
          .role(Role.TUTOR)
          .build();
      userRepository.save(tutorUser);

      return UserResponse.from(tutorUser);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  public List<StudentSummaryResponse> getAllStudents() {
    return studentRepository.findAll().stream()
        .map(StudentSummaryResponse::from)
        .toList();
  }

  public MyProfileResponse getMyProfile(Long userId) {
    User user = findSelfUser(userId);
    requireSelfServiceRole(user);
    if (user.getRole() == Role.STUDENT) {
      Student student = studentRepository.findByUser_UserId(userId)
          .orElseThrow(() -> new IllegalArgumentException(
              "해당 user_id 의 학생 정보를 찾을 수 없습니다: userId=" + userId));
      return MyProfileResponse.from(user, student);
    }
    return MyProfileResponse.from(user);
  }

  @Transactional
  public MyProfileResponse updateMyProfile(Long userId, UpdateMyProfileRequest request) {
    User user = findSelfUser(userId);
    requireSelfServiceRole(user);

    if (request.name() != null) {
      user.updateName(request.name());
    }
    if (request.nickname() != null) {
      user.updateNickname(requireValidNickname(request.nickname()));
    }
    if (request.email() != null) {
      user.updateEmail(request.email());
    }
    if (request.phone() != null) {
      user.updatePhone(request.phone());
    }
    if (hasText(request.newPassword())) {
      verifyCurrentPassword(user, request.currentPassword());
      user.updatePassword(passwordEncoder.encode(request.newPassword()));
    }

    if (user.getRole() == Role.STUDENT) {
      Student student = studentRepository.findByUser_UserId(userId)
          .orElseThrow(() -> new IllegalArgumentException(
              "해당 user_id 의 학생 정보를 찾을 수 없습니다: userId=" + userId));
      if (request.schoolName() != null || request.grade() != null) {
        student.updateSchoolInfo(
            request.schoolName() != null ? request.schoolName() : student.getSchoolName(),
            request.grade() != null ? request.grade() : student.getGrade());
      }
      if (request.parentName() != null || request.parentPhone() != null) {
        student.updateParentInfo(
            request.parentName() != null ? request.parentName() : student.getParentName(),
            request.parentPhone() != null ? request.parentPhone() : student.getParentPhone());
      }
      return MyProfileResponse.from(user, student);
    }

    return MyProfileResponse.from(user);
  }

  /**
   * 회원탈퇴는 users row 삭제를 루트로 삼는다.
   * DB cascade 범위: students/conversations/sources/student_scores/tutor academy/tutor-student-map 등 소유 데이터는
   * 기존 FK 정책으로 삭제되고, learning_materials.assigned_by 는 NULL, problem/answer source 연결은 V8 FK 정책으로
   * source 삭제에 동반 삭제된다. flush로 FK 정책 회귀를 같은 트랜잭션에서 즉시 검출한다.
   */
  @Transactional
  public void withdraw(Long userId, WithdrawRequest request) {
    User user = findSelfUser(userId);
    requireSelfServiceRole(user);
    verifyCurrentPassword(user, request.currentPassword());

    userRepository.delete(user);
    entityManager.flush();
  }

  /** users.user_id 로 학생 본인 상세 조회 (학생 직접 로그인 후 본인 프로필 fetch 용). */
  public StudentSummaryResponse getStudentByUserId(Long userId) {
    return studentRepository.findByUser_UserId(userId)
        .map(StudentSummaryResponse::from)
        .orElseThrow(() -> new IllegalArgumentException(
            "해당 user_id 의 학생 정보를 찾을 수 없습니다: userId=" + userId));
  }

  private User findSelfUser(Long userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: userId=" + userId));
  }

  private void requireSelfServiceRole(User user) {
    if (user.getRole() == Role.ADMIN) {
      throw new AccessDeniedException("관리자는 개인정보 수정/회원탈퇴 대상이 아닙니다.");
    }
  }

  private void verifyCurrentPassword(User user, String currentPassword) {
    if (!hasText(currentPassword) || !passwordEncoder.matches(currentPassword, user.getPassword())) {
      throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
    }
  }

  private String requireValidNickname(String nickname) {
    if (nickname == null) {
      throw new IllegalArgumentException("닉네임을 입력해 주세요.");
    }
    String normalized = nickname.trim();
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("닉네임을 입력해 주세요.");
    }
    if (!NICKNAME_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("닉네임은 한글/영문/숫자/공백/_/- 조합 2~20자로 입력해 주세요.");
    }
    return normalized;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
