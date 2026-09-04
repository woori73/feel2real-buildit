package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiDownloadLog;
import java.time.LocalDate;
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
}
