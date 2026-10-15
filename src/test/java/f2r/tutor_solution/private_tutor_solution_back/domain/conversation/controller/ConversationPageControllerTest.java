package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.ConversationDetailResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service.ConversationService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@DisplayName("ConversationPageController — legacy dashboard fence")
class ConversationPageControllerTest {

  private final ConversationService conversationService = mock(ConversationService.class);
  private final ConversationPageController controller = new ConversationPageController(conversationService);

  @Test
  @DisplayName("id와 conversationId가 없으면 학생 대시보드로 리다이렉트한다")
  void dashboard_without_id_redirects() {
    // given
    Model model = new ConcurrentModel();

    // when
    String view = controller.dashboard(null, null, model);

    // then
    assertThat(view).isEqualTo("redirect:/stdt_dashboard");
  }

  @Test
  @DisplayName("conversationId query도 렌더 전 student-owned guard를 통과해야 한다")
  void dashboard_with_conversation_id_is_fenced() {
    // given
    Model model = new ConcurrentModel();
    org.mockito.Mockito.when(conversationService.getDetail(88L)).thenReturn(detail("수업 대화", null));

    // when
    String view = controller.dashboard(null, 88L, model);

    // then
    assertThat(view).isEqualTo("student/conversation_dashboard");
    assertThat(model.getAttribute("mainTitle")).isEqualTo("수업 대화");
    verify(conversationService).getDetail(88L);
  }

  @Test
  @DisplayName("guard 실패는 기본 제목 fallback으로 숨기지 않고 전파한다")
  void dashboard_guard_failure_is_propagated_before_render() {
    // given
    Model model = new ConcurrentModel();
    doThrow(new AccessDeniedException("학생 소유 대화만 접근할 수 있습니다."))
        .when(conversationService).getDetail(77L);

    // when & then
    assertThatThrownBy(() -> controller.dashboard(77L, null, model))
        .isInstanceOf(AccessDeniedException.class);
  }

  private static ConversationDetailResponse detail(String title, String lessonName) {
    return new ConversationDetailResponse(
        88L,
        title,
        LocalDateTime.now(),
        null,
        List.of(),
        List.of(),
        null,
        lessonName,
        null);
  }
}
