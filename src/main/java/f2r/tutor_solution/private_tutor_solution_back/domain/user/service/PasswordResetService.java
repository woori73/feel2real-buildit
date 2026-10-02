package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.LoginIdFindResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.PasswordResetTokenResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.PasswordResetToken;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.PasswordResetTokenRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {

  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final int TOKEN_BYTE_LENGTH = 32;
  private static final int TOKEN_EXPIRY_MINUTES = 60;
  private static final int MIN_PASSWORD_LENGTH = 8;

  private final UserRepository userRepository;
  private final PasswordResetTokenRepository tokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final SecureRandom secureRandom = new SecureRandom();
  private final Clock clock = Clock.systemDefaultZone();

  public LoginIdFindResponse findLoginIds(String email) {
    String normalizedEmail = normalizeEmail(email);
    requireValidEmail(normalizedEmail);

    List<LoginIdFindResponse.Account> accounts = userRepository.findAllByEmail(normalizedEmail)
        .stream()
        .sorted(Comparator.comparing(User::getLoginId))
        .map(user -> new LoginIdFindResponse.Account(
            user.getLoginId(),
            user.getRole(),
            roleLabel(user.getRole())))
        .toList();
    return new LoginIdFindResponse(normalizedEmail, accounts);
  }

  @Transactional
  public PasswordResetTokenResult requestReset(String loginId, String email, String baseUrl) {
    String normalizedLoginId = normalizeLoginId(loginId);
    requireValidLoginId(normalizedLoginId);
    String normalizedEmail = normalizeEmail(email);
    requireValidEmail(normalizedEmail);

    User user = userRepository.findByLoginIdAndEmail(normalizedLoginId, normalizedEmail)
        .orElseThrow(() -> new IllegalArgumentException("아이디와 이메일이 일치하는 계정이 없습니다."));

    LocalDateTime now = now();
    tokenRepository.markUnusedTokensUsed(user, now);

    String rawToken = generateToken();
    String resetUrl = buildResetUrl(baseUrl, rawToken);
    tokenRepository.save(new PasswordResetToken(
        user,
        hashToken(rawToken),
        now.plusMinutes(TOKEN_EXPIRY_MINUTES)));

    // rawToken·resetUrl은 어떤 로그에도 남기지 않는다 — 토큰을 아는 사람은 누구나 비밀번호를
    // 바꿀 수 있다. 값은 응답으로만 전달하고 DB에는 SHA-256 hash만 저장된다.
    log.info("Password reset token issued for loginId={} email={} expiresMinutes={}",
        normalizedLoginId, normalizedEmail, TOKEN_EXPIRY_MINUTES);

    return new PasswordResetTokenResult(normalizedEmail, resetUrl);
  }

  @Transactional
  public void confirmReset(String token, String newPassword) {
    if (!hasText(token)) {
      throw new IllegalArgumentException("비밀번호 재설정 링크가 올바르지 않습니다.");
    }
    if (!hasText(newPassword) || newPassword.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException("비밀번호는 8자 이상이어야 합니다.");
    }

    PasswordResetToken resetToken = tokenRepository.findByTokenHash(hashToken(token.trim()))
        .orElseThrow(() -> new IllegalArgumentException("비밀번호 재설정 링크가 올바르지 않습니다."));

    LocalDateTime now = now();
    if (resetToken.isUsed()) {
      throw new IllegalArgumentException("이미 사용된 비밀번호 재설정 링크입니다.");
    }
    if (resetToken.isExpired(now)) {
      throw new IllegalArgumentException("비밀번호 재설정 링크가 만료되었습니다.");
    }

    resetToken.getUser().updatePassword(passwordEncoder.encode(newPassword));
    resetToken.markUsed(now);
  }

  private String normalizeLoginId(String loginId) {
    return loginId == null ? "" : loginId.trim();
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }

  private void requireValidLoginId(String loginId) {
    if (!hasText(loginId)) {
      throw new IllegalArgumentException("아이디를 입력해 주세요.");
    }
  }

  private void requireValidEmail(String email) {
    if (!hasText(email)) {
      throw new IllegalArgumentException("이메일을 입력해 주세요.");
    }
    if (!EMAIL_PATTERN.matcher(email).matches()) {
      throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
    }
  }

  private String roleLabel(f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role role) {
    return switch (role) {
      case STUDENT -> "학생";
      case TUTOR -> "강사";
      case ADMIN -> "관리자";
      case CREATOR -> "CHOONGGI 운영자";
    };
  }

  private String generateToken() {
    byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String buildResetUrl(String baseUrl, String rawToken) {
    return UriComponentsBuilder.fromUriString(baseUrl)
        .path("/signin/reset/password/confirm")
        .queryParam("token", rawToken)
        .build()
        .toUriString();
  }

  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm is not available", e);
    }
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
