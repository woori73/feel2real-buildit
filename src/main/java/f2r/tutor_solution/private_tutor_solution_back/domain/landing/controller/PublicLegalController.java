package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.HomeExamOffer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Public policy navigation; /privacy remains the existing Chrome extension document. */
@Controller
public class PublicLegalController {

  @GetMapping("/privacy")
  public String privacy() {
    return "public/privacy";
  }

  @GetMapping("/policies")
  public String policies() {
    return "public/policies";
  }

  @GetMapping("/policies/privacy")
  public String sitePrivacy(Model model) {
    return document(model, "privacy", "Feel2Real 홈페이지 개인정보 처리방침");
  }

  @GetMapping("/policies/terms")
  public String siteTerms(Model model) {
    model.addAttribute("offer", HomeExamOffer.CURRENT);
    return document(model, "terms", "Feel2Real 홈페이지 이용약관");
  }

  @GetMapping("/policies/refunds")
  public String refunds(Model model) {
    model.addAttribute("offer", HomeExamOffer.CURRENT);
    return document(model, "refunds", "환불·청약철회 정책");
  }

  @GetMapping("/policies/business")
  public String business(Model model) {
    return document(model, "business", "사업자 정보");
  }

  private String document(Model model, String section, String title) {
    model.addAttribute("policySection", section);
    model.addAttribute("policyTitle", title);
    return "public/policy_document";
  }

  @GetMapping("/faq")
  public String faq() {
    return "public/faq";
  }
}
