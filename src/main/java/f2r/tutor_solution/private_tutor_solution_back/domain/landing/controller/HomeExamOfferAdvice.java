package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.HomeExamOffer;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.controller.ExamInsightController;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** All current exam sales surfaces use the same offer; unrelated historical content is untouched. */
@ControllerAdvice(assignableTypes = {ExamPageController.class, PublicLegalController.class, ExamInsightController.class})
public class HomeExamOfferAdvice {
  @ModelAttribute("examOffer")
  public HomeExamOffer currentOffer() { return HomeExamOffer.CURRENT; }
}
