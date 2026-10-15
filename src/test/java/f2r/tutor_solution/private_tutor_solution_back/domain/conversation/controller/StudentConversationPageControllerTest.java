package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service.ConversationService;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;

@DisplayName("StudentConversationPageController — legacy student-shaped page fence")
class StudentConversationPageControllerTest {

  private final ConversationService conversationService = mock(ConversationService.class);
  private final StudentConversationPageController controller = new StudentConversationPageController(conversationService);

  @ParameterizedTest(name = "{0}")
  @MethodSource("studentPages")
  @DisplayName("id가 있으면 렌더 전 ConversationService.getDetail guard를 통과해야 한다")
  void page_with_id_is_fenced(String name, PageCall call, String expectedView) {
    // when
    String view = call.render(controller, 77L, null);

    // then
    assertThat(view).isEqualTo(expectedView);
    verify(conversationService).getDetail(77L);
  }

  @Test
  @DisplayName("id가 없으면 shell 렌더만 하고 conversation guard는 호출하지 않는다")
  void page_without_id_skips_fence() {
    // when
    String view = controller.report(null, null);

    // then
    assertThat(view).isEqualTo("student/conversation_report");
    verify(conversationService, never()).getDetail(77L);
  }

  @Test
  @DisplayName("conversationId query도 id와 동일하게 렌더 전 guard를 통과해야 한다")
  void page_with_conversation_id_is_fenced() {
    // when
    String view = controller.chat(null, 88L);

    // then
    assertThat(view).isEqualTo("student/conversation_chat");
    verify(conversationService).getDetail(88L);
  }

  @Test
  @DisplayName("tutor-owned id 등 student guard 실패는 학생 UI 렌더 전에 전파된다")
  void guard_failure_is_propagated_before_render() {
    // given
    doThrow(new AccessDeniedException("학생 소유 대화만 접근할 수 있습니다."))
        .when(conversationService).getDetail(77L);

    // when & then
    assertThatThrownBy(() -> controller.homework(77L, null))
        .isInstanceOf(AccessDeniedException.class);
  }

  static Stream<Arguments> studentPages() {
    return Stream.of(
        Arguments.of("chat", (PageCall) StudentConversationPageController::chat, "student/conversation_chat"),
        Arguments.of("tutor-materials", (PageCall) StudentConversationPageController::tutorMaterials, "student/conversation_tutor_materials"),
        Arguments.of("homework", (PageCall) StudentConversationPageController::homework, "student/conversation_homework"),
        Arguments.of("report", (PageCall) StudentConversationPageController::report, "student/conversation_report"),
        Arguments.of("review", (PageCall) StudentConversationPageController::review, "student/conversation_review"));
  }

  @FunctionalInterface
  interface PageCall {
    String render(StudentConversationPageController controller, Long id, Long conversationId);
  }
}
