package f2r.tutor_solution.private_tutor_solution_back.domain.chart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChartPageController {
  @GetMapping("/chart")
  public String publicChart() {
    return "public/chart";
  }

  @GetMapping("/admin/chart")
  public String adminChart() {
    return "admin/chart";
  }
}
