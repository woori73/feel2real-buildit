package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * NewStart 질문요청 게시판 화면(/newstart/questions) 렌더링.
 *
 * <p>화면 셸은 다른 NewStart 페이지와 동일하게 두고, 실제 요청 데이터는 전부 인증 뒤에 있다
 * ({@code /api/newstart/requests/**}는 {@code /api/**} catch-all이 401). 설계 §7의
 * "비로그인 열람 불가"는 데이터 접근으로 강제하고, 비로그인 방문자에게는 로그인 안내를 보여준다.
 */
@Controller
public class NewStartRequestPageController {

  @GetMapping("/newstart/questions")
  public String questions(HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "newstart/questions";
  }
}
