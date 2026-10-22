package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * RDS-backed counters work across restarts and application replicas. No tokens/IPs stored in clear.
 */
@Component
@RequiredArgsConstructor
public class McpRateLimit {
  private final JdbcTemplate db;

  public void check(String key, int maximum) {
    String hash = McpOAuth.hash(key);
    long minute = System.currentTimeMillis() / 60000;
    db.update("INSERT INTO mcp_rate_limits(bucket_key,window_start,calls) VALUES (?,?,1) ON DUPLICATE KEY UPDATE calls=IF(window_start=VALUES(window_start),calls+1,1),window_start=VALUES(window_start)",hash,minute);
    if (db.queryForObject(
            "SELECT calls FROM mcp_rate_limits WHERE bucket_key=?", Integer.class, hash)
        > maximum) throw McpOAuth.error(429, "rate_limit");
    if (Math.random() < 0.01)
      db.update("DELETE FROM mcp_rate_limits WHERE window_start<?", minute - 10);
  }
}
