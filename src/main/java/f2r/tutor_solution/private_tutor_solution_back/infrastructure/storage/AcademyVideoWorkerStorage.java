package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Streaming storage operations used by the durable optimization worker.
 *
 * <p>Implementations must not buffer an entire video in heap.
 */
public interface AcademyVideoWorkerStorage {

  default String attemptKey(
      Long jobId, int attempt, String leaseToken, String filename) {
    return "academy-video/attempts/" + jobId + "/" + attempt + "/" + leaseToken + "/" + filename;
  }

  void materialize(String key, Path target, Duration remaining) throws IOException;

  StoredObject upload(String key, Path source, String mediaType, Duration remaining)
      throws IOException;

  StoredObject inspectWorkerObject(String key, Duration remaining);

  void deleteWorkerObject(String key);

  /**
   * Deterministic, bounded reconciliation surface. Implementations return only objects strictly
   * older than the cutoff so a live upload/attempt cannot be swept.
   */
  default List<StoredKey> listWorkerObjects(
      String deterministicPrefix, Instant olderThan, int limit) {
    return List.of();
  }

  record StoredObject(long sizeBytes) {}
  record StoredKey(String key, Instant lastModified) {}
}
