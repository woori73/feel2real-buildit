package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("/dashboard 관리자 현황 — ADMIN만 보이고 일반 화면은 그대로")
class AdminDashboardOverviewContractTest {

  private static final Path ROOT = Path.of("src/main/resources");

  private static String read(String rel) throws Exception {
    return Files.readString(ROOT.resolve(rel));
  }

  @Test
  @DisplayName("given 대시보드 when 읽으면 then 관리자 현황 칸·버튼·AI 의논 칸은 처음엔 숨겨져 있다(ADMIN 확인 후에만 보임)")
  void adminPartsStartHidden() throws Exception {
    // given
    String html = read("templates/dashboard.html");
    // when / then
    assertThat(html).contains("id=\"adminOverview\" data-pane=\"admin\" data-admin-only hidden");
    assertThat(html).contains("data-view-btn=\"admin\" aria-pressed=\"false\" data-admin-only hidden");
    assertThat(html).contains("<div class=\"adm-discuss\" id=\"admDiscuss\" data-admin-only hidden>");
    assertThat(html).contains("/js/admin_overview_view.js", "/js/admin_overview.js", "/css/admin_overview.css");
  }

  @Test
  @DisplayName("given 대시보드 when 읽으면 then 기존 일반 화면(AI 대화·전체 대시보드·메뉴 링크)은 그대로 남아 있다")
  void generalScreenUnchanged() throws Exception {
    // given
    String html = read("templates/dashboard.html");
    // when / then
    assertThat(html).contains("data-pane=\"ai\"", "data-pane=\"all\"", "id=\"uaCommand\"",
        "/academycraft/screens/MBR-01-student-list.html", "AI 업무 지휘창");
  }

  @Test
  @DisplayName("given 관리자 현황 스크립트 when 읽으면 then ADMIN이 아니면 멈추고, 예시는 입력칸만 채우며 보내지 않는다")
  void scriptGuardsRoleAndNeverSends() throws Exception {
    // given
    String js = read("static/js/admin_overview.js");
    // when / then
    assertThat(js).contains("user.role !== 'ADMIN'", "/api/admin/easy/attendance/student-overview");
    assertThat(js).doesNotContain("requestSubmit", ".submit(");
  }

  @Test
  @DisplayName("given 관리자 현황 CSS when 읽으면 then 모든 화면 배치 규칙이 관리자 화면(data-view=admin) 안에서만 바뀐다")
  void cssScopedToAdminView() throws Exception {
    // given
    String css = read("static/css/admin_overview.css");
    // when / then
    for (String line : css.split("\n")) {
      String t = line.trim();
      if (t.startsWith("body") && !t.startsWith("body[data-view=\"admin\"]") && !t.startsWith("body:not([data-view=\"admin\"])")) {
        assertThat(t).as("관리자 화면 밖 규칙").isEmpty();
      }
    }
  }
}
