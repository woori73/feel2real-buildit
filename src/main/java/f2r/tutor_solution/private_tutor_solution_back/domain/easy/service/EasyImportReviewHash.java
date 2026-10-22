package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Same canonical DTO/hash already used by the financial collection preview. No raw archive I/O. */
public final class EasyImportReviewHash {
  private EasyImportReviewHash() {}

  public static final String NOTE_PREFIX = "financial-collector reviewed batch ";

  public static String of(EasyImportBatchResponse batch) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      new com.fasterxml.jackson.databind.ObjectMapper()
                          .findAndRegisterModules()
                          .writeValueAsBytes(batch)));
    } catch (Exception e) {
      throw new IllegalStateException("BATCH_REVIEW_HASH_FAILED");
    }
  }

  public static boolean matches(String note, EasyImportBatchResponse batch) {
    if (note == null || !note.matches(NOTE_PREFIX + "[a-f0-9]{64}")) return false;
    return MessageDigest.isEqual(
        note.substring(NOTE_PREFIX.length()).getBytes(StandardCharsets.US_ASCII),
        of(batch).getBytes(StandardCharsets.US_ASCII));
  }
}
