package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class TutorConversationPageController {

  @GetMapping("/tutor/conversation_dashboard")
  public String dashboard() {
    return "tutor/tutor_conversation_dashboard";
  }

  @GetMapping("/tutor/conversation/dashboard")
  public String detail() {
    return "tutor/tutor_conversation_detail";
  }

  @GetMapping({"/tutor/conversation/chat", "/tutor/conversation/chat.html"})
  public String chat() {
    return "tutor/tutor_conversation_chat";
  }

  @GetMapping({"/tutor/conversation/materials", "/tutor/conversation/materials.html"})
  public String materials(
      @RequestParam(defaultValue = "materials") String menu,
      @RequestParam(defaultValue = "ALL") String filter,
      Model model) {
    String activeMenu = normalizeMenu(menu);
    String materialFilter = normalizeFilter(filter);
    model.addAttribute("activeMenu", activeMenu);
    model.addAttribute("materialFilter", materialFilter);
    model.addAttribute("pageTitle", titleFor(activeMenu));
    model.addAttribute("pageSubtitle", subtitleFor(activeMenu));
    return "tutor/tutor_conversation_materials";
  }

  private String normalizeMenu(String menu) {
    return switch (menu == null ? "" : menu) {
      case "dashboard", "exam-materials", "study-materials", "materials", "chat" -> menu;
      default -> "materials";
    };
  }

  private String normalizeFilter(String filter) {
    return switch (filter == null ? "" : filter.toUpperCase()) {
      case "EXAM" -> "EXAM";
      case "GENERAL" -> "GENERAL";
      default -> "ALL";
    };
  }

  private String titleFor(String activeMenu) {
    return switch (activeMenu) {
      case "exam-materials" -> "시험지 대시보드";
      case "study-materials" -> "학습자료 대시보드";
      case "chat" -> "AI Chat";
      default -> "학교 자료 모음";
    };
  }

  private String subtitleFor(String activeMenu) {
    return switch (activeMenu) {
      case "exam-materials" -> "강사 기록에 첨부된 시험지를 한눈에 확인합니다.";
      case "study-materials" -> "강사 기록에 첨부된 일반 학습자료를 한눈에 확인합니다.";
      case "chat" -> "AI Chat은 별도 채팅 화면에서 이어갑니다.";
      default -> "강사 기록에 첨부된 시험지와 일반 자료를 한눈에 확인합니다.";
    };
  }

  @GetMapping("/tutor/conversations/{id}")
  public String open(@PathVariable Long id) {
    return "redirect:/tutor/conversation/dashboard?id=" + id;
  }
}
