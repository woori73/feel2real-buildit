package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamRound;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WikiExamRoundRepository extends JpaRepository<WikiExamRound, Long> {

  /** 공개 회차 목록 (아카이브 선택용) — 최신 회차부터. */
  @Query("""
      select r from WikiExamRound r
      where r.deleted = false and r.published = true
      order by r.examYear desc, r.examMonth desc
      """)
  List<WikiExamRound> findPublicRounds();

  /** 현재 메인 회차. 지정이 없으면 서비스가 최신 공개 회차로 폴백한다. */
  @Query("""
      select r from WikiExamRound r
      where r.deleted = false and r.published = true and r.current = true
      """)
  Optional<WikiExamRound> findCurrentPublic();

  @Query("""
      select r from WikiExamRound r
      where r.deleted = false
      order by r.examYear desc, r.examMonth desc
      """)
  List<WikiExamRound> findAdminList();

  @Query("select r from WikiExamRound r where r.wikiExamRoundId = :id and r.deleted = false")
  Optional<WikiExamRound> findAdminById(@Param("id") Long id);

  Optional<WikiExamRound> findByExamYearAndExamMonthAndDeletedFalse(Integer examYear, Integer examMonth);

  @Query("select r from WikiExamRound r where r.deleted = false and r.current = true")
  List<WikiExamRound> findAllCurrent();
}
