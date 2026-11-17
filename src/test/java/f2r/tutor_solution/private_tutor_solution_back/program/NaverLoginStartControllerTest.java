package f2r.tutor_solution.private_tutor_solution_back.program;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NaverLoginStartControllerTest {

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({
      "/,true",
      "/wiki,true",
      "/wiki/learning/7?tab=a,true",
      "/admissions/guide,true",
      "/designcraft/editor,true",
      "/admin/dashboard,false",
      "/admin,false",
      "/api/me,false",
      "/register,false",
      "/signin/reset/password,false",
      "/account/password-change,false",
      "/oauth2/authorization/naver,false",
      "https://evil.com,false",
      "//evil.com,false",
      "/wiki/../admin,false",
      "/\\evil.com,false"
  })
  void isAllowedReturnUrl(String url, boolean expected) {
    // given & when
    boolean allowed = NaverLoginStartController.isAllowedReturnUrl(url);

    // then
    assertThat(allowed).isEqualTo(expected);
  }

  @org.junit.jupiter.api.Test
  void isAllowedReturnUrl_nullOrBlank() {
    // given & when & then
    assertThat(NaverLoginStartController.isAllowedReturnUrl(null)).isFalse();
    assertThat(NaverLoginStartController.isAllowedReturnUrl("")).isFalse();
  }
}
