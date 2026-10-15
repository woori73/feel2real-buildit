package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AcademyRepository extends JpaRepository<Academy, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Academy a where a.academyId = :academyId")
  Optional<Academy> findForUpdateByAcademyId(@Param("academyId") Long academyId);

  Optional<Academy> findBySlug(String slug);

  Optional<Academy> findBySlugAndStatusAndVisibleTrue(String slug, AcademyStatus status);

  Optional<Academy> findByTutor_UserId(Long tutorUserId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Academy a where a.tutor.userId = :tutorUserId")
  Optional<Academy> findForUpdateByTutorUserId(@Param("tutorUserId") Long tutorUserId);

  Optional<Academy> findByHandle(String handle);

  Optional<Academy> findByHandleAndStatusAndVisibleTrue(String handle, AcademyStatus status);

  boolean existsBySlug(String slug);

  boolean existsByHandle(String handle);

  @Query("""
      select distinct a
      from Academy a
      left join a.subjects s
      where a.status = :status
        and a.visible = true
        and (:subjectId is null or s.subjectId = :subjectId)
        and (:targetGroup is null or :targetGroup member of a.targetGroups)
        and (:city is null or exists (
          select r.regionId
          from AcademyRegion r
          where r.academy = a
            and r.city = :city
            and (:district is null or r.district = :district or r.district = 'ALL')
        ))
      order by a.createdAt desc
      """)
  List<Academy> findPublicList(
      @Param("status") AcademyStatus status,
      @Param("subjectId") Integer subjectId,
      @Param("targetGroup") TargetGroup targetGroup,
      @Param("city") String city,
      @Param("district") String district);
}
