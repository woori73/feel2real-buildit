package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 로그인한 ADMIN이 본인 계정의 로그인 ID와 비밀번호를 함께 교체한다(로그인 ID·비밀번호는 사용자가 화면에서 직접 입력).
 *
 * <p>보안 계약:
 * <ul>
 *   <li>대상은 요청 본문이 아니라 인증된 세션의 userId(+role ADMIN)다. 다른 계정을 지정할 수 없다.
 *   <li>현재 비밀번호를 BCrypt로 다시 확인한다(세션 탈취만으로는 교체 불가). 실패는 userId별 sliding window로 제한한다.
 *   <li>같은 users 행을 갱신하므로 권한(ADMIN)·user_id·운영 데이터·FK 관계는 그대로다. 과거 기록의 created_by/updated_by 문자열은 이전 ID로 남는다.
 *   <li>교체 즉시 {@link AdminSessionValidator}의 stamp가 달라져 이전 ID·비밀번호와 기존 ADMIN 세션이 모두 무효가 된다.
 *   <li>평문·해시·ID를 로그·예외·응답에 포함하지 않는다(로그에는 userId만).
 * </ul>
 */
@Slf4j
@Service
public class AdminCredentialService {

  static final int MIN_PASSWORD_LENGTH = 16;
  static final int MAX_PASSWORD_BYTES = 72;
  static final Pattern LOGIN_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{5,49}$");

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;
  private final int maxFailures;
  private final Duration window;
  private final ConcurrentHashMap<Long, Deque<Instant>> failures = new ConcurrentHashMap<>();

  @Autowired
  public AdminCredentialService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this(userRepository, passwordEncoder, Clock.systemDefaultZone(), 5, Duration.ofMinutes(10));
  }

  AdminCredentialService(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock,
      int maxFailures, Duration window) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
    this.maxFailures = maxFailures;
    this.window = window;
  }

  @Transactional
  public void rotate(Long userId, String currentPassword, String newLoginId, String newPassword, String confirmPassword) {
    enforceRateLimit(userId);
    User admin = userRepository.findById(userId)
        .filter(u -> u.getRole() == Role.ADMIN)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 사용할 수 있습니다."));

    if (!hasText(currentPassword) || !passwordEncoder.matches(currentPassword, admin.getPassword())) {
      recordFailure(userId);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다.");
    }
    // 현재 비밀번호를 아는 사람 — 이후 검증 실패는 brute-force 신호가 아니므로 세지 않는다.
    String loginId = newLoginId == null ? "" : newLoginId.trim();
    if (!LOGIN_ID.matcher(loginId).matches()) {
      throw bad("새 로그인 ID는 영문·숫자로 시작하는 6~50자(영문·숫자·. _ - 사용)여야 합니다.");
    }
    if (loginId.equalsIgnoreCase(admin.getLoginId())) {
      throw bad("새 로그인 ID는 현재 ID와 달라야 합니다.");
    }
    if (userRepository.existsByLoginId(loginId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
    }
    if (!hasText(newPassword) || !newPassword.equals(confirmPassword)) {
      throw bad("새 비밀번호와 확인값이 일치하지 않습니다.");
    }
    if (newPassword.length() < MIN_PASSWORD_LENGTH) {
      throw bad("새 비밀번호는 16자 이상이어야 합니다.");
    }
    if (newPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
      throw bad("새 비밀번호는 72바이트 이하이어야 합니다.");
    }
    if (passwordEncoder.matches(newPassword, admin.getPassword())) {
      throw bad("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
    }
    if (newPassword.toLowerCase(Locale.ROOT).contains(loginId.toLowerCase(Locale.ROOT))) {
      throw bad("새 비밀번호에 새 로그인 ID를 포함할 수 없습니다.");
    }

    admin.updateLoginId(loginId);
    admin.updatePassword(passwordEncoder.encode(newPassword));
    try {
      userRepository.saveAndFlush(admin);
    } catch (DataIntegrityViolationException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
    }
    failures.remove(userId);
    log.info("ADMIN credentials rotated userId={}", userId);
  }

  private static ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private void enforceRateLimit(Long userId) {
    Instant now = Instant.now(clock);
    Deque<Instant> attempts = failures.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());
    synchronized (attempts) {
      while (!attempts.isEmpty() && Duration.between(attempts.peekFirst(), now).compareTo(window) >= 0) {
        attempts.pollFirst();
      }
      if (attempts.size() >= maxFailures) {
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "반복 실패로 잠시 차단되었습니다. 잠시 후 다시 시도해 주세요.");
      }
    }
  }

  private void recordFailure(Long userId) {
    Deque<Instant> attempts = failures.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());
    synchronized (attempts) {
      attempts.addLast(Instant.now(clock));
    }
  }

  private static boolean hasText(String v) {
    return v != null && !v.isBlank();
  }
}
