package f2r.tutor_solution.private_tutor_solution_back.domain.landing.service;

import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only publication of the single trailer designated by the user on 2026-10-29. */
@Service
@RequiredArgsConstructor
public class HomeAgentTrailerService {
  public static final String PROJECT_ID = "ai-agent-part1";
  public static final String FILE_ID = "8c2ef989-b739-4bf4-bcd7-b9582851e8e1";
  public static final String PUBLIC_PATH = "/api/landing/videos/home-agent-trailer/stream";
  private static final long MAX_BYTES = 100L * 1024 * 1024;
  private final JdbcTemplate db;
  private final FileStorage storage;

  // No request parameter, caller-provided ID, filename or URL can select another Studio file.
  static final String APPROVED_FILE_SQL = """
      SELECT f.storage_key, f.size_bytes, f.sha256 FROM studio_files f
      WHERE f.project_id=? AND f.id=? AND f.extension='mp4'
        AND EXISTS (
          SELECT 1 FROM studio_items i JOIN studio_revisions r
            ON r.project_id=i.project_id AND r.item_key=i.item_key AND r.id=i.approved_id
          WHERE r.project_id=f.project_id AND r.file_id=f.id AND r.kind='VIDEO')
      """;

  public record Media(String storageKey, long size, String sha256) {}

  /** Checks the live approval pointer on every read; missing metadata or DB failure fails closed. */
  public Optional<Media> approvedMedia() {
    try {
      return db.query(APPROVED_FILE_SQL,
              (rs, row) -> new Media(rs.getString(1), rs.getLong(2), rs.getString(3)),
              PROJECT_ID, FILE_ID).stream()
          .filter(m -> m.storageKey() != null && !m.storageKey().isBlank()
              && m.size() > 0 && m.size() <= MAX_BYTES
              && m.sha256() != null && m.sha256().matches("[a-f0-9]{64}"))
          .findFirst();
    } catch (DataAccessException unavailable) {
      // A failed optional trailer query must neither expose database details nor break the home page.
      return Optional.empty();
    }
  }

  public InputStream openRange(Media media, long start, long end) throws IOException {
    return storage.openRange(media.storageKey(), start, end);
  }
}
