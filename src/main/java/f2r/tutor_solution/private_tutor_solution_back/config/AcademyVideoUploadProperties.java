package f2r.tutor_solution.private_tutor_solution_back.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.video")
public class AcademyVideoUploadProperties {
  public static final long REQUIRED_MAX_RAW_BYTES = 209_715_200L;
  public static final long REQUIRED_MAX_OUTPUT_BYTES = 209_715_200L;
  public static final long REQUIRED_MAX_DURATION_MS = 120_000L;
  public static final long REQUIRED_UPLOAD_DEADLINE_MS = 180_000L;
  public static final long REQUIRED_PART_SIZE_BYTES = 20L * 1024 * 1024;
  public static final int REQUIRED_MAX_COUNT = 10;

  private long maxRawBytes = REQUIRED_MAX_RAW_BYTES;
  private long maxDurationMs = REQUIRED_MAX_DURATION_MS;
  private long uploadDeadlineMs = REQUIRED_UPLOAD_DEADLINE_MS;
  private long uploadPartSizeBytes = REQUIRED_PART_SIZE_BYTES;
  private int uploadMaxCount = REQUIRED_MAX_COUNT;
  private int validationMaterializationPermits = 1;
  private long uploadRecoveryPollMs = 1_000;
  private long uploadRecoveryRetryMs = 5_000;
  private long uploadRecoveryLeaseMs = 15_000;
  private long uploadRecoveryHeartbeatMs = 5_000;
  private String validationTempDir =
      System.getProperty("java.io.tmpdir") + "/academy-video-validation";
  private String localUploadDir =
      System.getProperty("java.io.tmpdir") + "/academy-video-uploads";
  private long optimizationPollMs = 1_000;
  private long optimizationLeaseMs = 90_000;
  private long optimizationHeartbeatMs = 15_000;
  private long optimizationRetryMs = 60_000;
  private long optimizationAttemptBudgetMs = 600_000;
  private long optimizationStallMs = 90_000;
  private int optimizationPermits = 1;
  private long maxOutputBytes = REQUIRED_MAX_OUTPUT_BYTES;
  private String optimizationTempDir =
      System.getProperty("java.io.tmpdir") + "/academy-video-optimization";
  private long cleanupPollMs = 1_000;
  private long cleanupLeaseMs = 30_000;
  private long cleanupRetryBaseMs = 5_000;
  private long cleanupOrphanMinAgeMs = 300_000;
  private int cleanupReconciliationBatchSize = 100;
  // 방문자가 페이지를 읽는 동안 preload 로 받은 signed URL / 302 가 클릭 시점까지 살아 있어야
  // "첫 영상 첫 클릭 버퍼링"이 사라진다. S3 자격증명 갱신 주기보다 짧은 15분으로 둔다(상한 1시간).
  private long deliverySignedUrlTtlMs = 900_000;

  @PostConstruct
  void validate() {
    if (maxRawBytes != REQUIRED_MAX_RAW_BYTES
        || maxDurationMs != REQUIRED_MAX_DURATION_MS
        || uploadDeadlineMs != REQUIRED_UPLOAD_DEADLINE_MS
        || uploadPartSizeBytes != REQUIRED_PART_SIZE_BYTES
        || uploadMaxCount != REQUIRED_MAX_COUNT) {
      throw new IllegalStateException("Academy video product limits must not drift");
    }
    if (validationMaterializationPermits != 1
        || uploadRecoveryPollMs < 100
        || uploadRecoveryRetryMs < 100
        || uploadRecoveryLeaseMs < 2_000
        || uploadRecoveryHeartbeatMs < 100
        || uploadRecoveryHeartbeatMs * 2 >= uploadRecoveryLeaseMs) {
      throw new IllegalStateException("Academy video recovery/materialization settings are invalid");
    }
    if (optimizationPermits != 1
        || optimizationLeaseMs != 90_000
        || optimizationHeartbeatMs != 15_000
        || optimizationRetryMs != 60_000
        || optimizationAttemptBudgetMs != 600_000
        || optimizationStallMs != 90_000
        || maxOutputBytes != REQUIRED_MAX_OUTPUT_BYTES
        || optimizationPollMs < 100) {
      throw new IllegalStateException("Academy video optimization settings are invalid");
    }
    if (cleanupPollMs < 100
        || cleanupLeaseMs < 2_000
        || cleanupRetryBaseMs < 100
        || cleanupOrphanMinAgeMs < cleanupLeaseMs
        || cleanupReconciliationBatchSize < 1
        || cleanupReconciliationBatchSize > 1_000
        || deliverySignedUrlTtlMs < 1_000
        || deliverySignedUrlTtlMs > 3_600_000) {
      throw new IllegalStateException("Academy video cleanup settings are invalid");
    }
  }

  public long getMaxRawBytes() { return maxRawBytes; }
  public void setMaxRawBytes(long value) { maxRawBytes = value; }
  public long getMaxDurationMs() { return maxDurationMs; }
  public void setMaxDurationMs(long value) { maxDurationMs = value; }
  public long getUploadDeadlineMs() { return uploadDeadlineMs; }
  public void setUploadDeadlineMs(long value) { uploadDeadlineMs = value; }
  public long getUploadPartSizeBytes() { return uploadPartSizeBytes; }
  public void setUploadPartSizeBytes(long value) { uploadPartSizeBytes = value; }
  public int getUploadMaxCount() { return uploadMaxCount; }
  public void setUploadMaxCount(int value) { uploadMaxCount = value; }
  public int getValidationMaterializationPermits() { return validationMaterializationPermits; }
  public void setValidationMaterializationPermits(int value) {
    validationMaterializationPermits = value;
  }
  public long getUploadRecoveryPollMs() { return uploadRecoveryPollMs; }
  public void setUploadRecoveryPollMs(long value) { uploadRecoveryPollMs = value; }
  public long getUploadRecoveryRetryMs() { return uploadRecoveryRetryMs; }
  public void setUploadRecoveryRetryMs(long value) { uploadRecoveryRetryMs = value; }
  public long getUploadRecoveryLeaseMs() { return uploadRecoveryLeaseMs; }
  public void setUploadRecoveryLeaseMs(long value) { uploadRecoveryLeaseMs = value; }
  public long getUploadRecoveryHeartbeatMs() { return uploadRecoveryHeartbeatMs; }
  public void setUploadRecoveryHeartbeatMs(long value) { uploadRecoveryHeartbeatMs = value; }
  public String getValidationTempDir() { return validationTempDir; }
  public void setValidationTempDir(String value) { validationTempDir = value; }
  public String getLocalUploadDir() { return localUploadDir; }
  public void setLocalUploadDir(String value) { localUploadDir = value; }
  public long getOptimizationPollMs() { return optimizationPollMs; }
  public void setOptimizationPollMs(long value) { optimizationPollMs = value; }
  public long getOptimizationLeaseMs() { return optimizationLeaseMs; }
  public void setOptimizationLeaseMs(long value) { optimizationLeaseMs = value; }
  public long getOptimizationHeartbeatMs() { return optimizationHeartbeatMs; }
  public void setOptimizationHeartbeatMs(long value) { optimizationHeartbeatMs = value; }
  public long getOptimizationRetryMs() { return optimizationRetryMs; }
  public void setOptimizationRetryMs(long value) { optimizationRetryMs = value; }
  public long getOptimizationAttemptBudgetMs() { return optimizationAttemptBudgetMs; }
  public void setOptimizationAttemptBudgetMs(long value) { optimizationAttemptBudgetMs = value; }
  public long getOptimizationStallMs() { return optimizationStallMs; }
  public void setOptimizationStallMs(long value) { optimizationStallMs = value; }
  public int getOptimizationPermits() { return optimizationPermits; }
  public void setOptimizationPermits(int value) { optimizationPermits = value; }
  public long getMaxOutputBytes() { return maxOutputBytes; }
  public void setMaxOutputBytes(long value) { maxOutputBytes = value; }
  public String getOptimizationTempDir() { return optimizationTempDir; }
  public void setOptimizationTempDir(String value) { optimizationTempDir = value; }
  public long getCleanupPollMs() { return cleanupPollMs; }
  public void setCleanupPollMs(long value) { cleanupPollMs = value; }
  public long getCleanupLeaseMs() { return cleanupLeaseMs; }
  public void setCleanupLeaseMs(long value) { cleanupLeaseMs = value; }
  public long getCleanupRetryBaseMs() { return cleanupRetryBaseMs; }
  public void setCleanupRetryBaseMs(long value) { cleanupRetryBaseMs = value; }
  public long getCleanupOrphanMinAgeMs() { return cleanupOrphanMinAgeMs; }
  public void setCleanupOrphanMinAgeMs(long value) { cleanupOrphanMinAgeMs = value; }
  public int getCleanupReconciliationBatchSize() { return cleanupReconciliationBatchSize; }
  public void setCleanupReconciliationBatchSize(int value) {
    cleanupReconciliationBatchSize = value;
  }
  public long getDeliverySignedUrlTtlMs() { return deliverySignedUrlTtlMs; }
  public void setDeliverySignedUrlTtlMs(long value) { deliverySignedUrlTtlMs = value; }
}
