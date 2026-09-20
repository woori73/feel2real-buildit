package f2r.tutor_solution.private_tutor_solution_back.config.security.oauth2;

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
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NewStart 네이버 로그인 — 공식 Spring Security OAuth2 Client 표준 흐름의 profile 조회/User 연결
 * 지점. token 교환·state 검증·callback 라우팅은 전부 Spring Security 프레임워크가 처리하고, 이
 * 클래스는 {@link DefaultOAuth2UserService}로 프로필을 받아온 뒤 (a) 네이버 응답의 "response"
 * 래퍼를 풀고 (b) 기존 {@code User}/{@code SocialAccount} 저장소에 연결하는 것만 한다.
 *
 * <p>내부 회원/인증 기반({@link User}, {@link UserRepository}, {@link SocialAccount},
 * {@link SocialAccountRepository})은 V1/V2와 동일하게 재사용한다 — 같은 (provider=NAVER,
 * providerUserId) 매핑 테이블을 쓰므로 V1/V2로 이미 연결된 네이버 계정이면 이 경로도 같은 User를
 * 찾아 재사용한다(중복 가입 없음). ROLE 전략은 V1/V2와 동일한 이유로 {@link Role#STUDENT}를
 * 의미 부여 없는 기술적 placeholder로만 쓴다(users.role은 마이그레이션 없이 확장 불가한
 * MySQL 네이티브 ENUM — 상세 이유는 V2 HANDOFF 문서 참고).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NaverOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

  private static final String LOGIN_ID_PREFIX = "naverstd_"; // V1(naver_)/V2(naver2_)와 겹치지 않음
  private static final SecureRandom RANDOM = new SecureRandom();

  private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
  private final UserRepository userRepository;
  private final SocialAccountRepository socialAccountRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User raw = delegate.loadUser(userRequest);
    log.info("NAVER_OAUTH2_PROFILE_FETCHED");
    return mapAndLink(raw.getAttributes());
  }

  /**
   * {@link #loadUser}에서 실제 네이버 호출(delegate) 이후 부분만 분리했다 — 이 메서드는 순수
   * 매핑/DB 연결 로직이라 실제 네트워크 호출 없이 단위테스트할 수 있다.
   */
  OAuth2User mapAndLink(Map<String, Object> rawAttributes) {
    Object responseObj = rawAttributes.get("response");
    if (!(responseObj instanceof Map<?, ?> profile)) {
      log.warn("NAVER_OAUTH2_PROFILE_INVALID reason=no_response_object");
      throw new OAuth2AuthenticationException(new OAuth2Error("naver_profile_invalid"));
    }
    Object idValue = profile.get("id");
    String providerUserId = idValue == null ? null : String.valueOf(idValue);
    if (providerUserId == null || providerUserId.isBlank()) {
      log.warn("NAVER_OAUTH2_PROFILE_INVALID reason=no_id");
      throw new OAuth2AuthenticationException(new OAuth2Error("naver_profile_no_id"));
    }

    LinkedUser linked = findOrCreateUser(providerUserId, profile);
    log.info(linked.created() ? "NAVER_OAUTH2_USER_CREATED" : "NAVER_OAUTH2_USER_FOUND");

    Map<String, Object> attributes = new LinkedHashMap<>();
    profile.forEach((k, v) -> attributes.put(String.valueOf(k), v));
    attributes.put("f2rUserId", linked.userId());
    attributes.put("f2rLoginId", linked.loginId());
    attributes.put("f2rRole", linked.role());

    return new DefaultOAuth2User(
        List.of(new SimpleGrantedAuthority("ROLE_" + linked.role())),
        attributes,
        "id");
  }

  record LinkedUser(Long userId, String loginId, String role, boolean created) {}

  private LinkedUser findOrCreateUser(String providerUserId, Map<?, ?> profile) {
    Optional<SocialAccount> existing =
        socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, providerUserId);
    if (existing.isPresent()) {
      User user = userRepository.findById(existing.get().getUserId())
          .orElseThrow(() -> new IllegalStateException("연결된 회원을 찾을 수 없습니다."));
      return new LinkedUser(user.getUserId(), user.getLoginId(), user.getRole().name(), false);
    }
    return createUser(providerUserId, profile);
  }

  private LinkedUser createUser(String providerUserId, Map<?, ?> profile) {
    String loginId = uniqueLoginId(providerUserId);
    try {
      AuditorContextHolder.set(loginId);
      User user = User.builder()
          .loginId(loginId)
          .password(passwordEncoder.encode(randomSecret()))
          .name(resolveName(profile))
          .nickname(resolveNickname(profile, providerUserId))
          .email(stringOrNull(profile.get("email")))
          .role(Role.STUDENT)
          .build();
      userRepository.save(user);
      socialAccountRepository.save(SocialAccount.builder()
          .userId(user.getUserId())
          .provider(SocialProvider.NAVER)
          .providerUserId(providerUserId)
          .build());
      return new LinkedUser(user.getUserId(), user.getLoginId(), Role.STUDENT.name(), true);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  private String uniqueLoginId(String providerUserId) {
    String base = LOGIN_ID_PREFIX + sha256Hex(providerUserId).substring(0, 16);
    if (!userRepository.existsByLoginId(base)) return base;
    for (int i = 0; i < 5; i++) {
      String candidate = base + "_" + suffix();
      if (!userRepository.existsByLoginId(candidate)) return candidate;
    }
    throw new IllegalStateException("NewStart 회원용 아이디를 발급할 수 없습니다.");
  }

  private String resolveName(Map<?, ?> profile) {
    String name = stringOrNull(profile.get("name"));
    if (name == null) name = stringOrNull(profile.get("nickname"));
    if (name == null) return "네이버회원";
    return name.length() > 30 ? name.substring(0, 30) : name;
  }

  private String resolveNickname(Map<?, ?> profile, String providerUserId) {
    String candidate = stringOrNull(profile.get("nickname"));
    if (candidate == null) candidate = stringOrNull(profile.get("name"));
    if (candidate != null) {
      String cleaned = candidate.trim().replaceAll("[^\\p{IsHangul}A-Za-z0-9 _-]", "").trim();
      if (cleaned.length() >= 2) {
        return cleaned.length() > 20 ? cleaned.substring(0, 20) : cleaned;
      }
    }
    String tail = sha256Hex(providerUserId).substring(0, 4);
    return "네이버" + tail;
  }

  private static String stringOrNull(Object value) {
    if (value == null) return null;
    String s = String.valueOf(value).trim();
    return s.isEmpty() ? null : s;
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
