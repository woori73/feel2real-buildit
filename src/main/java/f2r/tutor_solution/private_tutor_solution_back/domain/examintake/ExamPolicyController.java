package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.HomeExamOffer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ExamPolicyController {
  @GetMapping({"/exam-service/privacy", "/exam-service/terms"})
  String policies(Model model) {
    model.addAttribute("privacyVersion", PRIVACY);
    model.addAttribute("termsVersion", TERMS);
    model.addAttribute("offer", HomeExamOffer.CURRENT);
    return "public/exam_policy";
  }
}
