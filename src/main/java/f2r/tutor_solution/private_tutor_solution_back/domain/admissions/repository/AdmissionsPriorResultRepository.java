package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsPriorResult;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdmissionsPriorResultRepository extends JpaRepository<AdmissionsPriorResult, Long> {

    List<AdmissionsPriorResult> findByUniversityUniversityIdInAndStatus(Collection<Long> universityIds, KnowledgeStatus status);

    /** 적재 대조용: 시드에 든 대학의 기존 행만 읽는다(전체 행을 메모리에 올리지 않음). */
    List<AdmissionsPriorResult> findByUniversityUniversityIdIn(Collection<Long> universityIds);

    /** 보관(ARCHIVED)되지 않은 입시결과 행이 출처로 쓰는 공식자료 id. */
    @Query("select distinct r.source.materialId from AdmissionsPriorResult r where r.source is not null"
            + " and r.status <> f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED")
    List<Long> findSourceIdsInUse();

    @Query("select distinct r.university.universityId from AdmissionsPriorResult r")
    List<Long> findDistinctUniversityIds();

    @Query("select r.priorResultId from AdmissionsPriorResult r where r.status = :status")
    List<Long> findIdsByStatus(@Param("status") KnowledgeStatus status);

    /** 승격 대상 수(제외 자료를 출처로 둔 행 제외). */
    @Query("select count(r) from AdmissionsPriorResult r where r.academicYear = :year and r.status in :statuses"
            + " and (r.source is null or r.source.materialId not in :excludedMaterialIds)")
    long countPromotable(@Param("year") Integer year, @Param("statuses") Collection<KnowledgeStatus> statuses,
                         @Param("excludedMaterialIds") Collection<Long> excludedMaterialIds);

    @Query("select count(r) from AdmissionsPriorResult r where r.academicYear = :year and r.status in :statuses"
            + " and r.source.materialId in :excludedMaterialIds")
    long countExcluded(@Param("year") Integer year, @Param("statuses") Collection<KnowledgeStatus> statuses,
                       @Param("excludedMaterialIds") Collection<Long> excludedMaterialIds);

    /** 한 번에 승격(엔티티를 메모리에 올리지 않음). 제외 자료 id 목록이 비면 호출 측에서 -1 하나를 넣는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AdmissionsPriorResult r set r.status = :to where r.academicYear = :year and r.status in :statuses"
            + " and (r.source is null or r.source.materialId not in :excludedMaterialIds)")
    int promote(@Param("year") Integer year, @Param("statuses") Collection<KnowledgeStatus> statuses,
                @Param("excludedMaterialIds") Collection<Long> excludedMaterialIds, @Param("to") KnowledgeStatus to);
}
