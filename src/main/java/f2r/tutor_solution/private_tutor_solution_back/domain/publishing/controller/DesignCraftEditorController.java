package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * DesignCraft 웹 편집기 페이지.
 *
 * <p>Qoder 프론트엔드 (publishing_editor) 통합.
 * 실제 출판 API는 {@code /api/publishing/*} REST를 사용한다.
 */
@Controller
@RequestMapping("/designcraft")
public class DesignCraftEditorController {

  /**
   * 출판 편집기 화면 → {@code templates/designcraft/editor.html}.
   */
  @GetMapping("/editor")
  public String editor() {
    return "designcraft/editor";
  }
}
