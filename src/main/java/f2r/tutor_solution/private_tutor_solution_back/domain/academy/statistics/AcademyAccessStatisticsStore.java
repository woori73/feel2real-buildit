package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AcademyAccessStatisticsStore {
  private final JdbcTemplate jdbcTemplate;

  public boolean wasImported(String objectKey) {
    Long count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM alb_access_log_imports WHERE object_key_hash = ?",
        Long.class,
        sha256(objectKey));
    return count != null && count > 0;
  }

  public void increment(Long academyId, LocalDate accessDate, long count, Instant lastLogAt) {
    jdbcTemplate.update("""
        INSERT INTO academy_access_statistics
          (academy_id, total_request_count, last_log_at, created_at, updated_at)
        VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
        ON DUPLICATE KEY UPDATE
          total_request_count = total_request_count + VALUES(total_request_count),
          last_log_at = GREATEST(COALESCE(last_log_at, VALUES(last_log_at)), VALUES(last_log_at)),
          updated_at = CURRENT_TIMESTAMP(6)
        """, academyId, count, lastLogAt);
    jdbcTemplate.update("""
        INSERT INTO academy_access_daily_statistics
          (academy_id, access_date, request_count, created_at, updated_at)
        VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
        ON DUPLICATE KEY UPDATE
          request_count = request_count + VALUES(request_count),
          updated_at = CURRENT_TIMESTAMP(6)
        """, academyId, accessDate, count);
  }

  public void markImported(String objectKey, String etag, long importedRequestCount) {
    jdbcTemplate.update("""
        INSERT INTO alb_access_log_imports
          (object_key_hash, object_key, etag, imported_request_count, imported_at)
        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6))
        """, sha256(objectKey), objectKey, etag, importedRequestCount);
  }

  public SummaryRow summary(Long academyId) {
    List<SummaryRow> rows = jdbcTemplate.query("""
        SELECT total_request_count, last_log_at
        FROM academy_access_statistics
        WHERE academy_id = ?
        """, (rs, rowNum) -> new SummaryRow(
        rs.getLong("total_request_count"),
        rs.getTimestamp("last_log_at") == null ? null : rs.getTimestamp("last_log_at").toInstant()),
        academyId);
    return rows.isEmpty() ? new SummaryRow(0, null) : rows.getFirst();
  }

  public List<DailyRow> daily(Long academyId, LocalDate fromInclusive) {
    return jdbcTemplate.query("""
        SELECT access_date, request_count
        FROM academy_access_daily_statistics
        WHERE academy_id = ? AND access_date >= ?
        ORDER BY access_date
        """, (rs, rowNum) -> new DailyRow(
        rs.getDate("access_date").toLocalDate(), rs.getLong("request_count")),
        academyId, fromInclusive);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  public record SummaryRow(long totalRequestCount, Instant lastLogAt) {}
  public record DailyRow(LocalDate date, long requestCount) {}
}
