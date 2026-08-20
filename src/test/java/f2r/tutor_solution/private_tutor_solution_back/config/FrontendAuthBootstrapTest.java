package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FrontendAuthBootstrapTest {

  @Test
  @DisplayName("공통 API 스크립트는 JWT 쿠키 세션을 UI 캐시로만 복원한다")
  void api_script_restores_cookie_session_without_dashboard_redirect() throws Exception {
    // given
    String script = readClasspathText("static/js/api.js");

    // when
    boolean exposesBootstrapFunction = script.contains("window.redirectAuthenticatedUser = redirectAuthenticatedUser");

    // then
    assertThat(exposesBootstrapFunction).isTrue();
    assertThat(script).contains("fetch(archiveAwareUrl('/api/auth/me'), { credentials: 'same-origin' })");
    assertThat(script).contains("if (role === 'STUDENT') return '/stdt_dashboard';");
    assertThat(script).contains("if (role === 'ADMIN') return '/admin/students';");
    assertThat(script).contains("return '/tutor/academy';");
    assertThat(script).contains("sessionStorage.setItem('currentUser', JSON.stringify(user));");
    assertThat(script).doesNotContain("location.replace(dashboardPathForRole(user.role));");
  }

  @Test
  @DisplayName("홈은 로그인 여부와 무관하게 접근 가능한 최종 landing으로 남는다")
  void home_does_not_auto_bounce_to_role_dashboard() throws Exception {
    // given
    String home = readClasspathText("templates/public/home.html");

    // when
    boolean homeLoadsApiScript = home.contains("<script src=\"/js/api.js\"></script>");

    // then
    assertThat(homeLoadsApiScript).isTrue();
    assertThat(home).doesNotContain("redirectAuthenticatedUser()");
    assertThat(home).contains("openLoginModal()");
  }

  @Test
  @DisplayName("계정 메뉴 스크립트는 로그인 모달과 안전한 내부 returnUrl helper를 노출한다")
  void account_menu_exposes_modal_and_safe_return_url_helper() throws Exception {
    // given
    String script = readClasspathText("static/js/account_menu.js");
    String returnUrlScript = readClasspathText("static/js/return_url.js");

    // when & then
    assertThat(returnUrlScript).contains("window.safeInternalReturnUrl = safeInternalReturnUrl");
    assertThat(script).contains("window.openLoginModal = openLoginModal");
    assertThat(returnUrlScript).contains("decodeURIComponent(raw)");
    assertThat(returnUrlScript).contains("raw.startsWith('//')");
    assertThat(returnUrlScript).contains("raw.replace(/\\\\/g, '/')");
    assertThat(script).contains("return { href: '/tutor/academy', label: '강사 홈' }");
    assertThat(script).contains("return { href: '/admin/students', label: '어드민 홈' }");
  }

  @Test
  @DisplayName("공개 작성 액션은 인증 실패를 현재 페이지에서 직접 처리할 수 있다")
  void fetch_auth_supports_manual_public_auth_handling() throws Exception {
    // given
    String apiScript = readClasspathText("static/js/api.js");
    String communityNew = readClasspathText("static/js/community_new.js");
    String communityPost = readClasspathText("static/js/community_post.js");
    String lessonCommunity = readClasspathText("static/js/lesson_community.js");

    // when & then
    assertThat(apiScript).contains("const redirectOnAuthError = requestOpts.redirectOnAuthError !== false;");
    assertThat(apiScript).contains("if (!redirectOnAuthError) return res;");
    assertThat(communityNew).contains("redirectOnAuthError: false");
    assertThat(communityPost).contains("redirectOnAuthError = false");
    assertThat(communityPost).contains("redirectOnAuthError: false");
    assertThat(communityPost).contains("window.addEventListener('f2r:login'");
    assertThat(communityPost).contains("revealAnswerComposer()");
    assertThat(lessonCommunity).contains("window.addEventListener('f2r:login', init)");
  }

  private String readClasspathText(String path) throws Exception {
    return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
  }
}
