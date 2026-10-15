package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class AcademyBlogSanitizer {
  private final Safelist safelist = Safelist.relaxed()
      .addTags("figure", "figcaption", "s")
      .addAttributes("img", "src", "alt", "title", "class", "data-align")
      .addAttributes("p", "class")
      .addAttributes("h1", "class").addAttributes("h2", "class").addAttributes("h3", "class")
      .addAttributes("h4", "class").addAttributes("h5", "class").addAttributes("h6", "class")
      .addAttributes("a", "href", "title", "target", "rel")
      .addProtocols("a", "href", "http", "https", "mailto")
      .preserveRelativeLinks(true);

  public String sanitize(String html) {
    if (html == null) return "";
    return Jsoup.clean(html, "https://feel2real.co.kr", safelist);
  }
}
