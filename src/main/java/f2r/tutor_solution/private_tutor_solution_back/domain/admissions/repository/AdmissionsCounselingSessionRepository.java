package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsCounselingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdmissionsCounselingSessionRepository extends JpaRepository<AdmissionsCounselingSession, Long> {
    Optional<AdmissionsCounselingSession> findBySessionToken(String sessionToken);
    Optional<AdmissionsCounselingSession> findByQuestionFingerprint(String questionFingerprint);
}
