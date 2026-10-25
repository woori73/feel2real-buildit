package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * TESTPREP 3단계 — 화면 진입 SSR 컨트롤러. `/testprep` 하위만 노출하며feel2real `/home`·BuildIt 페이지는 건드리지 않는다.
 * 인증은 기존 feel2real SSO(ACCESS_TOKEN 쿠키)를 그대로 사용하므로 별도 로그인·dev-login 로직이 없다.
 */
@Controller
public class TestPrepPageController {

  private static final String SSO_LOGIN = "/login";

  @GetMapping("/testprep")
  public String home(Model model) {
    model.addAttribute("tpCanonical", "https://feel2real.co.kr/testprep");
    model.addAttribute("pageActive", "home");
    model.addAttribute("ssoLoginUrl", SSO_LOGIN);
    return "testprep/home";
  }

  @GetMapping("/testprep/practice")
  public String practice(Model model) {
    model.addAttribute("kind", "PRACTICE");
    model.addAttribute("pageActive", "practice");
    model.addAttribute("ssoLoginUrl", SSO_LOGIN);
    return "testprep/catalog";
  }

  @GetMapping("/testprep/vocab")
  public String vocab(Model model) {
    model.addAttribute("kind", "VOCAB");
    model.addAttribute("pageActive", "vocab");
    model.addAttribute("ssoLoginUrl", SSO_LOGIN);
    return "testprep/catalog";
  }

  @GetMapping("/testprep/guide")
  public String guide(Model model) {
    model.addAttribute("pageActive", "guide");
    model.addAttribute("ssoLoginUrl", SSO_LOGIN);
    return "testprep/guide";
  }

  @GetMapping("/testprep/run")
  public String run(Model model) {
    model.addAttribute("pageActive", "practice");
    model.addAttribute("ssoLoginUrl", SSO_LOGIN);
    return "testprep/run";
  }

  @GetMapping("/testprep/review")
  public String review(Model model) {
    model.addAttribute("pageActive", "scores");
    model.addAttribute("ssoLoginUrl", SSO_LOGIN);
    return "testprep/review";
  }

  @GetMapping("/testprep/scores")
  public String scores(Model model) {
    model.addAttribute("pageActive", "scores");
    model.addAttribute("ssoLoginUrl", SSO_LOGIN);
    return "testprep/scores";
  }

  @GetMapping("/testprep/learning")
  public String learning(Model model) {
    model.addAttribute("pageActive", "learning");
    model.addAttribute("ssoLoginUrl", SSO_LOGIN);
    return "testprep/learning";
  }
}
