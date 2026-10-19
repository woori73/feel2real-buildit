package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyListResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.dto.AcademyBlogPostResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.service.AcademyBlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequiredArgsConstructor
public class SitemapController {
  private final AcademyService academyService;
  private final AcademyBlogService academyBlogService;

  @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public String sitemap() {
    StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
    append(xml, "/", null);
    append(xml, "/home", null);
    append(xml, "/faq", null);
    append(xml, "/newstart", null);
    append(xml, "/exam", null);
    append(xml, "/wiki", null);
    append(xml, "/privacy", null);
    append(xml, "/academies", null);
    append(xml, "/academy-posts", null);
    for (AcademyListResponse academy : academyService.listPublic(null, null, null, null)) {
      append(xml, "/academies/" + academy.slug(), null);
      append(xml, "/academies/" + academy.slug() + "/posts", null);
    }
    for (AcademyBlogPostResponse post : academyBlogService.listPublishedForSitemap()) {
      append(xml, post.publicUrl(), post.updatedAt());
    }
    xml.append("</urlset>\n");
    return xml.toString();
  }

  private void append(StringBuilder xml, String path, LocalDateTime updatedAt) {
    xml.append("  <url><loc>https://feel2real.co.kr").append(escape(path)).append("</loc>");
    if (updatedAt != null) {
      xml.append("<lastmod>").append(updatedAt.toLocalDate().format(DateTimeFormatter.ISO_DATE)).append("</lastmod>");
    }
    xml.append("</url>\n");
  }

  private String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
