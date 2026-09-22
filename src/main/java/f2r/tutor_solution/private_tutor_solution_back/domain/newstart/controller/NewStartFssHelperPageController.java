package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 금감원 민원 도우미 화면(/newstart/fss-helper) 렌더링.
 *
 * <p>페이지 공개·데이터 API 인증은 {@code /newstart/chat}과 동일 정책(SecurityConfig에 새 규칙
 * 없음). compose/samples는 {@code /api/**} 인증 뒤에 있다.
 */
@Controller
public class NewStartFssHelperPageController {

  @GetMapping("/newstart/fss-helper")
  public String fssHelper(HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "newstart/fss-helper";
  }
}
