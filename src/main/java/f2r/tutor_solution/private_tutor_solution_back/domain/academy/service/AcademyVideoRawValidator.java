package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public interface AcademyVideoRawValidator {
  ValidatedRaw validate(
      String sessionId,
      String token,
      String storageKey,
      long expectedSizeBytes,
      UploadWorkBudget budget,
      BooleanSupplier cancelled);

  void sweepStale(Predicate<ValidationIdentity> stillLive);

  record ValidatedRaw(
      String mediaType, long sizeBytes, long durationMs, int width, int height) {}

  record ValidationIdentity(String sessionId, String token) {}
}
