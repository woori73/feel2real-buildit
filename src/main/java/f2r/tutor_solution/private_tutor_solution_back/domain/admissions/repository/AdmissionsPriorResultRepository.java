package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsPriorResult;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionsPriorResultRepository extends JpaRepository<AdmissionsPriorResult, Long> {

    List<AdmissionsPriorResult> findByUniversityUniversityIdInAndStatus(Collection<Long> universityIds, KnowledgeStatus status);

    /** 적재 중복 판정용: 이번 시드에 나온 대학의 행만 읽는다(전체 표를 매번 읽지 않게). */
    List<AdmissionsPriorResult> findByUniversityUniversityIdIn(Collection<Long> universityIds);

    /** 보관되지 않은 입시결과 행이 출처로 쓰는 공식자료 id — 모집요강 최신본 적재 때 보관 대상에서 뺀다. */
    @org.springframework.data.jpa.repository.Query("select distinct p.source.materialId from AdmissionsPriorResult p"
            + " where p.source is not null and p.status <> :archived")
    List<Long> findSourceMaterialIdsInUse(@org.springframework.data.repository.query.Param("archived") KnowledgeStatus archived);

    /** 승격 대상 수(제외 자료에 딸린 행 포함 전체). */
    @org.springframework.data.jpa.repository.Query("select count(p) from AdmissionsPriorResult p"
            + " where p.academicYear = :year and p.status in :statuses")
    long countPromotable(@org.springframework.data.repository.query.Param("year") Integer year,
                         @org.springframework.data.repository.query.Param("statuses") Collection<KnowledgeStatus> statuses);

    /** 승격에서 제외할 자료(file_key)에 딸린 행 수. */
    @org.springframework.data.jpa.repository.Query("select count(p) from AdmissionsPriorResult p"
            + " where p.academicYear = :year and p.status in :statuses and p.source.fileKey in :fileKeys")
    long countPromotableFromFiles(@org.springframework.data.repository.query.Param("year") Integer year,
                                  @org.springframework.data.repository.query.Param("statuses") Collection<KnowledgeStatus> statuses,
                                  @org.springframework.data.repository.query.Param("fileKeys") Collection<String> fileKeys);

    /** 한 문장으로 승격한다(수십만 행을 엔티티로 읽지 않게). 제외 자료에 딸린 행은 남긴다. */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update AdmissionsPriorResult p set p.status = :active"
            + " where p.academicYear = :year and p.status in :statuses"
            + " and (p.source is null or p.source.materialId not in"
            + " (select m.materialId from AdmissionsOfficialMaterial m where m.fileKey in :fileKeys))")
    int promote(@org.springframework.data.repository.query.Param("year") Integer year,
                @org.springframework.data.repository.query.Param("statuses") Collection<KnowledgeStatus> statuses,
                @org.springframework.data.repository.query.Param("active") KnowledgeStatus active,
                @org.springframework.data.repository.query.Param("fileKeys") Collection<String> fileKeys);
}
