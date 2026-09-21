package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * {@link NaverOAuth2UserService}가 언랩한 {@link OAuth2User}에서 뽑아낸 순수 값 객체.
 *
 * <p>identity 기준은 {@code providerUserId}(= 네이버 {@code response.id}) 하나뿐이다. email/name/
 * nickname/mobile은 전부 부가 profile일 뿐이며, 어느 것도 없어도 provider identity 자체는
 * 성립한다(docs/newstart/oauth/NAVER_LOGIN_REBUILD_DESIGN_2026-09-21.md §PHASE 2/5/6).
 */
public record NaverProfileIdentity(
    String providerUserId,
    String email,
    String name,
    String nickname,
    String mobile) {

  public static NaverProfileIdentity from(OAuth2User oauth2User) {
    Object idAttr = oauth2User.getAttribute(NaverProfileParser.ID_KEY);
    String providerUserId = idAttr == null ? null : String.valueOf(idAttr);
    if (providerUserId == null || providerUserId.isBlank()) {
      // NaverOAuth2UserService가 이미 response.id 존재를 보장하므로 정상 경로에서는 도달하지 않는다.
      throw new IllegalStateException("NaverProfileIdentity requires a non-blank providerUserId");
    }
    return new NaverProfileIdentity(
        providerUserId,
        stringAttribute(oauth2User, "email"),
        stringAttribute(oauth2User, "name"),
        stringAttribute(oauth2User, "nickname"),
        stringAttribute(oauth2User, "mobile"));
  }

  private static String stringAttribute(OAuth2User oauth2User, String key) {
    Object value = oauth2User.getAttribute(key);
    return value == null ? null : String.valueOf(value);
  }
}
