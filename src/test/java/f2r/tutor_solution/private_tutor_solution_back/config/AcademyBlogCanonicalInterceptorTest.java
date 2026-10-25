package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class AcademyBlogCanonicalInterceptorTest {
  @Test void givenKoreanPathAndTrackingQuery_whenRendered_thenOneEncodedCanonicalOnTrustedHost() {
    // given: MVC supplies decoded route variables; incoming host/query must not become canonical.
    var request = new MockHttpServletRequest("GET", "/academies/test/posts/news");
    request.setServerName("untrusted.example");
    request.addHeader("X-Forwarded-Host", "untrusted.example");
    request.setQueryString("utm_source=google&login=required");
    request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
        Map.of("slug", "우리 학원", "postSlug", "소식&안내"));
    for (String name : new String[]{"public/academy_blog_posts", "public/academy_blog_post"}) {
      var view = new ModelAndView(name);
      // when
      new AcademyBlogCanonicalInterceptor().postHandle(request, new MockHttpServletResponse(), new Object(), view);
      var context = new Context();
      context.setVariables(view.getModel());
      var links = Jsoup.parse(engine().process(name, context)).select("head link[rel=canonical]");
      // then: head survives layout decoration, no duplicate canonical or tracking query.
      assertThat(links).hasSize(1);
      String expected = "https://feel2real.co.kr/academies/%EC%9A%B0%EB%A6%AC%20%ED%95%99%EC%9B%90/posts";
      if (name.endsWith("academy_blog_post")) expected += "/%EC%86%8C%EC%8B%9D&%EC%95%88%EB%82%B4";
      assertThat(links.first().attr("href")).isEqualTo(expected);
    }
  }

  @Test void givenPrivateViewOrError_whenHandled_thenNoCanonicalInjected() {
    var request = new MockHttpServletRequest();
    request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("slug", "test"));
    for (String name : new String[]{"admin/dashboard", "studio/workspace", "redirect:/", "public/academy_blog_posts"}) {
      var view = new ModelAndView(name);
      var response = new MockHttpServletResponse();
      if (name.equals("public/academy_blog_posts")) response.setStatus(404);
      new AcademyBlogCanonicalInterceptor().postHandle(request, response, new Object(), view);
      assertThat(view.getModel()).doesNotContainKey("publicCanonical");
    }
  }

  @Test void givenPublicListAndPrivacyViews_whenRendered_thenExactlyOneFixedCanonical() {
    for (var item : Map.of("academies", "/academies", "academy_posts", "/academy-posts", "privacy", "/privacy").entrySet()) {
      var links = Jsoup.parse(engine().process("public/" + item.getKey(), new Context()))
          .select("head link[rel=canonical]");
      assertThat(links).hasSize(1);
      assertThat(links.first().attr("href")).isEqualTo("https://feel2real.co.kr" + item.getValue());
    }
  }

  private TemplateEngine engine() {
    var resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML");
    var engine = new TemplateEngine();
    engine.setTemplateResolver(resolver); engine.addDialect(new LayoutDialect());
    return engine;
  }
}
