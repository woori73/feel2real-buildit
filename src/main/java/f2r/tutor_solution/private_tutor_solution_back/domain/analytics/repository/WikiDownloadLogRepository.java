package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiDownloadLog;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WikiDownloadLogRepository extends JpaRepository<WikiDownloadLog, Long> {

  @Query("select count(d) from WikiDownloadLog d where d.downloadDate between :from and :to")
  long countDownloads(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("select count(d) from WikiDownloadLog d")
  long countDownloadsAllTime();

  /** 관리자 Analytics "최근 다운로드" — 최신순. */
  @Query("select d from WikiDownloadLog d order by d.downloadedAt desc")
  List<WikiDownloadLog> findRecent(Pageable pageable);

  /** 관리자 Analytics "다운로드 TOP" — 자료별 누적 다운로드 수 내림차순. */
  @Query("""
      select d.materialCategory as category, d.materialTitle as title, count(d) as downloadCount
      from WikiDownloadLog d
      group by d.materialCategory, d.materialId, d.materialTitle
      order by count(d) desc
      """)
  List<TopDownloadRow> findTopDownloads(Pageable pageable);

  interface TopDownloadRow {
    String getCategory();

    String getTitle();

    long getDownloadCount();
  }

  /**
   * 공개 Wiki "지금 많이 받는 자료" — 주어진 시간창 안의 다운로드를 (category + materialId) 단위로 집계.
   * 자료 정체성은 {@code material_id} 기준이며 title 문자열로 묶지 않는다(같은 자료가 이름이 바뀌어도 하나로).
   * 동률은 (다운로드 수 desc, category asc, materialId asc) 로 deterministic 하게 정렬한다.
   */
  @Query("""
      select d.materialCategory as category,
             d.materialId as materialId,
             max(d.materialTitle) as title,
             count(d) as downloadCount
      from WikiDownloadLog d
      where d.downloadedAt >= :from and d.downloadedAt < :to
      group by d.materialCategory, d.materialId
      order by count(d) desc, d.materialCategory asc, d.materialId asc
      """)
  List<PopularDownloadRow> aggregateBetween(
      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

  /** 특정 시간창의 총 다운로드 건수(fallback 기간 판단용). */
  @Query("select count(d) from WikiDownloadLog d where d.downloadedAt >= :from and d.downloadedAt < :to")
  long countBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

  interface PopularDownloadRow {
    String getCategory();

    Long getMaterialId();

    String getTitle();

    long getDownloadCount();
  }
}
