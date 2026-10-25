package f2r.tutor_solution.private_tutor_solution_back.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

/** Canonical for public blog views only; never trusts Host, forwarded headers or query parameters. */
public class AcademyBlogCanonicalInterceptor implements HandlerInterceptor {
  @Override
  public void postHandle(HttpServletRequest request, HttpServletResponse response,
      Object handler, ModelAndView view) {
    if (view == null || response.getStatus() != 200) return;
    boolean list = "public/academy_blog_posts".equals(view.getViewName());
    boolean detail = "public/academy_blog_post".equals(view.getViewName());
    if (!list && !detail) return;
    Object attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (!(attributes instanceof Map<?, ?> paths)
        || !(paths.get("slug") instanceof String slug) || slug.isBlank()) return;
    var uri = UriComponentsBuilder.fromUriString("https://feel2real.co.kr")
        .pathSegment("academies", slug, "posts");
    if (detail) {
      if (!(paths.get("postSlug") instanceof String postSlug) || postSlug.isBlank()) return;
      uri.pathSegment(postSlug);
    }
    view.addObject("publicCanonical", uri.build().encode().toUriString());
  }
}
