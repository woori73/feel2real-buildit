package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.controller.ExamInsightController;
import f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntakePageController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * User-supplied certificate details. Publication requires confirmation of current seller/address.
 */
@ControllerAdvice(
    assignableTypes = {
      LandingPageController.class,
      ExamPageController.class,
      PublicLegalController.class,
      ExamInsightController.class,
      ExamIntakePageController.class
    })
public class HomeExamSellerAdvice {
  private final boolean confirmed;

  public HomeExamSellerAdvice(@Value("${app.home.exam-seller-confirmed:false}") boolean confirmed) {
    this.confirmed = confirmed;
  }

  public record Seller(
      String name,
      String owner,
      String businessNumber,
      String commerceNumber,
      String address,
      String email) {}

  @ModelAttribute("examSeller")
  public Seller seller() {
    return confirmed
        ? new Seller(
            "압구정에듀홀릭 출판사",
            "박정민",
            "146-99-01347",
            "제2026-서울송파-1411호",
            "서울특별시 송파구 중대로10길 37-25, 4층 (가락동)",
            "woorinaesin@naver.com")
        : null;
  }
}
