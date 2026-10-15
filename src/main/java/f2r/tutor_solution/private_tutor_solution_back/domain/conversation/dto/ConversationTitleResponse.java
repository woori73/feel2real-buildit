package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;

/** 대화명 수정 응답. */
public record ConversationTitleResponse(Long conversationId, String title) {
  public static ConversationTitleResponse from(Conversation conv) {
    return new ConversationTitleResponse(conv.getConversationId(), conv.getTitle());
  }
}
