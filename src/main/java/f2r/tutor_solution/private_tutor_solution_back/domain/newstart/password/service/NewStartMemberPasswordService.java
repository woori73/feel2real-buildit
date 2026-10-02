package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.NewStartPasswordChangeRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.NewStartPasswordStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 회원 본인의 비밀번호 변경(자발적 변경과 ADMIN 초기화 후 강제 설정이 같은 규칙을 쓴다).
 *
 * <p>ADMIN 초기화 API({@link NewStartAdminPasswordResetService})와 이 경로는 일부러 분리했다:
 * 대상 지정 권한(운영자만)과 현재 비밀번호 지식(본인만)이 서로 다른 자격이다.
 *
 * <p>강제 해제 조건은 오직 하나 — 현재 비밀번호(=ADMIN이 준 임시 값)를 직접 통과했을 때만
 * {@code password_reset_required}가 FALSE가 된다. 플래그를 직접 켜고 끄는 엔드포인트는 없다.
 */
@Slf4j
@Service
public class NewStartMemberPasswordService {

  /** NewStart 가입 정책과 같은 최소 길이 — {@code NewStartIdPwSignupService.MIN_PASSWORD}(8). */
  public static final int MIN_PASSWORD_LENGTH = 8;
  private static final int MAX_PASSWORD_LENGTH = 100;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public NewStartMemberPasswordService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public NewStartPasswordStatus status(Long userId) {
    User user = loadMember(userId);
    return new NewStartPasswordStatus(
        user.isPasswordResetRequired(),
        MIN_PASSWORD_LENGTH,
        user.getLastLoginAt());
  }

  /** {@code /newstart/password/set} 화면이 강제 진입인지 자발적 변경인지 제목을 고르는 데 쓴다. */
  @Transactional(readOnly = true)
  public PasswordPageView pageView(Long userId) {
    User user = loadMember(userId);
    return new PasswordPageView(user.getLoginId(), user.isPasswordResetRequired(), MIN_PASSWORD_LENGTH);
  }

  public record PasswordPageView(String loginId, boolean changeRequired, int minPasswordLength) {
  }

  @Transactional
  public void change(Long userId, NewStartPasswordChangeRequest request) {
    User user = loadMember(userId);
    if (user.getRole() == Role.ADMIN) {
      // 운영 계정은 이 경로가 아니라 /admin/login 의 운영자 비밀번호 변경 폼(16자 정책)을 쓴다.
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NewStart 회원 계정만 사용할 수 있습니다.");
    }
    String current = request.currentPassword();
    String next = request.newPassword();
    String confirm = request.confirmPassword();

    if (isBlank(current)) {
      throw new IllegalArgumentException("현재 비밀번호를 입력해 주세요.");
    }
    if (next == null || next.length() < MIN_PASSWORD_LENGTH || next.length() > MAX_PASSWORD_LENGTH) {
      throw new IllegalArgumentException("새 비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상으로 입력해 주세요.");
    }
    if (!next.equals(confirm)) {
      throw new IllegalArgumentException("새 비밀번호가 서로 일치하지 않습니다.");
    }
    if (!passwordEncoder.matches(current, user.getPassword())) {
      throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
    }
    if (passwordEncoder.matches(next, user.getPassword())) {
      throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 다르게 입력해 주세요.");
    }

    user.updatePassword(passwordEncoder.encode(next));
    user.clearPasswordResetRequirement();
    userRepository.save(user);

    // 비밀번호 값은 로그에 남기지 않는다. 대상 userId만.
    log.info("NEWSTART_MEMBER_PASSWORD_CHANGED userId={}", user.getUserId());
  }

  private User loadMember(Long userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
