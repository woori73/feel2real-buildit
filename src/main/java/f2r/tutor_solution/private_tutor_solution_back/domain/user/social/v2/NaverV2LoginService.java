package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.LoginResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2.NaverV2Client.NaverV2Profile;
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
 * NewStart 전용 — 네이버 프로필 -&gt; Feel2Real User 식별/자동가입(V2).
 *
 * <p>내부 회원/인증 기반({@link User}, {@link UserRepository}, {@link SocialAccount},
 * {@link SocialAccountRepository})은 V1과 완전히 동일하게 <b>재사용</b>한다 — 이 저장소들은
 * 장애의 대상이 아니었다. 매핑 키는 (provider=NAVER, providerUserId)뿐이며, 동일 provider 테이블을
 * 공유하므로 V1으로 이미 연결된 네이버 계정이 있다면 V2도 같은 User를 찾아 재사용한다(중복 생성 없음).
 *
 * <p><b>ROLE 전략(명시적 결정, 임의 아님)</b>: NewStart(새출발기금 부실차주) 사용자는 교육 서비스의
 * "학생"이 아니다. 그러나 현재 {@code users.role}은 MySQL 네이티브 ENUM('STUDENT','ADMIN','TUTOR')
 * NOT NULL이라 마이그레이션 없이는 제4의 값을 저장할 수 없다(schema 주석: "STUDENT / ADMIN 사용,
 * TUTOR는 예약값" — 즉 이 스키마에서 STUDENT는 이미 사실상 "일반 인증 회원"의 기술적 자리다).
 * 불필요한 DB migration을 피하기 위해 이번 V2도 V1과 동일하게 STUDENT를 <i>authorization 상
 * 최소권한 placeholder</i>로만 재사용한다 — "새출발 사용자 = 학생"이라는 의미 부여가 아니다.
 * 화면/문구 어디에도 "학생"을 노출하지 않는다. 전용 role이 실제로 필요해지면 별도 PR로 스키마 변경을
 * 제안한다(HANDOFF 문서에 기록).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NaverV2LoginService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String LOGIN_ID_PREFIX = "naver2_"; // V1의 "naver_"와 절대 겹치지 않는 접두사

  private final UserRepository userRepository;
  private final SocialAccountRepository socialAccountRepository;
  private final PasswordEncoder passwordEncoder;

  public record LoginResult(LoginResponse user, boolean created) {}

  @Transactional
  public LoginResult loginOrRegister(NaverV2Profile profile) {
    if (profile == null || profile.providerUserId() == null || profile.providerUserId().isBlank()) {
      throw new IllegalArgumentException("네이버 프로필에서 stable user id를 확인할 수 없습니다.");
    }
    String providerUserId = profile.providerUserId();
    Optional<SocialAccount> existing =
        socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, providerUserId);
    if (existing.isPresent()) {
      User user = userRepository.findById(existing.get().getUserId())
          .orElseThrow(() -> new IllegalStateException("연결된 회원을 찾을 수 없습니다."));
      return new LoginResult(LoginResponse.from(user), false);
    }
    return new LoginResult(createUser(providerUserId, profile), true);
  }

  private LoginResponse createUser(String providerUserId, NaverV2Profile profile) {
    String loginId = uniqueLoginId(providerUserId);
    try {
      AuditorContextHolder.set(loginId);
      User user = User.builder()
          .loginId(loginId)
          .password(passwordEncoder.encode(randomSecret()))
          .name(resolveName(profile))
          .nickname(resolveNickname(profile, providerUserId))
          .email(profile.email())
          .role(Role.STUDENT) // 위 클래스 Javadoc 참고 — 의미 부여 아닌 기술적 placeholder
          .build();
      userRepository.save(user);
      // 동시 콜백으로 (provider, provider_user_id) unique 충돌 시 트랜잭션 전체 rollback —
      // 부분 상태를 남기지 않는 편이 안전하므로 여기서 흡수하지 않는다.
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

  /** "naver2_" + sha256(providerUserId) 앞 16hex — 재현 가능하고 50자 제한 안에 든다. */
  private String uniqueLoginId(String providerUserId) {
    String base = LOGIN_ID_PREFIX + sha256Hex(providerUserId).substring(0, 16);
    if (!userRepository.existsByLoginId(base)) return base;
    for (int i = 0; i < 5; i++) {
      String candidate = base + "_" + suffix();
      if (!userRepository.existsByLoginId(candidate)) return candidate;
    }
    throw new IllegalStateException("NewStart 회원용 아이디를 발급할 수 없습니다.");
  }

  private String resolveName(NaverV2Profile profile) {
    String name = profile.name() != null ? profile.name() : profile.nickname();
    if (name == null) return "네이버회원";
    return name.length() > 30 ? name.substring(0, 30) : name;
  }

  /** users.nickname NOT NULL + 프론트 닉네임 문자열 제약을 우회하지 않도록 최소 정제만 한다. */
  private String resolveNickname(NaverV2Profile profile, String providerUserId) {
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
