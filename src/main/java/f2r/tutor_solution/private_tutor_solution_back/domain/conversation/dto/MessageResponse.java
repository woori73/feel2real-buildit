package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationMessage;

import java.time.LocalDateTime;

public record MessageResponse(
    MessageInfo userMessage,
    MessageInfo assistantMessage
) {
  public record MessageInfo(Long messageId, String role, String content, LocalDateTime createdAt) {}

  public static MessageResponse of(ConversationMessage user, ConversationMessage assistant) {
    return new MessageResponse(
        new MessageInfo(user.getMessageId(), "USER", user.getContent(), user.getCreatedAt()),
        new MessageInfo(assistant.getMessageId(), "ASSISTANT", assistant.getContent(), assistant.getCreatedAt())
    );
  }
}
