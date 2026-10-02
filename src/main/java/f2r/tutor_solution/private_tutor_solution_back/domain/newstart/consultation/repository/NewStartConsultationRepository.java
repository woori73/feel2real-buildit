package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.ConsultationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상담 스레드 조회. 회원 경로는 항상 {@code memberUserId}가 함께 붙는 메서드만 쓴다 —
 * {@link #findByIdAndMemberUserId}가 소유권 HARD GATE다(다른 회원 상담은 조회 자체가 안 됨, 설계 §8).
 * 운영자 경로는 {@link #findAllByOrderByLastMessageAtDesc}(ADMIN 전용 controller/service에서만 호출).
 */
public interface NewStartConsultationRepository extends JpaRepository<NewStartConsultation, Long> {

  List<NewStartConsultation> findByMemberUserIdOrderByLastMessageAtDesc(Long memberUserId);

  Optional<NewStartConsultation> findByIdAndMemberUserId(Long consultationId, Long memberUserId);

  List<NewStartConsultation> findAllByOrderByLastMessageAtDesc();

  long countByStatus(ConsultationStatus status);
}
