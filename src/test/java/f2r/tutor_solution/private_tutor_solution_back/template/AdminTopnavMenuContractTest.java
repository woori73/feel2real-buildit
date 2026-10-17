package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ADMIN 상단 메뉴 — 노출만 정리하고 기능·URL은 유지")
class AdminTopnavMenuContractTest {

  private static final Path ROOT = Path.of("src/main");

  private static String read(String rel) throws Exception {
    return Files.readString(ROOT.resolve(rel));
  }

  @Test
  @DisplayName("given 상단 메뉴 when 읽으면 then 시험 분석 자료 관리·모의고사 원본·분석 자료 링크는 없다")
  void removedMenusAreNotExposed() throws Exception {
    // given
    String nav = read("resources/templates/fragments/_admin_topnav.html");
    // when / then
    assertThat(nav).doesNotContain("href=\"/admin/analyzes\"", "href=\"/admin/wiki-originals\"", "href=\"/admin/wiki-analysis\"");
    assertThat(nav).doesNotContain("시험 분석 자료 관리", "모의고사 원본", ">분석 자료<");
  }

  @Test
  @DisplayName("given 제거한 메뉴 when 라우트·화면을 보면 then URL과 템플릿은 그대로 남아 있다")
  void removedMenusKeepRoutesAndPages() throws Exception {
    // given
    String mvc = read("java/f2r/tutor_solution/private_tutor_solution_back/config/WebMvcConfig.java");
    // when / then
    assertThat(mvc).contains("\"/admin/analyzes\"", "\"/admin/wiki-originals\"", "\"/admin/wiki-analysis\"");
    for (String page : new String[] {"analyzes", "wiki_originals", "wiki_analysis"}) {
      assertThat(Files.exists(ROOT.resolve("resources/templates/admin/" + page + ".html"))).as(page).isTrue();
    }
  }

  @Test
  @DisplayName("given 상단 메뉴 when 읽으면 then 서비스 6개와 기존 ADMIN 메뉴는 유지되고 AcademyCraft 가짜 링크는 없다")
  void keepsServiceAndAdminMenus() throws Exception {
    // given
    String nav = read("resources/templates/fragments/_admin_topnav.html");
    // when / then
    for (String href : new String[] {"/easy", "/wemade", "/choonggi/admin", "/builder", "/problem-generator", "/testprep",
        "/admin/hub", "/admin/students", "/admin/members", "/admin/analytics", "/admin/academies", "/admin/landing"}) {
      assertThat(nav).contains("href=\"" + href + "\"");
    }
    assertThat(nav).doesNotContain("href=\"/lms\"").doesNotContain(">AcademyCraft<");
  }
}
