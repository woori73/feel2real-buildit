package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;

import java.time.LocalDateTime;

public record ConversationSummaryResponse(
    Long conversationId,
    String title,
    LocalDateTime createdAt,
    LocalDateTime lastMessageAt,
    int sourceCount,
    int messageCount,
    Long lessonId,
    String lessonName,
    String academyName
) {
  public static ConversationSummaryResponse from(Conversation conv, int sources, int messages) {
    return from(conv, sources, messages, null, null, null);
  }

  public static ConversationSummaryResponse from(
      Conversation conv,
      int sources,
      int messages,
      Long lessonId,
      String lessonName,
      String academyName) {
    return new ConversationSummaryResponse(
        conv.getConversationId(),
        conv.getTitle(),
        conv.getCreatedAt(),
        conv.getLastMessageAt(),
        sources,
        messages,
        lessonId,
        lessonName,
        academyName
    );
  }
}
