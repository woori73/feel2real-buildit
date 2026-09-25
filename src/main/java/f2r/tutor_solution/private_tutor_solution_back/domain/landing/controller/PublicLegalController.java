package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 공개 법적 페이지. Chrome Web Store 제출에 필요한 개인정보처리방침 공개 URL을 제공한다.
 * 내용은 NewStart Chrome Agent v0.4.0의 실제 동작(읽기·판별·안내 전용)과 1:1로 일치해야 한다.
 */
@Controller
public class PublicLegalController {

  @GetMapping("/privacy")
  public String privacy() {
    return "public/privacy";
  }
}
