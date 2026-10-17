package f2r.tutor_solution.private_tutor_solution_back.domain.academy.inquiry;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyInquiryRepository extends JpaRepository<AcademyInquiry, Long> {

  Page<AcademyInquiry> findByAcademyIdOrderByInquiryIdDesc(Long academyId, Pageable pageable);

  Page<AcademyInquiry> findByAcademyIdAndStatusOrderByInquiryIdDesc(
      Long academyId, InquiryStatus status, Pageable pageable);

  /** 다른 학원의 접수를 id만으로 찾지 못하게, 항상 academyId와 함께 조회한다. */
  Optional<AcademyInquiry> findByInquiryIdAndAcademyId(Long inquiryId, Long academyId);

  long countByAcademyIdAndCreatedAtGreaterThanEqual(Long academyId, LocalDateTime since);

  boolean existsByAcademyIdAndPhoneAndCreatedAtGreaterThanEqual(
      Long academyId, String phone, LocalDateTime since);
}
