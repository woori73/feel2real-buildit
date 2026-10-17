package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ADMIN 로그인 세션의 서버 측 유효성 확인. JWT는 서버에 저장되지 않으므로, ADMIN 토큰에만 "자격 증명 stamp"를 넣고
 * 매 요청 현재 계정의 stamp와 비교한다. ID나 비밀번호가 바뀌면 stamp가 달라져 이전에 발급된 ADMIN 세션이 전부 무효가 된다.
 *
 * <ul>
 *   <li>일반 회원·TUTOR 토큰은 검사하지 않는다(기존과 동일, DB 조회 없음).
 *   <li>stamp = SHA-256(loginId + ":" + BCrypt 해시)의 앞 22자(base64url). 비밀번호·해시는 토큰에 담기지 않는다.
 *   <li>stamp가 없는 ADMIN 토큰(이 기능 이전에 발급)은 무효다 — 배포 후 관리자는 한 번 다시 로그인한다.
 * </ul>
 */
@Component
public class AdminSessionValidator {

  private final UserRepository userRepository;
  private final boolean enabled;

  public AdminSessionValidator(
      UserRepository userRepository,
      @Value("${app.security.admin-session-binding.enabled:true}") boolean enabled) {
    this.userRepository = userRepository;
    this.enabled = enabled;
  }

  /** 로그인 직후 ADMIN 토큰에 넣을 stamp. */
  public String stampFor(Long userId) {
    return userRepository.findById(userId).map(AdminSessionValidator::stampOf).orElse(null);
  }

  /** ADMIN이 아닌 주체는 항상 true. ADMIN은 DB의 현재 계정과 stamp가 일치할 때만 true. */
  public boolean isValid(AuthenticatedPrincipal principal) {
    if (!enabled || !"ADMIN".equals(principal.role())) return true;
    if (principal.credentialStamp() == null) return false;
    return userRepository.findById(principal.userId())
        .filter(u -> u.getRole() == Role.ADMIN)
        .map(u -> MessageDigest.isEqual(
            stampOf(u).getBytes(StandardCharsets.UTF_8), principal.credentialStamp().getBytes(StandardCharsets.UTF_8)))
        .orElse(false);
  }

  static String stampOf(User user) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest((user.getLoginId() + ":" + user.getPassword()).getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 22);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
