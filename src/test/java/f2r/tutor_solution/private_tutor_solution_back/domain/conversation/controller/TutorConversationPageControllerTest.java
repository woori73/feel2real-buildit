package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TutorConversationPageControllerTest {

  private final MockMvc mockMvc = MockMvcBuilders
      .standaloneSetup(new TutorConversationPageController())
      .build();

  @Test
  @DisplayName("강사 대화 대시보드 URL은 tutor template view를 반환한다")
  void dashboardRouteReturnsTutorConversationDashboardView() throws Exception {
    mockMvc.perform(get("/tutor/conversation_dashboard"))
        .andExpect(status().isOk())
        .andExpect(view().name("tutor/tutor_conversation_dashboard"));
  }

  @Test
  @DisplayName("강사 대화 상세 URL은 tutor detail template view를 반환한다")
  void detailRouteReturnsTutorConversationDetailView() throws Exception {
    mockMvc.perform(get("/tutor/conversation/dashboard?id=7"))
        .andExpect(status().isOk())
        .andExpect(view().name("tutor/tutor_conversation_detail"));
  }


  @Test
  @DisplayName("강사 대화 AI Chat URL은 tutor chat template view를 반환한다")
  void chatRouteReturnsTutorConversationChatView() throws Exception {
    mockMvc.perform(get("/tutor/conversation/chat?id=7"))
        .andExpect(status().isOk())
        .andExpect(view().name("tutor/tutor_conversation_chat"));
  }

  @Test
  @DisplayName("강사 대화 자료 모음 URL은 tutor materials template view를 반환한다")
  void materialsRouteReturnsTutorConversationMaterialsView() throws Exception {
    mockMvc.perform(get("/tutor/conversation/materials?id=7"))
        .andExpect(status().isOk())
        .andExpect(view().name("tutor/tutor_conversation_materials"))
        .andExpect(model().attribute("activeMenu", "materials"))
        .andExpect(model().attribute("materialFilter", "ALL"));
  }

  @Test
  @DisplayName("강사 대화 자료 모음 URL은 학생식 사이드바 active menu와 자료 필터를 반영한다")
  void materialsRouteAppliesStudentSidebarMenuAndFilter() throws Exception {
    mockMvc.perform(get("/tutor/conversation/materials?id=7&menu=exam-materials&filter=EXAM"))
        .andExpect(status().isOk())
        .andExpect(view().name("tutor/tutor_conversation_materials"))
        .andExpect(model().attribute("activeMenu", "exam-materials"))
        .andExpect(model().attribute("materialFilter", "EXAM"))
        .andExpect(model().attribute("pageTitle", "시험지 대시보드"));
  }

  @Test
  @DisplayName("강사 대화 legacy open URL은 선택 id를 유지해 상세 화면으로 리다이렉트한다")
  void openRouteRedirectsToDetailWithConversationId() throws Exception {
    mockMvc.perform(get("/tutor/conversations/7"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/tutor/conversation/dashboard?id=7"));
  }

  @Test
  @DisplayName("강사 대화 대시보드 template은 favicon과 전용 static asset을 로드한다")
  void templateLoadsFaviconAndDedicatedAssets() throws Exception {
    String html = Files.readString(Path.of(
        "src/main/resources/templates/tutor/tutor_conversation_dashboard.html"));

    assertThat(html)
        .contains("fragments/_head :: favicon")
        .contains("/css/tutor_conversation_dashboard.css")
        .contains("/js/api.js")
        .contains("/js/tutor_conversation_dashboard.js");
  }
  @Test
  @DisplayName("강사 대화 대시보드는 학생 대시보드처럼 목록 전용 화면으로 유지한다")
  void dashboardTemplateKeepsListOnlyStudentDashboardFlow() throws Exception {
    String html = Files.readString(Path.of(
        "src/main/resources/templates/tutor/tutor_conversation_dashboard.html"));

    assertThat(html)
        .contains("data-page=\"dashboard\"")
        .contains("최근 학습기록")
        .doesNotContain("messageForm")
        .doesNotContain("messageInput");
  }

  @Test
  @DisplayName("강사 대화 상세 template은 별도 AI Chat 화면과 전용 static asset을 로드한다")
  void detailTemplateLoadsChatAssets() throws Exception {
    String html = Files.readString(Path.of(
        "src/main/resources/templates/tutor/tutor_conversation_detail.html"));

    assertThat(html)
        .contains("data-page=\"detail\"")
        .contains("card-materials")
        .contains("card-radar")
        .contains("card-feedback")
        .contains("btnChatPage")
        .contains("/css/tutor_conversation_dashboard.css")
        .contains("/js/tutor_conversation_dashboard.js")
        .doesNotContain("messageForm")
        .doesNotContain("messageInput");
  }

  @Test
  @DisplayName("강사 대화 자료 모음 template은 tutor context로 공용 materials asset을 로드한다")
  void materialsTemplateLoadsTutorContextAndAssets() throws Exception {
    String html = Files.readString(Path.of(
        "src/main/resources/templates/tutor/tutor_conversation_materials.html"));

    assertThat(html)
        .contains("MATERIAL_CONTEXT = 'TUTOR'")
        .contains("materialsGrid")
        .contains("data-menu=\"exam-materials\"")
        .contains("data-menu=\"study-materials\"")
        .contains("data-menu=\"materials\"")
        .contains("data-menu=\"chat\"")
        .doesNotContain("data-menu=\"list\"")
        .doesNotContain("data-menu=\"tutor-materials\"")
        .doesNotContain("data-menu=\"homework\"")
        .doesNotContain("data-menu=\"review\"")
        .contains("/js/conversation_materials.js")
        .contains("/js/conversation_upload.js");
  }

  @Test
  @DisplayName("강사 AI Chat template은 학생 chat layout asset을 tutor context로 재사용한다")
  void chatTemplateUsesStudentChatLayoutWithTutorContext() throws Exception {
    String html = Files.readString(Path.of(
        "src/main/resources/templates/tutor/tutor_conversation_chat.html"));

    assertThat(html)
        .contains("CONVERSATION_CONTEXT = 'TUTOR'")
        .contains("file-panel")
        .contains("chat-panel")
        .contains("data-menu=\"dashboard\"")
        .contains("data-menu=\"exam-materials\"")
        .contains("data-menu=\"study-materials\"")
        .contains("data-menu=\"materials\"")
        .contains("data-menu=\"chat\"")
        .contains("/js/conversation.js")
        .doesNotContain("data-menu=\"list\"")
        .doesNotContain("data-menu=\"tutor-materials\"")
        .doesNotContain("data-menu=\"homework\"")
        .doesNotContain("data-menu=\"review\"");
  }

}
