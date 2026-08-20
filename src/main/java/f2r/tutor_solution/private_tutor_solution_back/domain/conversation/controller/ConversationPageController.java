package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.ConversationDetailResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class ConversationPageController {

  private final ConversationService conversationService;

  @GetMapping({"/conversation/dashboard", "/conversation/dashboard.html"})
  public String dashboard(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) Long conversationId,
      Model model) {
    Long scopedConversationId = id != null ? id : conversationId;
    if (scopedConversationId == null) {
      return "redirect:/stdt_dashboard";
    }

    ConversationDetailResponse detail = conversationService.getDetail(scopedConversationId);
    String mainTitle = detail.lessonName() != null ? detail.lessonName()
        : detail.title() != null ? detail.title()
        : "English Dashboard";
    model.addAttribute("mainTitle", mainTitle);
    return "student/conversation_dashboard";
  }
}
