package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 감사회원 안내 화면(/newstart/thanks-guide) 렌더링.
 *
 * <p>카페 공지 #3198(전자책 구매 방법) 기준의 절차 안내만 표시한다.
 * AUTH·권한·DB를 변경하지 않는 정적 안내 페이지다.
 */
@Controller
public class NewStartThanksGuidePageController {

  @GetMapping("/newstart/thanks-guide")
  public String thanksGuide(HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "newstart/thanks-guide";
  }
}
