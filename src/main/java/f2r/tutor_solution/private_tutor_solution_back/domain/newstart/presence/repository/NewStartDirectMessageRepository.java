package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.entity.NewStartDirectMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewStartDirectMessageRepository extends JpaRepository<NewStartDirectMessage, Long> {

  Page<NewStartDirectMessage> findByReceiverUserIdOrderByMessageIdDesc(
      Long receiverUserId, Pageable pageable);

  Page<NewStartDirectMessage> findByReceiverUserIdAndMessageIdLessThanOrderByMessageIdDesc(
      Long receiverUserId, Long before, Pageable pageable);

  long countByReceiverUserIdAndIsReadFalse(Long receiverUserId);
}
