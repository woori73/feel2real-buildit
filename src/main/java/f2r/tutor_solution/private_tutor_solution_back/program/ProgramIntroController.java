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
  public static final String NOTICE_TITLE_ATTRIBUTE = ProgramIntroController.class.getName() + ".NOTICE_TITLE";
  public static final String NOTICE_MESSAGE_ATTRIBUTE = ProgramIntroController.class.getName() + ".NOTICE_MESSAGE";

  @GetMapping("/program/intro")
  public String intro(
      Authentication auth, HttpServletRequest request, Model model, HttpServletResponse response) {
    Object attr = request.getAttribute(PROGRAM_ATTRIBUTE);
    if (!(attr instanceof Program program)) {
      return "redirect:/";
    }
    return ProgramEntry.intro(program, ProgramEntry.stateOf(program, auth), model, response);
  }

  /**
   * 소개 화면이 없는 하위 기능(학원 운영 화면, 강사 전용 대화 화면 등)에서 권한이 없는 로그인 사용자에게 보이는
   * 서비스 안내 화면. SecurityConfig의 접근 거부 처리기가 forward하며 상태는 403이다.
   */
  @GetMapping("/program/notice")
  public String notice(HttpServletRequest request, Model model, HttpServletResponse response) {
    Object message = request.getAttribute(NOTICE_MESSAGE_ATTRIBUTE);
    if (!(message instanceof String text)) {
      return "redirect:/";
    }
    Object title = request.getAttribute(NOTICE_TITLE_ATTRIBUTE);
    model.addAttribute("noticeTitle", title instanceof String t ? t : "이용 권한 안내");
    model.addAttribute("noticeMessage", text);
    response.setHeader("Cache-Control", "private, no-store");
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    return "program/notice";
  }
}
