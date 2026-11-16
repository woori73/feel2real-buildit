package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.TemporaryPasswordIssue;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.TemporaryPasswordIssueRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 임시 비밀번호 발급과 "로그인 후 새 비밀번호 설정".
 *
 * <ul>
 *   <li>관리자 발급: 이메일 없이 가입한 회원용. 관리자 화면에 한 번만 보여 주고, 발급 기록(누가·언제·누구에게)만 남긴다.
 *   <li>계정 찾기 재설정: 아이디+이메일 확인 뒤 같은 방식(무작위·72시간·변경 강제)으로 바꾼다.
 *   <li>비밀번호는 해시로만 저장하며 로그·기록 표에 남기지 않는다.
 * </ul>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class TemporaryPasswordService {

  public static final Duration VALIDITY = Duration.ofHours(72);
  static final int MIN_PASSWORD_LENGTH = 8;

  private final UserRepository userRepository;
  private final TemporaryPasswordIssueRepository issueRepository;
  private final PasswordEncoder passwordEncoder;
  private final SecureRandom random;
  private final Clock clock;

  @Autowired
  public TemporaryPasswordService(UserRepository userRepository, TemporaryPasswordIssueRepository issueRepository,
      PasswordEncoder passwordEncoder) {
    this(userRepository, issueRepository, passwordEncoder, new SecureRandom(), Clock.systemDefaultZone());
  }

  TemporaryPasswordService(UserRepository userRepository, TemporaryPasswordIssueRepository issueRepository,
      PasswordEncoder passwordEncoder, SecureRandom random, Clock clock) {
    this.userRepository = userRepository;
    this.issueRepository = issueRepository;
    this.passwordEncoder = passwordEncoder;
    this.random = random;
    this.clock = clock;
  }

  /** 관리자 발급 결과. temporaryPassword는 이 응답에서 한 번만 나간다. expiresAt은 한국 시간. */
  public record AdminIssueResult(Long userId, String loginId, String displayName, String temporaryPassword,
      LocalDateTime expiresAt) {}

  /** 관리자가 회원 한 명에게 임시 비밀번호를 발급한다. 관리자 계정에는 발급하지 않는다. */
  @Transactional
  public AdminIssueResult issueByAdmin(AuthenticatedPrincipal admin, Long targetUserId) {
    if (admin == null || !"ADMIN".equals(admin.role())) throw new AccessDeniedException("관리자만 발급할 수 있습니다.");
    User target = userRepository.findById(targetUserId)
        .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    if (target.getRole() == Role.ADMIN) {
      throw new IllegalArgumentException("관리자 계정에는 임시 비밀번호를 발급하지 않습니다. 관리자 로그인 화면의 비밀번호 변경을 써 주세요.");
    }
    LocalDateTime now = LocalDateTime.now(clock);
    String raw = apply(target, now);
    issueRepository.save(new TemporaryPasswordIssue(
        target.getUserId(), target.getLoginId(), admin.userId(), admin.loginId(), now, now.plus(VALIDITY)));
    log.info("임시 비밀번호 발급: by={} target={}", admin.loginId(), target.getLoginId());
    return new AdminIssueResult(target.getUserId(), target.getLoginId(), target.getDisplayName(), raw,
        KstTime.toKst(now.plus(VALIDITY)));
  }

  /** 계정 찾기 재설정용. 무작위 임시 비밀번호를 넣고 원문을 돌려준다(화면에 한 번 표시). */
  @Transactional
  public String issueForSelfReset(User user) {
    return apply(user, LocalDateTime.now(clock));
  }

  /** 임시 비밀번호로 로그인한 회원이 새 비밀번호를 정한다. 성공하면 임시 상태가 끝난다. */
  @Transactional
  public User changeTemporaryPassword(Long userId, String newPassword, String confirmPassword) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    if (!user.isPasswordChangeRequired()) {
      throw new IllegalArgumentException("이미 새 비밀번호로 바꾼 계정입니다. 다시 로그인해 주세요.");
    }
    if (user.isTemporaryPasswordExpired(LocalDateTime.now(clock))) {
      throw new IllegalArgumentException("임시 비밀번호 유효기간(72시간)이 지났습니다. 관리자에게 다시 발급을 요청해 주세요.");
    }
    if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException("새 비밀번호는 8자 이상이어야 합니다.");
    }
    if (!newPassword.equals(confirmPassword)) {
      throw new IllegalArgumentException("새 비밀번호 확인이 일치하지 않습니다.");
    }
    if (passwordEncoder.matches(newPassword, user.getPassword())) {
      throw new IllegalArgumentException("임시 비밀번호와 다른 비밀번호를 정해 주세요.");
    }
    user.updatePassword(passwordEncoder.encode(newPassword));
    return user;
  }

  private String apply(User user, LocalDateTime now) {
    String raw = TemporaryPasswordGenerator.generate(random);
    user.issueTemporaryPassword(passwordEncoder.encode(raw), now.plus(VALIDITY));
    return raw;
  }
}
