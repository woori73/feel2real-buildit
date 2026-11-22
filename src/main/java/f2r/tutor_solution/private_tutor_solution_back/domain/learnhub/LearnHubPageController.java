package f2r.tutor_solution.private_tutor_solution_back.domain.learnhub;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 학생 풀이 첫 화면(/learn). 과목·시험 탭을 고르고 「풀기」를 누르면 기존 풀이 화면으로 바로 이어진다.
 * 목록은 누구나 보고, 풀이의 로그인 규칙은 각 풀이 화면의 기존 규칙 그대로다.
 */
@Controller
public class LearnHubPageController {

  static final String PAGE = "/learn";

  private final LearnHubCatalog catalog;

  public LearnHubPageController(LearnHubCatalog catalog) {
    this.catalog = catalog;
  }

  @GetMapping(PAGE)
  public String page(@RequestParam(required = false) String tab, Model model) {
    model.addAttribute("tabs", catalog.tabs());
    model.addAttribute("activeTab", LearnHubCatalog.tabOrDefault(tab));
    model.addAttribute("grades", catalog.grades());
    model.addAttribute("schools", catalog.schools());
    return "public/learn";
  }
}
