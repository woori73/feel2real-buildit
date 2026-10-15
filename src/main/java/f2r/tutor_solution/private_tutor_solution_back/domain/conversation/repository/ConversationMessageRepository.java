package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
  List<ConversationMessage> findByConversationConversationIdOrderByCreatedAtAsc(Long conversationId);
}
