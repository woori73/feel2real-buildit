package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service.ConversationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("ConversationMaterialsPageController — 학생 자료 뷰 3종 모델 분기")
class ConversationMaterialsPageControllerTest {

  private final ConversationService conversationService = mock(ConversationService.class);
  private final ConversationMaterialsPageController controller = new ConversationMaterialsPageController(conversationService);

  @Test
  @DisplayName("all — given /materials when 렌더 then activeMenu=materials, filter=ALL, conversation_materials 뷰")
  void all_sets_materials_filter_all() {
    // given
    Model model = new ConcurrentModel();

    // when
    String view = controller.all(null, null, model);

    // then
    assertThat(view).isEqualTo("student/conversation_materials");
    assertThat(model.getAttribute("activeMenu")).isEqualTo("materials");
    assertThat(model.getAttribute("materialFilter")).isEqualTo("ALL");
    assertThat(model.getAttribute("pageTitle")).isEqualTo("학교 자료 모음");
  }

  @Test
  @DisplayName("exams — given /exam-materials when 렌더 then activeMenu=exam-materials, filter=EXAM")
  void exams_sets_exam_filter() {
    // given
    Model model = new ConcurrentModel();

    // when
    String view = controller.exams(null, null, model);

    // then
    assertThat(view).isEqualTo("student/conversation_materials");
    assertThat(model.getAttribute("activeMenu")).isEqualTo("exam-materials");
    assertThat(model.getAttribute("materialFilter")).isEqualTo("EXAM");
    assertThat(model.getAttribute("pageTitle")).isEqualTo("시험지 대시보드");
  }

  @Test
  @DisplayName("study — given /study-materials when 렌더 then activeMenu=study-materials, filter=GENERAL")
  void study_sets_general_filter() {
    // given
    Model model = new ConcurrentModel();

    // when
    String view = controller.study(null, null, model);

    // then
    assertThat(view).isEqualTo("student/conversation_materials");
    assertThat(model.getAttribute("activeMenu")).isEqualTo("study-materials");
    assertThat(model.getAttribute("materialFilter")).isEqualTo("GENERAL");
    assertThat(model.getAttribute("pageTitle")).isEqualTo("학습자료 대시보드");
  }
  @Test
  @DisplayName("materials — given id when 렌더 then getDetail guard를 먼저 호출")
  void materials_with_id_calls_guard() {
    // given
    Model model = new ConcurrentModel();

    // when
    String view = controller.all(77L, null, model);

    // then
    assertThat(view).isEqualTo("student/conversation_materials");
    verify(conversationService).getDetail(77L);
  }

  @Test
  @DisplayName("materials — given conversationId when 렌더 then getDetail guard를 먼저 호출")
  void materials_with_conversation_id_calls_guard() {
    // given
    Model model = new ConcurrentModel();

    // when
    String view = controller.study(null, 88L, model);

    // then
    assertThat(view).isEqualTo("student/conversation_materials");
    verify(conversationService).getDetail(88L);
  }

  @Test
  @DisplayName("materials — given tutor-owned id when guard fails then 학생 자료 UI 렌더 전 403")
  void materials_guard_failure_propagates() {
    // given
    Model model = new ConcurrentModel();
    doThrow(new AccessDeniedException("학생 소유 대화만 접근할 수 있습니다."))
        .when(conversationService).getDetail(77L);

    // when & then
    assertThatThrownBy(() -> controller.exams(77L, null, model))
        .isInstanceOf(AccessDeniedException.class);
  }

}
