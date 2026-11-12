package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridge between the Visible Local Browser Collector's KB download and the KB import pipeline.
 *
 * <p>Owns exactly one job: integrity-gate a collector-delivered file (filename/size/SHA-256/
 * query range as reported by the collector) and hand its bytes to {@link KbCardImportService}.
 * No approval, no posting, no classification decisions here — events enter as PENDING_REVIEW
 * through the import service, and the existing recommendation chain stays untouched.
 *
 * <p>Fail-closed: size or SHA-256 mismatch aborts before any parse (messages carry metadata
 * only, never file content). Cancellation semantics stay UNKNOWN — the import service's
 * fail-closed rules apply unchanged.
 */
@Service
@RequiredArgsConstructor
public class KbCollectorIngestService {

  public record CollectedFile(
      byte[] content,
      String filename,
      long reportedSize,
      String reportedSha256Hex,
      LocalDate queryFrom,
      LocalDate queryTo) {
  }

  public record IngestSummary(
      String filename,
      long size,
      String sha256Hex,
      LocalDate queryFrom,
      LocalDate queryTo,
      int sourceRows,
      int newEvents,
      int duplicateEvents,
      int foreignRows) {
  }

  private final KbCardImportService importService;

  @Transactional
  public IngestSummary ingestCollected(Long businessId, CollectedFile file) {
    if (businessId == null) throw new IllegalArgumentException("businessId is required.");
    if (file == null || file.content() == null || file.content().length == 0) {
      throw new IllegalArgumentException("Collected KB file is empty.");
    }
    if (file.content().length != file.reportedSize()) {
      throw new IllegalArgumentException("Collected KB file size mismatch for " + file.filename() + ".");
    }
    String actualSha256 = sha256Hex(file.content());
    if (!MessageDigest.isEqual(
        actualSha256.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        file.reportedSha256Hex().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
      throw new IllegalArgumentException("Collected KB file SHA-256 mismatch for " + file.filename() + ".");
    }

    KbCardImportService.ImportResult result =
        importService.importFor(businessId, new ByteArrayInputStream(file.content()));

    return new IngestSummary(file.filename(), file.content().length, actualSha256,
        file.queryFrom(), file.queryTo(), result.sourceRows(),
        result.newPurchaseEvents() + result.newCancellationEvents(),
        result.duplicatePurchaseEvents() + result.duplicateCancellationEvents(),
        result.foreignRows());
  }

  static String sha256Hex(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
