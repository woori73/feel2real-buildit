package f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LessonApplicationEntityTest {

  @Test
  void cancel_marksApplicationCanceledAndSetsCanceledAt() {
    // given
    LessonApplication application = LessonApplication.builder()
        .status(LessonApplicationStatus.APPROVED)
        .appliedAt(LocalDateTime.of(2026, 6, 1, 10, 0))
        .build();
    LocalDateTime canceledAt = LocalDateTime.of(2026, 6, 2, 10, 0);

    // when
    application.cancel(canceledAt);

    // then
    assertThat(application.getStatus()).isEqualTo(LessonApplicationStatus.CANCELED);
    assertThat(application.getCanceledAt()).isEqualTo(canceledAt);
  }

  @Test
  void reactivate_marksCanceledApplicationApprovedAndClearsCanceledAt() {
    // given
    LessonApplication application = LessonApplication.builder()
        .status(LessonApplicationStatus.CANCELED)
        .canceledAt(LocalDateTime.of(2026, 6, 2, 10, 0))
        .build();
    LocalDateTime appliedAt = LocalDateTime.of(2026, 6, 3, 10, 0);

    // when
    application.reactivate(appliedAt);

    // then
    assertThat(application.getStatus()).isEqualTo(LessonApplicationStatus.APPROVED);
    assertThat(application.getAppliedAt()).isEqualTo(appliedAt);
    assertThat(application.getCanceledAt()).isNull();
  }

  @Test
  void linkConversation_attachesConversationToApplication() {
    // given
    LessonApplication application = LessonApplication.builder()
        .status(LessonApplicationStatus.APPROVED)
        .appliedAt(LocalDateTime.of(2026, 6, 1, 10, 0))
        .build();
    Conversation conversation = Conversation.forStudent(
        org.mockito.Mockito.mock(Student.class), "수학 학습 대화", null);

    // when
    application.linkConversation(conversation);

    // then
    assertThat(application.getConversation()).isEqualTo(conversation);
  }

  @Test
  void linkConversation_builderWithConversationSetsConversationField() {
    // given
    Conversation conversation = Conversation.forStudent(
        org.mockito.Mockito.mock(Student.class), "영어 학습 대화", null);

    // when
    LessonApplication application = LessonApplication.builder()
        .conversation(conversation)
        .status(LessonApplicationStatus.APPROVED)
        .appliedAt(LocalDateTime.of(2026, 6, 1, 10, 0))
        .build();

    // then
    assertThat(application.getConversation()).isEqualTo(conversation);
  }

  @Test
  void cancel_throwsWhenApplicationAlreadyCanceled() {
    // given
    LessonApplication application = LessonApplication.builder()
        .status(LessonApplicationStatus.CANCELED)
        .build();

    // when & then
    assertThatThrownBy(() -> application.cancel(LocalDateTime.now()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("이미 취소");
  }
}
