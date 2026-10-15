package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

@DisplayName("NaverProfileIdentity")
class NaverProfileIdentityTest {

  private static OAuth2User oauth2User(Map<String, Object> unwrappedResponseAttributes, String nameAttributeKey) {
    return new DefaultOAuth2User(
        List.of(new SimpleGrantedAuthority("ROLE_USER")), unwrappedResponseAttributes, nameAttributeKey);
  }

  @Test
  @DisplayName("response.id가 있으면 identity를 만들고, email/name/nickname/mobile은 있는 만큼만 채운다")
  void buildsIdentityFromUnwrappedAttributes() {
    NaverProfileIdentity identity = NaverProfileIdentity.from(oauth2User(Map.of(
        "id", "stable-id-1",
        "email", "user@example.com",
        "name", "홍길동",
        "nickname", "길동"), "id"));

    assertThat(identity.providerUserId()).isEqualTo("stable-id-1");
    assertThat(identity.email()).isEqualTo("user@example.com");
    assertThat(identity.name()).isEqualTo("홍길동");
    assertThat(identity.nickname()).isEqualTo("길동");
    assertThat(identity.mobile()).isNull();
  }

  @Test
  @DisplayName("id 하나만 있어도(email/name/nickname/mobile 전부 없음) identity는 성립한다")
  void buildsIdentityWithOnlyId() {
    NaverProfileIdentity identity = NaverProfileIdentity.from(oauth2User(Map.of("id", "stable-id-2"), "id"));

    assertThat(identity.providerUserId()).isEqualTo("stable-id-2");
    assertThat(identity.email()).isNull();
    assertThat(identity.name()).isNull();
    assertThat(identity.nickname()).isNull();
  }

  @Test
  @DisplayName("id가 없으면 실패한다 — 이 경로는 User/SocialAccount 생성 이전 단계다")
  void throwsWhenIdMissing() {
    assertThatThrownBy(() -> NaverProfileIdentity.from(
        oauth2User(Map.of("email", "user@example.com"), "email")))
        .isInstanceOf(IllegalStateException.class);
  }
}
