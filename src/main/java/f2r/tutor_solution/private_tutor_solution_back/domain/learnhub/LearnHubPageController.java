package f2r.tutor_solution.private_tutor_solution_back.domain.learnhub;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 학습 콘텐츠 모음(/learn). 누구나 보는 링크 모음 화면이며, 각 카드가 가리키는 화면의 로그인 규칙은 그대로다. */
@Controller
public class LearnHubPageController {

  static final String PAGE = "/learn";

  private final LearnHubCatalog catalog;

  public LearnHubPageController(LearnHubCatalog catalog) {
    this.catalog = catalog;
  }

  @GetMapping(PAGE)
  public String page(Model model) {
    model.addAttribute("groups", catalog.groups());
    model.addAttribute("audiences", LearnHubCatalog.AUDIENCES);
    model.addAttribute("schools", catalog.schools());
    return "public/learn";
  }
}
