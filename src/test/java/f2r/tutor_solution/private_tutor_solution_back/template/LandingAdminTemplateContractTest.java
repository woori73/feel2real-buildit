package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("랜딩 페이지 관리자 UI 계약")
class LandingAdminTemplateContractTest {

  private static final Path TEMPLATE =
      Path.of("src/main/resources/templates/admin/landing.html");
  private static final Path TOPNAV =
      Path.of("src/main/resources/templates/fragments/_admin_topnav.html");
  private static final Path SCRIPT =
      Path.of("src/main/resources/static/js/admin_landing.js");
  private static final Path STYLE =
      Path.of("src/main/resources/static/css/admin_landing.css");

  @Test
  @DisplayName("given 관리자 템플릿 when 읽기 then active nav·3슬롯 host·접근성 피드백을 제공한다")
  void admin_template_has_page_and_accessibility_contract() throws Exception {
    // given
    String html = Files.readString(TEMPLATE);
    String nav = Files.readString(TOPNAV);

    // when & then
    assertThat(html)
        .contains("topnav('landing')")
        .contains("id=\"landingVideoCards\"")
        .contains("60초 미만")
        .doesNotContain("60초 이하")
        .contains("aria-live=\"polite\"")
        .contains("aria-busy=\"true\"")
        .contains("role=\"status\"")
        .contains("/css/admin_landing.css")
        .containsSubsequence("/js/api.js", "/js/admin_landing.js")
        .doesNotContain("data-delete", ">삭제<");
    assertThat(nav)
        .contains("activeMenu == 'landing'")
        .contains("href=\"/admin/landing\"")
        .contains("랜딩 페이지 관리");
  }

  @Test
  @DisplayName("given 관리자 JS when 읽기 then 정확한 API·3슬롯·교체 확인·가드를 사용한다")
  void admin_script_uses_exact_management_contract() throws Exception {
    // given
    String script = Files.readString(SCRIPT);

    // when & then
    assertThat(script)
        .contains("const API_BASE = '/api/admin/landing/videos'")
        .contains("['HERO_BACKGROUND', 'ACADEMY_SHORTS_DEMO', 'PARENT_REPORT_DEMO']")
        .contains("지원하지 않는 영상 sourceType입니다.")
        .contains("60초 미만")
        .doesNotContain("60초 이하")
        .contains("await ensureCurrentUser()")
        .contains("currentUser.role !== 'ADMIN'")
        .contains("window.confirm(")
        .contains("body.set('video', file)")
        .contains("method: 'PUT'")
        .contains("`${API_BASE}/${slot}/visibility`")
        .contains("method: 'PATCH'")
        .contains("JSON.stringify({ visible: toggle.checked })")
        .contains("aria-busy")
        .contains("role=\"status\"")
        .contains("role', 'alert'")
        .doesNotContain("method: 'DELETE'")
        .doesNotContain("/delete");
  }

  @Test
  @DisplayName("given 관리자 스타일 when 읽기 then 3카드·busy·모바일 상태를 표현한다")
  void admin_style_supports_cards_and_feedback_states() throws Exception {
    // given
    String css = Files.readString(STYLE);

    // when & then
    assertThat(css)
        .contains("grid-template-columns: repeat(3, minmax(0, 1fr))")
        .contains(".landing-video-card[aria-busy=\"true\"]")
        .contains(".landing-video-message.is-error")
        .contains("@media (max-width: 960px)")
        .contains("@media (max-width: 680px)");
  }
}
