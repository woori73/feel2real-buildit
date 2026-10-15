package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.SubdomainAcademyFilter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;

/**
 * 서브도메인 root forward 렌더 지점. {@link SubdomainAcademyFilter}가 handle→slug를 해석해
 * request 속성으로 넘긴 뒤 이 경로로 forward한다. 기존 CSR 페이지({@code public/academy_public})를
 * 그대로 재사용하되, 브라우저 URL이 {@code /}라 JS가 path에서 slug를 못 뽑으므로 slug를 model로 주입한다.
 *
 * <p>직접 접근({@code /subdomain-home})은 속성이 없어 400이 되며, 정상 진입은 항상 필터 forward를 거친다.
 */
@Controller
public class SubdomainHomePageController {

  @GetMapping("/subdomain-home")
  public String render(
      @RequestAttribute(SubdomainAcademyFilter.SLUG_ATTRIBUTE) String slug,
      Model model) {
    model.addAttribute("slug", slug);
    return "public/academy_public";
  }
}
