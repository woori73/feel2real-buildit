package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 학생 업로드 자료 뷰 3종을 같은 {@code student/conversation_materials} 템플릿으로 렌더한다.
 * 사이드바 활성 메뉴(activeMenu) / 제목 / 클라이언트 필터(materialFilter)만 모델로 분기한다.
 *
 * <ul>
 *   <li>{@code /conversation/materials} — 학교 자료 모음 (학생 업로드 전체 union)</li>
 *   <li>{@code /conversation/exam-materials} — 시험지 대시보드 (ContentKind EXAM_*)</li>
 *   <li>{@code /conversation/study-materials} — 학습자료 대시보드 (ContentKind GENERAL)</li>
 * </ul>
 *
 * <p>실제 필터링은 {@code conversation_materials.js}가 {@code window.MATERIAL_FILTER} 값으로 수행한다.
 * 데이터 소스는 모두 {@code GET /api/conversations/{id}}의 sources(STUDENT_MATERIAL)이며 스키마 변경은 없다.
 */
@Controller
@RequiredArgsConstructor
public class ConversationMaterialsPageController {

  private final ConversationService conversationService;

  private static final String VIEW = "student/conversation_materials";

  @GetMapping({"/conversation/materials", "/conversation/materials.html"})
  public String all(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) Long conversationId,
      Model model) {
    fence(id, conversationId);
    return render(model, "materials", "학교 자료 모음",
        "이 대화에 첨부된 모든 자료를 한눈에 확인해요.", "ALL");
  }

  @GetMapping({"/conversation/exam-materials", "/conversation/exam-materials.html"})
  public String exams(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) Long conversationId,
      Model model) {
    fence(id, conversationId);
    return render(model, "exam-materials", "시험지 대시보드",
        "학생이 업로드한 시험지를 한눈에 확인해요.", "EXAM");
  }

  @GetMapping({"/conversation/study-materials", "/conversation/study-materials.html"})
  public String study(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) Long conversationId,
      Model model) {
    fence(id, conversationId);
    return render(model, "study-materials", "학습자료 대시보드",
        "학생이 업로드한 일반 학습자료를 한눈에 확인해요.", "GENERAL");
  }

  private void fence(Long id, Long conversationId) {
    Long scopedConversationId = id != null ? id : conversationId;
    if (scopedConversationId != null) {
      conversationService.getDetail(scopedConversationId);
    }
  }

  private String render(Model model, String activeMenu, String title, String subtitle, String filter) {
    model.addAttribute("activeMenu", activeMenu);
    model.addAttribute("pageTitle", title);
    model.addAttribute("pageSubtitle", subtitle);
    model.addAttribute("materialFilter", filter);
    return VIEW;
  }
}
