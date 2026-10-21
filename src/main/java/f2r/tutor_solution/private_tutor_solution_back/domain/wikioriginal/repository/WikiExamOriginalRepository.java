package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginal;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginalDocType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WikiExamOriginalRepository extends JpaRepository<WikiExamOriginal, Long> {

  @Query("""
      select w from WikiExamOriginal w
      where w.deleted = false and w.visible = true
        and (:grade is null or w.grade = :grade)
      order by w.createdAt desc, w.wikiExamOriginalId desc
      """)
  List<WikiExamOriginal> findPublicByGrade(@Param("grade") Integer grade);

  /** 회차(연도·월)·자료 종류까지 거른 공개 목록 — 메인 회차 섹션과 아카이브 공용. */
  @Query("""
      select w from WikiExamOriginal w
      where w.deleted = false and w.visible = true
        and (:grade is null or w.grade = :grade)
        and (:examYear is null or w.examYear = :examYear)
        and (:examMonth is null or w.examMonth = :examMonth)
        and (:docType is null or w.docType = :docType)
      order by w.grade asc, w.createdAt desc, w.wikiExamOriginalId desc
      """)
  List<WikiExamOriginal> findPublicFiltered(
      @Param("grade") Integer grade,
      @Param("examYear") Integer examYear,
      @Param("examMonth") Integer examMonth,
      @Param("docType") WikiExamOriginalDocType docType);

  @Query("""
      select w from WikiExamOriginal w
      where w.deleted = false
      order by w.createdAt desc, w.wikiExamOriginalId desc
      """)
  List<WikiExamOriginal> findAdminList();

  @Query("""
      select w from WikiExamOriginal w
      where w.wikiExamOriginalId = :id and w.deleted = false and w.visible = true
      """)
  Optional<WikiExamOriginal> findPublicById(@Param("id") Long id);

  @Query("""
      select w from WikiExamOriginal w
      where w.wikiExamOriginalId = :id and w.deleted = false
      """)
  Optional<WikiExamOriginal> findAdminById(@Param("id") Long id);

  /** 공개 Wiki 메인 "NEW 최신 업데이트" — 학년 무관 최신 공개 원본. */
  List<WikiExamOriginal> findTop8ByDeletedFalseAndVisibleTrueOrderByCreatedAtDesc();
}
