package f2r.tutor_solution.private_tutor_solution_back.program;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SecurityConfig(인증 진입점·접근 거부 처리기)가 내부 forward로 넘겨 주는 공개 소개 화면.
 * 뷰 컨트롤러로 매핑된 프로그램 대표 URL({@code /ledger}, {@code /easy} 등)이 비회원·권한 부족 사용자에게
 * 로그인 이동이나 기본 403 대신 소개/안내 화면을 보이게 한다. forward 속성이 없는 직접 요청은 홈으로 보낸다.
 */
@Controller
public class ProgramIntroController {

  public static final String PROGRAM_ATTRIBUTE = ProgramIntroController.class.getName() + ".PROGRAM";

  @GetMapping("/program/intro")
  public String intro(
      Authentication auth, HttpServletRequest request, Model model, HttpServletResponse response) {
    Object attr = request.getAttribute(PROGRAM_ATTRIBUTE);
    if (!(attr instanceof Program program)) {
      return "redirect:/";
    }
    return ProgramEntry.intro(program, ProgramEntry.stateOf(program, auth), model, response);
  }
}
