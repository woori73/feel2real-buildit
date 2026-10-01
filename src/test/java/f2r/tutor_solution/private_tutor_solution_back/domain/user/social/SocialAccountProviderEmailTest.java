package f2r.tutor_solution.private_tutor_solution_back.domain.user.social;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SocialAccount.providerEmail — 로그인마다 갱신, 미제공 로그인은 기존 값을 지우지 않는다")
class SocialAccountProviderEmailTest {

  private static SocialAccount account(String email) {
    return SocialAccount.builder().userId(1L).provider(SocialProvider.NAVER).providerUserId("subj").providerEmail(email).build();
  }

  @Test
  void normalizesOnCreate_andRefreshesOnChange() {
    SocialAccount a = account("  SSGW1234@Naver.com ");
    assertThat(a.getProviderEmail()).isEqualTo("ssgw1234@naver.com");

    assertThat(a.refreshProviderEmail("ssgw1234@naver.com")).isFalse();     // 같은 값 — 저장 불필요
    assertThat(a.refreshProviderEmail("New1234@naver.com")).isTrue();       // 이메일 변경 반영
    assertThat(a.getProviderEmail()).isEqualTo("new1234@naver.com");
  }

  @Test
  void missingEmailAtLogin_doesNotWipeStoredValue_andLateConsentIsPickedUp() {
    SocialAccount a = account(null);                                        // 동의 이전 가입자: 비어 있음
    assertThat(a.getProviderEmail()).isNull();
    assertThat(a.refreshProviderEmail(null)).isFalse();
    assertThat(a.refreshProviderEmail("   ")).isFalse();

    assertThat(a.refreshProviderEmail("late1234@naver.com")).isTrue();      // 이후 로그인에서 동의 → 채워진다
    assertThat(a.refreshProviderEmail(null)).isFalse();                     // 다음 로그인에 안 와도 지우지 않는다
    assertThat(a.getProviderEmail()).isEqualTo("late1234@naver.com");
  }
}
