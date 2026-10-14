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
 * 랜딩 템플릿을 Thymeleaf 엔진으로 직접 렌더한다.
 *
 * <p>문자열 계약 테스트(LandingTemplateContractTest)는 파싱 오류나 fragment 오타를 잡지 못한다.
 * 전체 Spring context 없이(=DB 없이) 실제 렌더를 돌려 그 구멍을 막는다.
 */
class LandingTemplateRenderTest {

  private static final String VIEW = "public/landing";

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

  private static String render() {
    return engine().process(VIEW, new Context());
  }

  @Test
  @DisplayName("given 랜딩 when 렌더하면 then 레이아웃·fragment가 해석되고 Report·Shorts·제작 중복 섹션은 없다")
  void renders_landing_without_unprovided_sections() {
    // given/when
    String html = render();

    // then
    assertThat(html)
        .contains("<!DOCTYPE html>")
        // layouts/public가 적용됐다는 증거 (GTM + favicon fragment)
        .contains("GTM-MMJ4MGNN")
        .contains("/favicon.svg?v=20260613")
        // fragment가 실제로 치환됐는지 (미치환이면 th:block 태그가 그대로 남는다)
        .contains("class=\"public-footer\"")
        .contains("id=\"contactModal\"")
        .contains("data-account-menu")
        .contains("data-login-modal")
        .doesNotContain("th:block")
        .doesNotContain("th:replace")
        .contains("class=\"footer-name footer-name-bi\"")
        .contains("상호명: 압구정에듀홀릭학원")
        .contains("href=\"/chart\"")
        .doesNotContain("Feel to Real (F2R)")
        // 홈페이지 제작은 히어로 한 곳에서만 소개한다.
        .doesNotContain("id=\"web\"")
        .doesNotContain("id=\"journey\"")
        // 아직 제공하지 않는 서비스
        .doesNotContain("id=\"report\"")
        .doesNotContain("id=\"shorts\"")
        .doesNotContain("/api/landing/videos/ACADEMY_SHORTS_DEMO/stream")
        .doesNotContain("/api/landing/videos/PARENT_REPORT_DEMO/stream")
        // Wiki 단계 영상은 번들 정적 경로 그대로
        .contains("src=\"/video/landing/digital_twin_ex.mp4\"")
        .contains("src=\"/video/landing/how2use_digitaltwin.mp4\"");
  }
}
