package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.MessageAuthorRole;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultationMessage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상담 메시지 조회. 회원 스레드는 {@link #findByConsultationIdAndAuthorRoleInOrderByMessageIdAsc}에
 * MEMBER·OPERATOR만 넣어 AI_RESEARCH(운영자 전용)가 절대 회원에게 내려가지 않게 한다(설계 §5).
 */
public interface NewStartConsultationMessageRepository extends JpaRepository<NewStartConsultationMessage, Long> {

  /** 운영자용 전체 스레드(AI_RESEARCH 포함). */
  List<NewStartConsultationMessage> findByConsultationIdOrderByMessageIdAsc(Long consultationId);

  /** 회원용 스레드 — 전달된 authorRole만(서비스가 MEMBER·OPERATOR만 넘긴다). */
  List<NewStartConsultationMessage> findByConsultationIdAndAuthorRoleInOrderByMessageIdAsc(
      Long consultationId, Collection<MessageAuthorRole> authorRoles);

  /** 읽음 커서 이후의 특정 작성자 메시지 수("새 답변 N건" / 운영자 미답변 질문 수). cursor는 null 대신 0을 넘긴다. */
  long countByConsultationIdAndAuthorRoleAndMessageIdGreaterThan(
      Long consultationId, MessageAuthorRole authorRole, Long messageId);

  /** 스레드의 마지막 메시지(읽음 커서 갱신용). */
  Optional<NewStartConsultationMessage> findTopByConsultationIdOrderByMessageIdDesc(Long consultationId);

  /** 특정 작성자의 첫 메시지(목록 미리보기용 "처음 질문"). */
  Optional<NewStartConsultationMessage> findFirstByConsultationIdAndAuthorRoleOrderByMessageIdAsc(
      Long consultationId, MessageAuthorRole authorRole);

  /** 특정 작성자의 마지막 메시지(운영자가 조사할 "회원 최신 질문" 특정용). */
  Optional<NewStartConsultationMessage> findTopByConsultationIdAndAuthorRoleOrderByMessageIdDesc(
      Long consultationId, MessageAuthorRole authorRole);
}
