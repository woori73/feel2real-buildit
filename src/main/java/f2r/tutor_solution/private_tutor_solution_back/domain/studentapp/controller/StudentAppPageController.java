package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 재원생 앱 화면(빈 틀 — 데이터는 API가 권한을 확인해 준다).
 * <ul>
 *   <li>/naesintest/app: 학생 앱 첫 화면(코드 로그인 → 배정된 시험·내 기록). 설치용 manifest는 원장 앱과 따로.</li>
 *   <li>/naesintest/app/run·review: 기존 시험·해설 화면을 앱 모드(ntApp)로 연다.</li>
 *   <li>/easy/student-app: 원장 — 코드 발급·시험 배정·볼 사람 지정(ADMIN, /easy/**).</li>
 *   <li>/dashboard/exam-results: 원장·지정한 강사 — 새 응시 기록.</li>
 * </ul>
 */
@Controller
public class StudentAppPageController {

  @GetMapping("/naesintest/app")
  public String app() {
    return "naesintest/app";
  }

  @GetMapping("/naesintest/app/run")
  public String run(Model model) {
    model.addAttribute("ntApp", true);
    return "naesintest/run";
  }

  @GetMapping("/naesintest/app/review")
  public String review(Model model) {
    model.addAttribute("ntApp", true);
    return "naesintest/review";
  }

  @GetMapping("/easy/student-app")
  public String manage() {
    return "easy/student_app";
  }

  @GetMapping("/dashboard/exam-results")
  public String results() {
    return "dashboard_exam_results";
  }
}
