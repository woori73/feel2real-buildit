package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 갈무리 게시판 화면(/newstart/galmuri) 렌더링.
 *
 * <p>채팅·질문요청과 같은 규칙: 셸은 열리고 데이터는 인증 회원만
 * ({@code /api/newstart/galmuri/**}는 {@code /api/**} catch-all이 401). 갈무리는 운영 승인(ACTIVE)
 * 지식만 노출하므로 자유 게시판 쓰기 endpoint는 애초에 없다(§6).
 */
@Controller
public class NewStartGalmuriPageController {

  @GetMapping("/newstart/galmuri")
  public String galmuri(HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "newstart/galmuri";
  }
}
