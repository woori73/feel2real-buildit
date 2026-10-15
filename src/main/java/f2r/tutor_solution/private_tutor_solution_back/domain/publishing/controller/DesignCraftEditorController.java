package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.controller;

import f2r.tutor_solution.private_tutor_solution_back.program.Program;
import f2r.tutor_solution.private_tutor_solution_back.program.ProgramEntry;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * DesignCraft 웹 편집기 페이지.
 *
 * <p>Qoder 프론트엔드 (publishing_editor) 통합.
 * 실제 출판 API는 {@code /api/publishing/*} REST를 사용한다.
 * 첫 화면({@code /designcraft})과 편집기 주소({@code /designcraft/editor})는 비회원에게도 공개 소개 화면(200)으로 열리고,
 * TUTOR·ADMIN 로그인 사용자에게만 실제 편집기를 렌더한다(그 외 로그인 사용자는 안내 화면).
 * 출판 API({@code /api/publishing/**})의 인증·인가는 SecurityConfig·서비스가 그대로 담당한다.
 */
@Controller
@RequestMapping("/designcraft")
public class DesignCraftEditorController {

  /**
   * 출판 편집기 화면 → {@code templates/designcraft/editor.html}.
   */
  @GetMapping("/editor")
  public String editor(Authentication auth, Model model, HttpServletResponse response) {
    ProgramEntry.State state = ProgramEntry.stateOf(Program.DESIGNCRAFT, auth);
    if (state == ProgramEntry.State.ALLOWED) {
      return "designcraft/editor";
    }
    return ProgramEntry.intro(Program.DESIGNCRAFT, state, model, response);
  }

  /** 공개 첫 화면 — 프로그램 소개와 "시작하기" 버튼(비회원은 네이버 로그인). */
  @GetMapping
  public String intro(Authentication auth, Model model, HttpServletResponse response) {
    return ProgramEntry.intro(
        Program.DESIGNCRAFT, ProgramEntry.stateOf(Program.DESIGNCRAFT, auth), model, response);
  }
}
