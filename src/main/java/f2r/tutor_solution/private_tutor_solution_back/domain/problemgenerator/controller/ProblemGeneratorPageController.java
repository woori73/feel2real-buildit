package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProblemGeneratorPageController {
  private record Menu(String key, String path, String label) {}
  private static final List<Menu> MENUS = List.of(
      new Menu("generator", "/problem-generator", "중등 내신 문제 생성기"),
      new Menu("digital-twin", "/problem-generator/digital-twin", "Digital Twin 관리"),
      new Menu("questions", "/problem-generator/questions", "강사 문제 DB"),
      new Menu("exam", "/problem-generator/exam", "내신 실전 제작"),
      new Menu("wrong-notes", "/problem-generator/wrong-notes", "학생별 오답노트"),
      new Menu("textbooks", "/problem-generator/textbooks", "교과서 DB"),
      new Menu("schools", "/problem-generator/schools", "학교별 DB"));

  @GetMapping("/problem-generator") public String generator(Model model) { return page(model, "generator", "중등 내신 문제 생성기", "학교·학년·시험을 선택하면 Digital Twin이 자동 적용되어 신규 문제 생성과 기존 문제 변형을 모두 지원합니다."); }
  @GetMapping("/problem-generator/digital-twin") public String digitalTwin(Model model) { return page(model, "digital-twin", "Digital Twin 관리", "강사가 만든 Digital Twin HTML을 업로드하면 학교·학년·시험 계층이 자동으로 생성됩니다."); }
  @GetMapping("/problem-generator/questions") public String questions(Model model) { return page(model, "questions", "강사 문제 DB", "생성하거나 변형한 문항을 SQLite DB에서 확인하고 관리합니다."); }
  @GetMapping("/problem-generator/exam") public String exam(Model model) { return page(model, "exam", "내신 실전 제작", "선택한 문항으로 실전 시험지를 구성합니다."); }
  @GetMapping("/problem-generator/wrong-notes") public String wrongNotes(Model model) { return page(model, "wrong-notes", "학생별 오답노트", "학생을 등록하고, 채점된 시험지에서 틀린 문제만 추출해 학생·시험별로 저장합니다."); }
  @GetMapping("/problem-generator/textbooks") public String textbooks(Model model) { return page(model, "textbooks", "교과서 DB", "출판사와 Lesson별 수업 자료를 등록하고 관리합니다."); }
  @GetMapping("/problem-generator/schools") public String schools(Model model) { return page(model, "schools", "학교별 DB", "학교·학년·시험별 기출과 실전 자료를 한곳에서 관리합니다."); }

  private String page(Model model, String active, String title, String subtitle) {
    model.addAttribute("menus", MENUS);
    model.addAttribute("activeMenu", active);
    model.addAttribute("pageTitle", title);
    model.addAttribute("pageSubtitle", subtitle);
    return "problem-generator/page";
  }
}
