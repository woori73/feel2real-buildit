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

/** 홈 3D UI 계약: 기존 내용·링크 보존, 3D는 선택적 강화(정적 대체·JS 없음 대비), 서비스 카드의 실제 경로. DB 없이 렌더한다. */
class LandingF3dTemplateContractTest {

  private static final Path STATIC = Path.of("src/main/resources/static");

  private static String render() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.addDialect(new LayoutDialect());
    return engine.process("public/landing", new Context());
  }

  private static String read(String relative) throws IOException {
    return Files.readString(STATIC.resolve(relative), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 3D 무대·보석 정적 대체(SVG)·캔버스가 있고 장식 요소는 스크린리더에서 제외된다")
  void hero_hasStageAndStaticFallback() {
    // given / when
    String html = render();
    // then
    assertThat(html)
        .contains("data-f3d-stage")
        .contains("id=\"f3dCore\"")
        .contains("class=\"f3d-core\" id=\"f3dCore\" aria-hidden=\"true\"")
        .contains("f3d-core-fallback")
        .contains("<canvas></canvas>")
        .contains("class=\"f3d-bg\" aria-hidden=\"true\"");
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 기존 오퍼·H1·CTA ID가 유지되고 H1은 하나뿐이다")
  void hero_keepsOfferAndSingleH1() {
    // given / when
    String html = render();
    // then
    assertThat(html).contains("id=\"enOfferTitle\"").contains("id=\"enExamCta\"").contains("id=\"enExamMail\"")
        .contains("내신 영어 예상문제").contains("1회 20,000원");
    assertThat(html.split("<h1", -1).length - 1).isEqualTo(1);
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 히어로 서비스 바로가기 4개와 서비스 카드가 실제 운영 경로로 연결된다")
  void services_linkToRealRoutes() {
    // given / when
    String html = render();
    // then
    for (String href : new String[] {"/builder", "/academycraft", "/admissions/guide", "/wiki", "/dashboard", "/ledger"}) {
      assertThat(html).contains("href=\"" + href + "\"");
    }
    assertThat(html).contains("class=\"f3d-quick\"").contains("AcademyCraft").contains("입시상담 AI").contains("모의고사위키")
        .contains("BuildIt 학원 홈페이지 빌더").doesNotContain("href=\"#\"");
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 서비스 카드 7개가 번호 순서대로 있고 기존 앵커 ID 순서(예상문제→대시보드→AI 콘텐츠)가 유지된다")
  void cards_orderAndAnchors() {
    // given / when
    String html = render();
    // then
    int prev = -1;
    for (String no : new String[] {"01", "02", "03", "04", "05", "06", "07"}) {
      int at = html.indexOf("<span class=\"en-card-no\">" + no + "</span>");
      assertThat(at).as("card " + no).isGreaterThan(prev);
      prev = at;
    }
    assertThat(html.indexOf("id=\"svc-dashboard\"")).isGreaterThan(html.indexOf("id=\"svc-exam\""));
    assertThat(html.indexOf("id=\"ai-content\"")).isGreaterThan(html.indexOf("id=\"svc-dashboard\""));
    assertThat(html).contains("class=\"en-coming-soon\"").contains("COMING SOON");
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 폐기·미검증 표현(위키 별칭·완료 단정)이 없고 위키는 모의고사위키로만 표기한다")
  void copy_staysWithinApprovedWording() {
    // given / when
    String html = render();
    // then
    assertThat(html).contains(">모의고사위키<").doesNotContain("내신위키").doesNotContain("기출분석").doesNotContain("출제방향")
        .doesNotContain("DesignCraft").doesNotContain("PhotoCraft");
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 3D CSS는 기존 CSS 뒤에, 코어 스크립트는 메인 스크립트 앞에 defer로 로드된다")
  void assets_orderedAndDeferred() {
    // given / when
    String html = render();
    // then
    int base = html.indexOf("/css/landing_edunova.css");
    int f3d = html.indexOf("/css/landing_3d.css");
    int core = html.indexOf("/js/landing_3d_core.js");
    int main = html.indexOf("/js/landing_3d.js");
    assertThat(base).isPositive();
    assertThat(f3d).isGreaterThan(base);
    assertThat(core).isPositive();
    assertThat(main).isGreaterThan(core);
    assertThat(html).contains("landing_3d_core.js?v=").contains("landing_3d.js?v=");
    assertThat(html).containsPattern("<script src=\"/js/landing_3d_core\\.js[^>]*defer>");
    assertThat(html).containsPattern("<script src=\"/js/landing_3d\\.js[^>]*defer>");
  }

  @Test
  @DisplayName("given 3D CSS when 읽으면 then JS 없을 때 콘텐츠를 숨기지 않고 움직임 감소·WebGL 없음 대응이 있다")
  void css_isProgressiveAndAccessible() throws IOException {
    // given / when
    String css = read("css/landing_3d.css");
    // then
    assertThat(css).contains(".f3d-js [data-f3d-reveal]").contains("@media (prefers-reduced-motion: reduce)")
        .contains("backdrop-filter").contains("@supports not");
    // 기본(JS 없음) 상태에서는 숨김 규칙이 없다: 모든 [data-f3d-reveal] 선택자는 .f3d-js 아래에만 존재한다
    assertThat(css).doesNotContainPattern("(?<!\\.f3d-js )\\[data-f3d-reveal\\]");
  }

  @Test
  @DisplayName("given 3D JS when 읽으면 then 서버 호출·저장소 접근·외부 라이브러리가 없고 지연 로딩·정적 모드·컨텍스트 복구가 있다")
  void js_isSelfContainedAndSafe() throws IOException {
    // given / when
    String js = read("js/landing_3d.js");
    // then
    assertThat(js).doesNotContain("fetch(").doesNotContain("XMLHttpRequest").doesNotContain("localStorage")
        .doesNotContain("sessionStorage").doesNotContain("document.cookie").doesNotContain("import(");
    assertThat(js).contains("IntersectionObserver").contains("webglcontextlost").contains("webglcontextrestored")
        .contains("prefers-reduced-motion").contains("decideRenderMode").contains("visibilitychange");
  }
}
