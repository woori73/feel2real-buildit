package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 옛 내신 영어 문제지 제작(맞춤문제) 페이지 "/exam".
 * 2026-11-23 대표 지시로 맞춤문제를 내려, 들어오면 홈의 「서비스 도입 문의」로 보낸다.
 */
@Controller
public class ExamPageController {

  @GetMapping("/exam")
  public String exam() {
    return "redirect:/#contact";
  }
}
