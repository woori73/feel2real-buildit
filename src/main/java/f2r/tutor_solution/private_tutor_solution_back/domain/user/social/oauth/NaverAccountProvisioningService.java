package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * NAVER 최초 로그인 시 User + SocialAccount를 한 트랜잭션에서 생성한다.
 *
 * <p>별도 스프링 빈으로 분리한 이유는 self-invocation 프록시 우회 문제 때문이다 — 같은 클래스 안에서
 * {@code this.provision(...)}를 호출하면 {@code @Transactional} 프록시가 걸리지 않아 롤백 경계가
 * 깨진다. {@link NaverAccountResolutionService}가 별도 빈인 이 서비스를 호출해야 실제 트랜잭션
 * 프록시를 통과하고, {@code (provider, provider_user_id)} unique 위반(V982) 시 User insert까지
 * 통째로 롤백된다(동시 최초 로그인에서 고아 User가 남지 않도록).
 */
@Service
class NaverAccountProvisioningService {

  /** NAVER 프로필에 이름/닉네임이 전혀 없을 때 쓰는 값 — 실제 개인정보를 추정/생성하지 않는다. */
  private static final String FALLBACK_DISPLAY_NAME = "네이버 회원";
  private static final int NAME_MAX_LENGTH = 30;
  private static final int NICKNAME_MAX_LENGTH = 50;

  private final UserRepository userRepository;
  private final SocialAccountRepository socialAccountRepository;
  private final PasswordEncoder passwordEncoder;

  NaverAccountProvisioningService(
      UserRepository userRepository,
      SocialAccountRepository socialAccountRepository,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.socialAccountRepository = socialAccountRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * @throws org.springframework.dao.DataIntegrityViolationException 동시 최초 로그인 race에서
   *     진 쪽 — 호출자가 {@code (NAVER, providerUserId)}를 다시 조회해 이긴 쪽 User로 합류해야 한다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  User provision(NaverProfileIdentity identity) {
    String syntheticLoginId = deriveSyntheticLoginId(identity.providerUserId());
    try {
      AuditorContextHolder.set(syntheticLoginId);

      User user = User.builder()
          .loginId(syntheticLoginId)
          // NAVER 계정은 ID/PW 로그인을 쓰지 않는다 — 아무에게도 전달되지 않는 랜덤 값으로 채워
          // users.password NOT NULL만 만족시키고 비밀번호 로그인 자체를 구조적으로 불가능하게 한다.
          .password(passwordEncoder.encode(UUID.randomUUID().toString()))
          .name(truncate(firstNonBlank(identity.name(), identity.nickname(), FALLBACK_DISPLAY_NAME), NAME_MAX_LENGTH))
          // nickname이 비어 있으면 User 생성자 자체가 loginId로 폴백한다(기존 동작, 여기서 재구현 안 함).
          .nickname(truncate(identity.nickname(), NICKNAME_MAX_LENGTH))
          .email(identity.email())   // consent 미제공 시 null 허용(schema nullable) — 가짜 이메일 생성 안 함
          .phone(identity.mobile())  // consent 미제공 시 null 허용(schema nullable)
          .role(Role.STUDENT)        // Role enum에 별도 "일반 회원"이 없다 — 기존 자기가입 학생과 동일한 최소권한 role
          .build();
      userRepository.saveAndFlush(user);

      SocialAccount socialAccount = SocialAccount.builder()
          .userId(user.getUserId())
          .provider(SocialProvider.NAVER)
          .providerUserId(identity.providerUserId())
          .build();
      // 여기서 (provider, provider_user_id) unique 위반이 나면(V982) REQUIRES_NEW 트랜잭션 전체가
      // 롤백되어 방금 만든 User도 함께 사라진다 — 고아 User가 남지 않는다.
      socialAccountRepository.saveAndFlush(socialAccount);

      return user;
    } finally {
      AuditorContextHolder.clear();
    }
  }

  /**
   * users.login_id는 NOT NULL + UNIQUE(길이 50)다. response.id 원문을 그대로 쓰지 않는 이유는
   * SocialAccount.provider_user_id는 길이 64까지 허용하는데 반해 이 컬럼은 50이라 아주 긴 provider id가
   * 들어오면 넘칠 수 있기 때문이다 — 고정 길이 해시로 결정적으로 축약해 항상 안전하게 맞춘다.
   * 이 값은 화면에 노출되지 않고, 회원 재식별은 항상 SocialAccountRepository로만 한다(이 값으로 하지 않음).
   */
  private String deriveSyntheticLoginId(String providerUserId) {
    return "naver_" + sha256Hex(providerUserId).substring(0, 32);
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
    }
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
  }
}
