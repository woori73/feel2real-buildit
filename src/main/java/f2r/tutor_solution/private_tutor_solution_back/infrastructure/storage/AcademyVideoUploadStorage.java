package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

public interface AcademyVideoUploadStorage {

  UploadSession initiate(String sessionId, long declaredSizeBytes, int expectedPartCount);

  String uploadLocalPart(
      String sessionId, String uploadId, int partNumber, InputStream body, long contentLength)
      throws IOException;

  StorageState inspect(String stagingKey, String uploadId, Duration remaining);

  CompletedObject complete(
      String stagingKey, String uploadId, List<Part> orderedParts, Duration remaining);

  ObjectMetadata head(String stagingKey, Duration remaining);

  InputStream open(String stagingKey, Duration remaining) throws IOException;

  void abort(String stagingKey, String uploadId);

  /** Cleanup-path abort: succeeds only after abort is confirmed or the upload is confirmed absent. */
  default void abortForCleanup(String stagingKey, String uploadId) {
    abort(stagingKey, uploadId);
  }

  record UploadSession(String stagingKey, String uploadId, List<String> opaquePartUrls) {}
  record Part(int partNumber, String eTag, String checksum) {}
  record CompletedObject(String eTag, long sizeBytes) {}
  record ObjectMetadata(String eTag, long sizeBytes) {}
  record StorageState(boolean multipartOpen, boolean objectPresent) {}
}
