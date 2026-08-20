package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공개 가능 여부와 최적화 진행 상태를 분리한 학원 홍보 영상.
 *
 * <p>소유권은 중복 owner 컬럼이 아니라 {@code academy.tutor}로만 판정한다. 공개 소스 전환과
 * 삭제 tombstone은 일반 JPA {@code @Version}이 아닌 {@code sourceVersion} CAS로 보호한다.
 */
@Entity
@Table(name = "academy_videos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyVideo extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "video_id")
  private Long videoId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "academy_id", nullable = false)
  private Academy academy;

  @Column(name = "original_filename", length = 255)
  private String originalFilename;

  @Enumerated(EnumType.STRING)
  @Column(name = "lifecycle_status", nullable = false, length = 20)
  private VideoLifecycleStatus lifecycleStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "active_source", nullable = false, length = 20)
  private VideoActiveSource activeSource;

  @Column(name = "raw_storage_key", length = 500)
  private String rawStorageKey;

  @Column(name = "raw_media_type", length = 100)
  private String rawMediaType;

  @Column(name = "raw_size_bytes")
  private Long rawSizeBytes;

  @Column(name = "normalized_storage_key", length = 500)
  private String normalizedStorageKey;

  @Column(name = "normalized_media_type", length = 100)
  private String normalizedMediaType;

  @Column(name = "normalized_size_bytes")
  private Long normalizedSizeBytes;

  /** 정규화가 끝나기 전에는 poster가 없을 수 있다. */
  @Column(name = "thumbnail_key", length = 500)
  private String thumbnailKey;

  @Column(name = "duration_ms")
  private Long durationMs;

  @Column(name = "width")
  private Integer width;

  @Column(name = "height")
  private Integer height;

  /** 활성 소스 또는 lifecycle 전이마다 증가하는 명시적 CAS 버전. */
  @Column(name = "source_version", nullable = false)
  private long sourceVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "raw_cleanup_status", nullable = false, length = 20)
  private VideoRawCleanupStatus rawCleanupStatus;

  @Builder
  private AcademyVideo(
      Academy academy,
      String originalFilename,
      VideoLifecycleStatus lifecycleStatus,
      VideoActiveSource activeSource,
      String rawStorageKey,
      String rawMediaType,
      Long rawSizeBytes,
      String normalizedStorageKey,
      String normalizedMediaType,
      Long normalizedSizeBytes,
      String thumbnailKey,
      Long durationMs,
      Integer width,
      Integer height,
      Long sourceVersion,
      VideoRawCleanupStatus rawCleanupStatus,
      // V930 synchronous callers remain source-compatible until their story removes the old endpoint.
      String storageKey,
      Integer durationSec,
      Long sizeBytes,
      VideoStatus status) {
    this.academy = academy;
    this.originalFilename = originalFilename;
    this.lifecycleStatus = lifecycleStatus != null
        ? lifecycleStatus
        : status == VideoStatus.FAILED
            ? VideoLifecycleStatus.UNAVAILABLE
            : VideoLifecycleStatus.READY;
    this.activeSource = activeSource != null ? activeSource : VideoActiveSource.NORMALIZED;
    this.rawStorageKey = rawStorageKey;
    this.rawMediaType = rawMediaType;
    this.rawSizeBytes = rawSizeBytes;
    this.normalizedStorageKey =
        normalizedStorageKey != null ? normalizedStorageKey : storageKey;
    this.normalizedMediaType = normalizedMediaType != null
        ? normalizedMediaType
        : this.activeSource == VideoActiveSource.NORMALIZED ? "video/mp4" : null;
    this.normalizedSizeBytes = normalizedSizeBytes != null
        ? normalizedSizeBytes
        : sizeBytes != null ? sizeBytes : storageKey != null ? 1L : null;
    this.thumbnailKey = thumbnailKey;
    this.durationMs = durationMs != null
        ? durationMs
        : durationSec == null ? null : durationSec.longValue() * 1000;
    this.width = width;
    this.height = height;
    this.sourceVersion = sourceVersion != null ? sourceVersion : 0;
    this.rawCleanupStatus = rawCleanupStatus != null
        ? rawCleanupStatus
        : this.activeSource == VideoActiveSource.RAW
            ? VideoRawCleanupStatus.NOT_REQUIRED
            : VideoRawCleanupStatus.DONE;
    validateSourceContract();
  }

  /** 업로드 승인 트랜잭션이 생성하는 즉시 공개 가능한 RAW 영상. */
  public static AcademyVideo readyRaw(
      Academy academy,
      String originalFilename,
      String rawStorageKey,
      String rawMediaType,
      long rawSizeBytes,
      long durationMs,
      Integer width,
      Integer height) {
    return AcademyVideo.builder()
        .academy(academy)
        .originalFilename(originalFilename)
        .lifecycleStatus(VideoLifecycleStatus.READY)
        .activeSource(VideoActiveSource.RAW)
        .rawStorageKey(rawStorageKey)
        .rawMediaType(rawMediaType)
        .rawSizeBytes(rawSizeBytes)
        .durationMs(durationMs)
        .width(width)
        .height(height)
        .sourceVersion(0L)
        .rawCleanupStatus(VideoRawCleanupStatus.NOT_REQUIRED)
        .build();
  }

  public boolean belongsToAcademy(Long academyId) {
    return academyId != null && academy != null && academyId.equals(academy.getAcademyId());
  }

  /** 새 source resolution을 즉시 막고 stale worker publication CAS를 무효화한다. */
  public void markDeleting() {
    if (lifecycleStatus != VideoLifecycleStatus.DELETING) {
      lifecycleStatus = VideoLifecycleStatus.DELETING;
      if (activeSource == VideoActiveSource.RAW) {
        rawCleanupStatus = VideoRawCleanupStatus.PENDING;
      }
      sourceVersion++;
    }
  }

  public boolean publishNormalized(
      long expectedSourceVersion,
      String normalizedKey,
      long normalizedBytes,
      String thumbnail,
      long normalizedDurationMs,
      int normalizedWidth,
      int normalizedHeight) {
    if (lifecycleStatus != VideoLifecycleStatus.READY
        || activeSource != VideoActiveSource.RAW
        || sourceVersion != expectedSourceVersion) {
      return false;
    }
    if (isBlank(normalizedKey) || normalizedBytes < 1 || isBlank(thumbnail)) {
      throw new IllegalArgumentException("normalized source metadata is incomplete");
    }
    normalizedStorageKey = normalizedKey;
    normalizedMediaType = "video/mp4";
    normalizedSizeBytes = normalizedBytes;
    thumbnailKey = thumbnail;
    durationMs = normalizedDurationMs;
    width = normalizedWidth;
    height = normalizedHeight;
    activeSource = VideoActiveSource.NORMALIZED;
    rawCleanupStatus = VideoRawCleanupStatus.PENDING;
    sourceVersion++;
    return true;
  }

  /** Raw object absence has already been confirmed by the cleanup worker. */
  public boolean completeRawCleanup(String expectedRawKey) {
    if (activeSource != VideoActiveSource.NORMALIZED
        || rawCleanupStatus != VideoRawCleanupStatus.PENDING
        || expectedRawKey == null
        || !expectedRawKey.equals(rawStorageKey)) {
      return false;
    }
    rawStorageKey = null;
    rawMediaType = null;
    rawSizeBytes = null;
    rawCleanupStatus = VideoRawCleanupStatus.DONE;
    return true;
  }

  private void validateSourceContract() {
    if (academy == null) {
      throw new IllegalArgumentException("academy is required");
    }
    if (sourceVersion < 0) {
      throw new IllegalArgumentException("sourceVersion must be non-negative");
    }
    if (activeSource == VideoActiveSource.RAW) {
      boolean validRawLifecycle =
          lifecycleStatus == VideoLifecycleStatus.READY
              && rawCleanupStatus == VideoRawCleanupStatus.NOT_REQUIRED
          || lifecycleStatus == VideoLifecycleStatus.DELETING
              && rawCleanupStatus == VideoRawCleanupStatus.PENDING;
      if (!validRawLifecycle
          || isBlank(rawStorageKey)
          || isBlank(rawMediaType)
          || rawSizeBytes == null
          || rawSizeBytes <= 0) {
        throw new IllegalArgumentException("RAW source metadata is incomplete");
      }
      return;
    }
    if (isBlank(normalizedStorageKey)
        || !"video/mp4".equals(normalizedMediaType)
        || normalizedSizeBytes == null
        || normalizedSizeBytes <= 0
        || thumbnailKey == null
        || rawCleanupStatus == VideoRawCleanupStatus.NOT_REQUIRED) {
      throw new IllegalArgumentException("NORMALIZED source metadata is incomplete");
    }
    if (rawCleanupStatus == VideoRawCleanupStatus.PENDING
        && (isBlank(rawStorageKey) || isBlank(rawMediaType)
            || rawSizeBytes == null || rawSizeBytes <= 0)) {
      throw new IllegalArgumentException("pending raw cleanup requires raw metadata");
    }
    if (rawCleanupStatus == VideoRawCleanupStatus.DONE
        && (rawStorageKey != null || rawMediaType != null || rawSizeBytes != null)) {
      throw new IllegalArgumentException("completed raw cleanup must clear raw metadata");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /** @deprecated transitional view for the V930 DTO; do not persist optimization state here. */
  @Deprecated(forRemoval = true)
  public VideoStatus getStatus() {
    return lifecycleStatus == VideoLifecycleStatus.READY ? VideoStatus.READY : VideoStatus.FAILED;
  }

  /** @deprecated use the explicit raw/normalized source fields. */
  @Deprecated(forRemoval = true)
  public String getStorageKey() {
    return activeSource == VideoActiveSource.RAW ? rawStorageKey : normalizedStorageKey;
  }

  /** @deprecated duration is persisted exactly in milliseconds. */
  @Deprecated(forRemoval = true)
  public Integer getDurationSec() {
    return durationMs == null ? null : Math.toIntExact(durationMs / 1000);
  }

  /** @deprecated use the size for {@link #getActiveSource()}. */
  @Deprecated(forRemoval = true)
  public Long getSizeBytes() {
    return activeSource == VideoActiveSource.RAW ? rawSizeBytes : normalizedSizeBytes;
  }
}
