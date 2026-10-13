package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Easy 회계 화면 계약 — ADMIN SSR 셸 + JS 렌더.
 * 사업체명은 API로만 내려받고 템플릿에 하드코딩하지 않는다.
 * 목록은 표가 아니라 카드형(.item)이라 375px에서 깨지지 않는다.
 */
class EasyTemplateContractTest {

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

  private static String readStatic(String name) throws IOException {
    return Files.readString(
        Path.of("src/main/resources/static/" + name), StandardCharsets.UTF_8);
  }

  private static String readTemplate(String view) throws IOException {
    return Files.readString(
        Path.of("src/main/resources/templates/" + view + ".html"), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("Workspace 7개 화면용 6개 템플릿이 topnav·easy 리소스까지 실제로 렌더된다")
  void templates_render() {
    TemplateEngine engine = engine();
    for (String view : new String[] {
        "easy/dashboard", "easy/transactions", "easy/students", "easy/payments",
        "easy/reviews", "easy/accounting"}) {
      String html = engine.process(view, new Context());
      assertThat(html)
          .as(view)
          .contains("<!DOCTYPE html>")
          .contains("width=device-width, initial-scale=1.0")
          .contains("/css/easy.css")
          .contains("ADMIN")
          .doesNotContain("th:block", "th:replace", "layout:fragment");
    }
  }

  @Test
  @DisplayName("대시보드는 사업체 전환·5카드·경영비교·검토큐·브리핑·최근거래·미수 영역을 갖는다")
  void dashboard_structure() {
    String html = engine().process("easy/dashboard", new Context());
    assertThat(html)
        .contains("id=\"bizSwitch\"")
        .contains("id=\"statGrid\"")
        .contains("id=\"yoyGrid\"")
        .contains("id=\"yearPlGrid\"")
        .contains("id=\"reviewGrid\"")
        .contains("id=\"workspaceStatusGrid\"")
        .contains("href=\"/easy/reviews\"")
        .contains("id=\"briefList\"")
        .contains("id=\"taxPreview\"")
        .contains("id=\"recentList\"")
        .contains("id=\"overdueList\"")
        .contains("/js/easy_dashboard.js")
        .contains("/js/easy_insight.js");
  }

  @Test
  @DisplayName("사업체명은 템플릿·JS에 하드코딩하지 않는다")
  void noHardcodedBusinessNames() throws IOException {
    for (String view : new String[] {
        "easy/dashboard", "easy/transactions", "easy/students", "easy/payments"}) {
      assertThat(readTemplate(view))
          .as(view)
          .contains("fragments/_admin_topnav :: topnav")
          .doesNotContain("압구정에듀홀릭");
    }
    for (String js : new String[] {
        "js/easy_api.js", "js/easy_dashboard.js", "js/easy_insight.js",
        "js/easy_transactions.js", "js/easy_students.js", "js/easy_payments.js"}) {
      assertThat(readStatic(js)).as(js).doesNotContain("압구정에듀홀릭");
    }
  }

  @Test
  @DisplayName("JS는 ADMIN API만 호출하고 Workspace 거래 화면은 물리 삭제를 노출하지 않는다")
  void js_usesAdminApi_andWorkspaceDoesNotExposeDelete() throws IOException {
    for (String js : new String[] {
        "js/easy_dashboard.js", "js/easy_insight.js", "js/easy_transactions.js",
        "js/easy_students.js", "js/easy_payments.js", "js/easy_import.js"}) {
      assertThat(readStatic(js)).as(js).contains("/api/admin/easy/");
    }
    assertThat(readStatic("js/easy_transactions.js"))
        .contains("/api/admin/easy/workspace/transactions")
        .doesNotContain("method: 'DELETE'", "정말 삭제할까요?");
    assertThat(readStatic("js/easy_payments.js")).contains("confirm(");
    assertThat(readStatic("js/easy_import.js"))
        .contains("/api/admin/easy/imports")
        .contains("confirm(");
  }

  @Test
  @DisplayName("검토함의 승인·보류는 businessId가 포함된 Workspace API를 호출한다")
  void workspaceReviewActions_areBusinessScoped() throws IOException {
    String js = readStatic("js/easy_reviews.js");
    assertThat(js)
        .contains("/api/admin/easy/workspace/reviews/")
        .contains("/approve?businessId=")
        .contains("/hold?businessId=")
        .contains("method: 'POST'");
  }

  @Test
  @DisplayName("거래 화면은 수집·가져오기·장부·분개 상태를 구분한다")
  void workspaceTransactionStatuses_areDistinct() throws IOException {
    String html = readTemplate("easy/transactions");
    String js = readStatic("js/easy_transactions.js");
    assertThat(html).contains("IMPORT STAGED", "IMPORTED TO LEDGER", "JOURNAL POSTED");
    assertThat(js).contains("collectionStatus", "importStatus", "ledgerStatus", "journalStatus");
  }

  @Test
  @DisplayName("거래 화면은 내보내기·staging·계정·증빙 요소를 갖는다")
  void transactions_importElements() throws IOException {
    String html = readTemplate("easy/transactions");
    assertThat(html)
        .contains("/js/easy_import.js")
        .contains("id=\"btnExport\"")
        .contains("id=\"impFile\"")
        .contains("id=\"fAccount\"")
        .contains("id=\"fEvidence\"")
        .contains("id=\"fSupply\"");
  }

  @Test
  @DisplayName("easy.css는 모바일 카드 우선 + PC 확장 브레이크포인트를 갖는다")
  void css_mobileFirst() throws IOException {
    String css = readStatic("css/easy.css");
    assertThat(css)
        .contains(".stat-grid")
        .contains(".item")
        .contains("grid-template-columns: 1fr 1fr")
        .contains("@media (min-width: 768px)");
  }
}
