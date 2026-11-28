package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyGradingRecord;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyGradingPhotoTarget;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyGradingRecordRepository extends JpaRepository<EasyGradingRecord, Long> {
  @Query("""
      select new f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyGradingPhotoTarget(
        g.gradingId, s.studentId, s.name, g.gradedOn, g.subjectArea, g.material, g.status,
        (select count(p) from EasyGradingPhoto p where p.grading = g))
      from EasyGradingRecord g join g.student s
      where g.business.businessId = :businessId and (:before is null or g.gradingId < :before)
      order by g.gradingId desc
      """)
  List<EasyGradingPhotoTarget> photoTargets(@Param("businessId") Long businessId, @Param("before") Long before, Pageable page);

  boolean existsByGradingIdAndBusiness_BusinessIdAndStudent_StudentId(Long gradingId, Long businessId, Long studentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select g from EasyGradingRecord g where g.business.businessId = :businessId and g.gradingId = :gradingId")
  Optional<EasyGradingRecord> lockForPhoto(@Param("businessId") Long businessId, @Param("gradingId") Long gradingId);

  List<EasyGradingRecord> findByBusiness_BusinessIdAndStudent_StudentIdOrderByGradedOnDescGradingIdDesc(
      Long businessId, Long studentId);

  List<EasyGradingRecord> findByLessonRecord_LessonRecordId(Long lessonRecordId);

  List<EasyGradingRecord> findByBusiness_BusinessIdAndStatusOrderByGradedOnDescGradingIdDesc(Long businessId, String status);
}
