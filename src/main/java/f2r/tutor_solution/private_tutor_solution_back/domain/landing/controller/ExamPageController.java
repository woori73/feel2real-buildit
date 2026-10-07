package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 내신 영어 문제지 제작 페이지("/exam") 렌더링.
 */
@Controller
public class ExamPageController {

  @GetMapping("/exam")
  public String exam() {
    return "public/exam";
  }
}
