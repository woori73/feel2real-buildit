package f2r.tutor_solution.private_tutor_solution_back.domain.user.social;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.LoginResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthClient.NaverProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 네이버 프로필 → Feel2Real User 식별/생성.
 *
 * <p>정책:
 * <ul>
 *   <li>매핑 키는 (provider=NAVER, providerUserId) 연결 레코드뿐. 이메일로 기존 계정을
 *       자동 병합하지 않는다(계정 탈취/오병합 방지).
 *   <li>신규 회원은 항상 Role.STUDENT로만 생성 — 네이버 로그인으로 ADMIN/TUTOR 권한은
 *       절대 부여되지 않는다.
 *   <li>password 컬럼은 NOT NULL이므로 해석 불가능한 랜덤 값의 bcrypt 해시를 넣어
 *       ID/PW 로그인을 원천 차단한다(비밀번호 재설정 흐름만 추후 정책화 가능).
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NaverLoginService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository userRepository;
  private final SocialAccountRepository socialAccountRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public LoginResponse loginOrUpdate(NaverProfile profile) {
    if (profile == null || profile.providerUserId() == null || profile.providerUserId().isBlank()) {
      throw new IllegalArgumentException("네이버 프로필에서 stable user id를 확인할 수 없습니다.");
    }
    String providerUserId = profile.providerUserId();
    Optional<SocialAccount> existing =
        socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, providerUserId);
    if (existing.isPresent()) {
      User user = userRepository.findById(existing.get().getUserId())
          .orElseThrow(() -> new IllegalStateException("연결된 회원을 찾을 수 없습니다."));
      return LoginResponse.from(user);
    }
    return createUser(providerUserId, profile);
  }

  private LoginResponse createUser(String providerUserId, NaverProfile profile) {
    String loginId = uniqueLoginId(providerUserId);
    try {
      AuditorContextHolder.set(loginId);
      User user = User.builder()
          .loginId(loginId)
          .password(passwordEncoder.encode(randomSecret()))
          .name(resolveName(profile))
          .nickname(resolveNickname(profile, providerUserId))
          .email(profile.email())
          .role(Role.STUDENT)
          .build();
      userRepository.save(user);
      // 동시 콜백으로 (provider, provider_user_id) unique 충돌 시 트랜잭션 전체가 rollback된다.
      // 부분 상태를 안 남기는 편이 안전하므로 여기서 흡수 시도하지 않고 실패를 전파한다.
      socialAccountRepository.save(SocialAccount.builder()
          .userId(user.getUserId())
          .provider(SocialProvider.NAVER)
          .providerUserId(providerUserId)
          .build());
      return LoginResponse.from(user);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  /** "naver_" + sha256(providerUserId) 앞 16hex — 재현 가능하고 50자 제한 안에 든다. */
  private String uniqueLoginId(String providerUserId) {
    String base = "naver_" + sha256Hex(providerUserId).substring(0, 16);
    if (!userRepository.existsByLoginId(base)) return base;
    for (int i = 0; i < 5; i++) {
      String candidate = base + "_" + suffix();
      if (!userRepository.existsByLoginId(candidate)) return candidate;
    }
    throw new IllegalStateException("네이버 회원용 아이디를 발급할 수 없습니다.");
  }

  private String resolveName(NaverProfile profile) {
    String name = profile.name() != null ? profile.name() : profile.nickname();
    if (name == null) return "네이버회원";
    return name.length() > 30 ? name.substring(0, 30) : name;
  }

  /** users.nickname NOT NULL + 프론트 닉네임 문자열 제약을 우회하지 않도록 최소 정제만 한다. */
  private String resolveNickname(NaverProfile profile, String providerUserId) {
    String candidate = profile.nickname() != null ? profile.nickname() : profile.name();
    if (candidate != null) {
      String cleaned = candidate.trim().replaceAll("[^\\p{IsHangul}A-Za-z0-9 _-]", "").trim();
      if (cleaned.length() >= 2) {
        return cleaned.length() > 20 ? cleaned.substring(0, 20) : cleaned;
      }
    }
    String tail = sha256Hex(providerUserId).substring(0, 4);
    return "네이버" + tail;
  }

  private String randomSecret() {
    byte[] buf = new byte[32];
    RANDOM.nextBytes(buf);
    return HexFormat.of().formatHex(buf);
  }

  private String suffix() {
    byte[] buf = new byte[2];
    RANDOM.nextBytes(buf);
    return HexFormat.of().formatHex(buf);
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256를 사용할 수 없습니다.", e);
    }
  }
}
