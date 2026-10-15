package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import f2r.tutor_solution.private_tutor_solution_back.program.Program;
import f2r.tutor_solution.private_tutor_solution_back.program.ProgramEntry;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProblemGeneratorPageController {
  private static final String WIKI = "redirect:/problem-generator/wiki/index.html";

  /**
   * 문제지 제작기 첫 화면. 비회원은 공개 소개(200)에서 네이버 로그인으로 시작하고, 권한 없는 로그인 사용자는 안내 화면,
   * 허용된 회원은 기존 제작기(Wiki 화면)로 이동한다. 하위 화면(/problem-generator/**)은 기존대로 로그인 필수.
   */
  @GetMapping("/problem-generator")
  public String generator(Authentication auth, Model model, HttpServletResponse response) {
    ProgramEntry.State state = ProgramEntry.stateOf(Program.PROBLEM_GENERATOR, auth);
    if (state == ProgramEntry.State.ALLOWED) return WIKI;
    return ProgramEntry.intro(Program.PROBLEM_GENERATOR, state, model, response);
  }
  @GetMapping("/problem-generator/digital-twin") public String digitalTwin() { return WIKI; }
  @GetMapping("/problem-generator/questions") public String questions() { return WIKI; }
  @GetMapping("/problem-generator/exam") public String exam() { return WIKI; }
  @GetMapping("/problem-generator/wrong-notes") public String wrongNotes() { return WIKI; }
  @GetMapping("/problem-generator/textbooks") public String textbooks() { return WIKI; }
  @GetMapping("/problem-generator/schools") public String schools() { return WIKI; }
}
