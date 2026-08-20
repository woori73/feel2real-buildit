package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProblemGeneratorPageController {
  private static final String WIKI = "redirect:/problem-generator/wiki/index.html";

  @GetMapping("/problem-generator") public String generator() { return WIKI; }
  @GetMapping("/problem-generator/digital-twin") public String digitalTwin() { return WIKI; }
  @GetMapping("/problem-generator/questions") public String questions() { return WIKI; }
  @GetMapping("/problem-generator/exam") public String exam() { return WIKI; }
  @GetMapping("/problem-generator/wrong-notes") public String wrongNotes() { return WIKI; }
  @GetMapping("/problem-generator/textbooks") public String textbooks() { return WIKI; }
  @GetMapping("/problem-generator/schools") public String schools() { return WIKI; }
}
