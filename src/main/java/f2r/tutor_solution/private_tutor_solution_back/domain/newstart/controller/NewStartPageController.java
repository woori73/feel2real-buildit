package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * NewStart 공개 페이지(&#47;newstart) 렌더링.
 *
 * <p>개인채무조정 상황 관리, NewStart AI, 최신 정보, 지식 라이브러리 진입점을 제공하는 MVP 페이지다.
 * 아직 백엔드 개인 데이터는 연결되지 않았으므로 공개 페이지로 제공하며, 실제 로그인/개인 데이터는
 * 향후 단계에서 연결한다.
 *
 * <p>WikiPageController 와 동일하게 {@code Cache-Control: no-store}만 위해 컨트롤러로 둔다.
 * 앞단 캐시가 계정 상태에 따라 달라질 수 있는 HTML을 공유하지 않게 하려는 목적이다.
 */
@Controller
public class NewStartPageController {

  @GetMapping("/newstart")
  public String newstartHome(HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "newstart/index";
  }
}
