package f2r.tutor_solution.private_tutor_solution_back.domain.academy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-② 회귀 방지 계약 테스트.
 *
 * <p>PR #578 머지 과정에서 PR #576(테넌트 기능)의 파일 7개가 삭제되고, 두 HTML 템플릿의
 * tenant 스크립트 연결이 끊긴 회귀를 방지한다. 아래 계약 중 하나라도 깨지면 빌드가 실패한다.
 *
 * <ul>
 *   <li>academy_public.html → tenant_*.js 5종 스크립트 참조
 *   <li>academy_builder.html → academy_tenant_features.js 스크립트 참조
 *   <li>src/main/resources/static/js/ → 테넌트 JS 6종 파일 존재
 * </ul>
 */
class AcademyTenantScriptsContractTest {

  private static final String[] TENANT_PUBLIC_SCRIPTS = {
      "tenant_settings.js",
      "tenant_video_filter.js",
      "tenant_popup.js",
      "tenant_cta.js",
      "tenant_live.js"
  };

  private static final String[] TENANT_JS_FILES = {
      "academy_tenant_features.js",
      "tenant_cta.js",
      "tenant_live.js",
      "tenant_popup.js",
      "tenant_settings.js",
      "tenant_video_filter.js"
  };

  @Test
  void givenAcademyPublicHtml_whenInspected_thenTenantScriptsAreReferenced()
      throws Exception {
    // given
    String html = template("public/academy_public.html");

    // when / then — tenant_settings.js 포함 tenant 5종 전부 참조되어야 한다
    assertThat(html).contains("/js/tenant_settings.js");
    for (String script : TENANT_PUBLIC_SCRIPTS) {
      assertThat(html)
          .as("academy_public.html must reference /js/%s", script)
          .contains("/js/" + script);
    }
  }

  @Test
  void givenAcademyBuilderHtml_whenInspected_thenTenantFeaturesScriptIsReferenced()
      throws Exception {
    // given
    String html = template("tutor/academy_builder.html");

    // when / then
    assertThat(html)
        .as("academy_builder.html must reference academy_tenant_features.js")
        .contains("/js/academy_tenant_features.js");
  }

  @Test
  void givenStaticJsDir_whenInspected_thenSixTenantScriptsExist() {
    // when / then — 테넌트 JS 6종이 static 리소스로 존재해야 한다
    for (String file : TENANT_JS_FILES) {
      assertThat(new ClassPathResource("static/js/" + file).exists())
          .as("static JS file must exist: %s", file)
          .isTrue();
    }
  }

  private static String template(String path) throws Exception {
    ClassPathResource resource = new ClassPathResource("templates/" + path);
    assertThat(resource.exists()).as("template must exist: %s", path).isTrue();
    try (var input = resource.getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
