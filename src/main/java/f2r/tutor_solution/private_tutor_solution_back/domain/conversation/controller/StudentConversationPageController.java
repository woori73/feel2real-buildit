package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Legacy student-shaped conversation pages.
 *
 * <p>These routes render student UI shells, but many accept {@code ?id=} and client-side JS then loads
 * conversation-scoped APIs. Fence the id server-side as well so tutor-owned canonical records cannot
 * render through the legacy student templates before API guards run.
 */
@Controller
@RequiredArgsConstructor
public class StudentConversationPageController {

  private final ConversationService conversationService;

  @GetMapping({"/conversation/chat", "/conversation/chat.html"})
  public String chat(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) Long conversationId) {
    fence(id, conversationId);
    return "student/conversation_chat";
  }

  @GetMapping({"/conversation/tutor-materials", "/conversation/tutor-materials.html"})
  public String tutorMaterials(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) Long conversationId) {
    fence(id, conversationId);
    return "student/conversation_tutor_materials";
  }

  @GetMapping({"/conversation/homework", "/conversation/homework.html"})
  public String homework(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) Long conversationId) {
    fence(id, conversationId);
    return "student/conversation_homework";
  }

  @GetMapping({"/conversation/report", "/conversation/report.html"})
  public String report(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) Long conversationId) {
    fence(id, conversationId);
    return "student/conversation_report";
  }

  @GetMapping({"/conversation/review", "/conversation/review.html"})
  public String review(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) Long conversationId) {
    fence(id, conversationId);
    return "student/conversation_review";
  }

  private void fence(Long id, Long conversationId) {
    Long scopedConversationId = id != null ? id : conversationId;
    if (scopedConversationId != null) {
      conversationService.getDetail(scopedConversationId);
    }
  }
}
