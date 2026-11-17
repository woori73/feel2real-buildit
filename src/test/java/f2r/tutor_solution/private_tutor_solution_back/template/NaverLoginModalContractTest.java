package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("공용 로그인 창 — 네이버 로그인 버튼")
class NaverLoginModalContractTest {

  private static final Path ROOT = Path.of("src/main/resources");

  @Test
  @DisplayName("given 공용 로그인 창 when 읽으면 then 빈 자리 대신 네이버 로그인 버튼·기존 회원 안내·스크립트가 있다")
  void loginModalHasNaverButton() throws Exception {
    // given
    String html = Files.readString(ROOT.resolve("templates/fragments/_account_menu.html"));
    String css = Files.readString(ROOT.resolve("static/css/account_menu.css"));

    // when & then
    assertThat(html).contains("data-naver-login", "href=\"/auth/naver/start\"", "네이버 로그인",
        "/js/naver_login.js", "이미 아이디로 가입하셨다면");
    assertThat(html).doesNotContain("login-modal-social-placeholder", "간편 로그인 준비 중");
    assertThat(css).contains(".login-modal-naver", "#03c75a");
  }

  @Test
  @DisplayName("given 회원가입 화면 when 읽으면 then 네이버 간편 가입 버튼·복귀 주소 전달·스크립트가 있다")
  void registerPageHasNaverSignup() throws Exception {
    // given
    String html = Files.readString(ROOT.resolve("templates/auth/register.html"));

    // when & then
    assertThat(html).contains("data-naver-login", "data-naver-role=\"tutor\"", "네이버로 강사 간편 가입",
        "네이버로 학생 간편 가입", "/js/naver_login.js", "window.__f2rLoginReturnUrl = registerReturnUrl");
  }
}
