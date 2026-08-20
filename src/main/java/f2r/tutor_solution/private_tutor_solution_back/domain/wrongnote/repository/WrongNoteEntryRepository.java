package f2r.tutor_solution.private_tutor_solution_back.domain.wrongnote.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wrongnote.entity.WrongNoteEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WrongNoteEntryRepository extends JpaRepository<WrongNoteEntry, Long> {
  List<WrongNoteEntry> findByConversation_ConversationIdOrderByCreatedAtDesc(Long conversationId);
}
