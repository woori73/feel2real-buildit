package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.LandingVideoRenderItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;
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

  private static String render(
      LandingVideoRenderItem academyShortsVideo,
      LandingVideoRenderItem parentReportVideo) {
    Context context = new Context();
    context.setVariable("academyShortsVideo", academyShortsVideo);
    context.setVariable("parentReportVideo", parentReportVideo);
    return engine().process(VIEW, context);
  }

  @Test
  @DisplayName("given Shorts/Report 영상이 공개일 때 when 랜딩을 렌더하면 then 레이아웃·fragment·영상 source가 모두 해석된다")
  void renders_landing_with_visible_product_videos() {
    // given
    LandingVideoRenderItem shorts =
        LandingVideoRenderItem.managed(LandingVideoSlotKey.ACADEMY_SHORTS_DEMO);
    LandingVideoRenderItem report =
        LandingVideoRenderItem.managed(LandingVideoSlotKey.PARENT_REPORT_DEMO);

    // when
    String html = render(shorts, report);

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
        // publicFooterBuildIt → publicFooterBranded(true) 중첩 치환이 실제로 풀렸는지.
        // 파라미터가 안 넘어가면 조용히 F2R 브랜드가 렌더된다.
        .contains("class=\"footer-name footer-name-bi\"")
        .contains("© 2026 Feel 2 Real. All rights reserved.")
        .contains("상호명: 압구정에듀홀릭출판사")
        .contains("대한민국 지역별 영어교육자 TOP 100")
        .contains("href=\"/chart\"")
        .doesNotContain("Feel to Real (F2R)")
        // footer는 Build It이 Feel 2 Real의 서비스처럼 보이도록 F2R 로고를 함께 노출한다.
        .contains("/img/F2R_logo_pure.svg")
        .contains("Feel 2 Real 서비스")
        // Web은 매뉴얼의 가로 홈페이지 제작 영상을 쓰고, Shorts/Report는 관리형 스트림 URL이 src로 나간다.
        .contains("/manual/videos/02_homepage_setup.mp4")
        .contains("src=\"/api/landing/videos/ACADEMY_SHORTS_DEMO/stream\"")
        .contains("src=\"/api/landing/videos/PARENT_REPORT_DEMO/stream\"")
        .doesNotContain("Build It Shorts 예시 영상 준비 중")
        .doesNotContain("Build It Report 예시 영상 준비 중")
        // Wiki 단계 영상은 번들 정적 경로 그대로
        .contains("src=\"/video/landing/digital_twin_ex.mp4\"")
        .contains("src=\"/video/landing/how2use_digitaltwin.mp4\"");
  }

  @Test
  @DisplayName("given Shorts/Report 영상이 비공개일 때 when 랜딩을 렌더하면 then video 태그 없이 준비 중 프레임만 남는다")
  void renders_placeholders_when_product_videos_hidden() {
    // given
    LandingVideoRenderItem hidden =
        LandingVideoRenderItem.hidden(LandingVideoSlotKey.ACADEMY_SHORTS_DEMO);

    // when
    String html = render(hidden, LandingVideoRenderItem.hidden(LandingVideoSlotKey.PARENT_REPORT_DEMO));

    // then
    // 숨김 슬롯의 video 태그가 남으면 브라우저가 없는 리소스를 요청한다.
    assertThat(html)
        .doesNotContain("id=\"builderVideo\"")
        .doesNotContain("/api/landing/videos/ACADEMY_SHORTS_DEMO/stream")
        .doesNotContain("/api/landing/videos/PARENT_REPORT_DEMO/stream")
        .contains("/manual/videos/02_homepage_setup.mp4")
        .contains("Build It Shorts 예시 영상 준비 중")
        .contains("Build It Report 예시 영상 준비 중")
        // Wiki 영상은 이 슬롯과 무관하므로 그대로 렌더돼야 한다.
        .contains("src=\"/video/landing/digital_twin_ex.mp4\"");
  }
}
