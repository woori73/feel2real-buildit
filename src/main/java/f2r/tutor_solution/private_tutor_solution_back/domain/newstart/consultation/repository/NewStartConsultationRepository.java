package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.ConsultationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상담 스레드 조회. 회원 경로의 소유권 HARD GATE는 {@code findById}(PK) 조회 뒤
 * {@code NewStartConsultationService#ownConsultation}의 소유자 비교에서 강제한다(다른 회원 상담은 404/403, 설계 §8).
 * 운영자 목록은 {@link #findAllByOrderByLastMessageAtDesc}(ADMIN 전용 controller/service에서만 호출).
 */
public interface NewStartConsultationRepository extends JpaRepository<NewStartConsultation, Long> {

  List<NewStartConsultation> findByMemberUserIdOrderByLastMessageAtDesc(Long memberUserId);

  List<NewStartConsultation> findAllByOrderByLastMessageAtDesc();

  /** Muse ping용 — 가장 최근 상담 1건 (read-only). */
  Optional<NewStartConsultation> findTopByOrderByConsultationIdDesc();

  /** Muse ping용 — 미확인(NEW) 상담 건수 (read-only). */
  long countByStatus(ConsultationStatus status);
}
