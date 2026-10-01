package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** NewStart ID/PW 로그인·가입 화면(/newstart/login). 공개 페이지이며 인증은 JS 가 기존 /api/auth/login 과 /api/newstart/auth/signup 을 호출한다. */
@Controller
public class NewStartLoginPageController {

  @GetMapping("/newstart/login")
  public String login() {
    return "newstart/login";
  }
}
