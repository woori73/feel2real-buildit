package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * ADMIN topnav "새출발" 메뉴 + 새출발 관리 화면 계약. 관리자 화면 전체가 공유하는
 * {@code _admin_topnav} fragment에 새 메뉴가 기존 패턴(admin-nav-link + activeMenu)으로
 * 추가됐는지, 새출발 관리 화면이 기존 ADMIN 레이아웃을 재사용하는지 실제 Thymeleaf 렌더로 검증한다.
 */
@DisplayName("ADMIN 새출발 진입 계약")
class AdminNewStartNavContractTest {

  private static TemplateEngine engine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.addDialect(new LayoutDialect());
    return engine;
  }

  private static String render(String view, String activeMenu) {
    Context context = new Context();
    context.setVariable("activeMenu", activeMenu);
    return engine().process(view, context);
  }

  @Test
  @DisplayName("ADMIN topnav에 새출발 메뉴가 기존 패턴으로 존재하고 /admin/newstart를 가리킨다")
  void topnavHasNewStartMenu() {
    String html = render("admin/dashboard", "dashboard");

    assertThat(html).contains("href=\"/admin/newstart\"");
    assertThat(html).contains("새출발");
    assertThat(html).contains("admin-nav-link");
    // 기존 ADMIN 메뉴 회귀 방지 — 기존 링크 전부 유지
    assertThat(html).contains("href=\"/admin/students\"");
    assertThat(html).contains("href=\"/admin/members\"");
    assertThat(html).contains("href=\"/admin/landing\"");
    assertThat(html).contains("href=\"/easy\"");
  }

  @Test
  @DisplayName("새출발 관리 화면이 기존 ADMIN 레이아웃으로 렌더되고 최소 구성을 갖는다")
  void newstartAdminPageRenders() {
    String html = render("admin/newstart", "newstart");

    assertThat(html).contains("새출발 관리");
    assertThat(html).contains("href=\"/newstart/chat\"");
    assertThat(html).contains("회원 채팅");
    assertThat(html).contains("href=\"/newstart\"");
    assertThat(html).contains("서비스 화면 보기");
    assertThat(html).contains("admin-nav");           // 공통 topnav 재사용
    assertThat(html).doesNotContain("th:replace");    // fragment 치환 완료
  }
}
