package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영 ADMIN(admin-f2r) 비밀번호 변경 — {@code /admin/login} 화면의 변경 폼 전용.
 *
 * <p>보안 계약:
 * <ul>
 *   <li>대상은 서버 고정({@code admin-f2r} + role ADMIN). 요청에 userId/role이 없어 다른 계정 지정 불가.
 *   <li>기존 비밀번호를 {@code passwordEncoder.matches()}로 확인한 뒤에만 변경.
 *   <li>새 비밀번호 16자 이상(UTF-8 72바이트 이하 — BCrypt 상한), 확인값 일치 필수.
 *   <li>평문·해시를 로그·예외·응답에 절대 포함하지 않는다.
 *   <li>인증 실패(대상 아님/현재 비번 오류)는 loginId별 sliding window로 집계해 상한 초과 시 429.
 *       기존 코드의 in-memory interval 차단({@code WikiLiveMessageService})과 같은 방식이며,
 *       별도 의존성·인프라 없이 동작한다.
 * </ul>
 */
@Slf4j
@Service
public class AdminPasswordChangeService {

  /** 비밀번호 변경이 허용된 유일한 운영자 로그인 ID (ID 자체는 secret이 아니라 코드에 둔다). */
  static final String ADMIN_LOGIN_ID = "admin-f2r";

  static final int MIN_PASSWORD_LENGTH = 16;

  /** BCrypt 입력 상한 — 초과분 silent truncate 방지를 위해 사전 거부한다. */
  static final int MAX_PASSWORD_BYTES = 72;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final int maxFailures;
  private final Duration failureWindow;
  private final Clock clock;
  private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

  @Autowired
  public AdminPasswordChangeService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      @Value("${app.admin-password-change.max-failures:5}") int maxFailures,
      @Value("${app.admin-password-change.window-minutes:10}") long windowMinutes) {
    this(userRepository, passwordEncoder, maxFailures, Duration.ofMinutes(windowMinutes),
        Clock.systemDefaultZone());
  }

  /** 테스트용 — 실패 상한/윈도우/시계를 주입한다. */
  AdminPasswordChangeService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      int maxFailures,
      Duration failureWindow,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.maxFailures = maxFailures;
    this.failureWindow = failureWindow;
    this.clock = clock;
  }

  @Transactional
  public void changePassword(
      String loginId, String currentPassword, String newPassword, String confirmPassword) {
    String normalized = loginId == null ? "" : loginId.trim();
    enforceRateLimit(normalized);

    User admin = userRepository.findByLoginId(normalized)
        .filter(user -> ADMIN_LOGIN_ID.equals(user.getLoginId()) && user.getRole() == Role.ADMIN)
        .orElseThrow(() -> {
          recordFailure(normalized);
          return new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 비밀번호 변경 대상이 아닙니다.");
        });

    if (!hasText(currentPassword) || !passwordEncoder.matches(currentPassword, admin.getPassword())) {
      recordFailure(normalized);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다.");
    }
    // 여기까지 통과 = 기존 비밀번호를 아는 사람. 이후 검증 실패는 brute-force 신호가 아니라 세지 않는다.
    if (!hasText(newPassword) || !newPassword.equals(confirmPassword)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 비밀번호와 확인값이 일치하지 않습니다.");
    }
    if (newPassword.length() < MIN_PASSWORD_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "새 비밀번호는 16자 이상이어야 합니다.");
    }
    if (newPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "새 비밀번호는 72바이트 이하이어야 합니다.");
    }

    admin.updatePassword(passwordEncoder.encode(newPassword));
    userRepository.save(admin);
    failures.remove(normalized);
    log.info("Admin password changed loginId={}", normalized);
  }

  private void enforceRateLimit(String key) {
    Instant now = Instant.now(clock);
    Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
    synchronized (attempts) {
      while (!attempts.isEmpty()
          && Duration.between(attempts.peekFirst(), now).compareTo(failureWindow) >= 0) {
        attempts.pollFirst();
      }
      if (attempts.size() >= maxFailures) {
        throw new ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS, "반복 실패로 잠시 차단되었습니다. 잠시 후 다시 시도해 주세요.");
      }
    }
  }

  private void recordFailure(String key) {
    Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
    synchronized (attempts) {
      attempts.addLast(Instant.now(clock));
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
