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

  /**
   * 직접 업로드 영상인지 외부 링크 영상인지. 도입 이전(이 컬럼 이전) 행은 마이그레이션이
   * {@code DIRECT_UPLOAD} 로 backfill 하며, null 로 새로 만들어지지 않는다.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "source_kind", nullable = false, length = 20)
  private AcademyVideoSourceKind sourceKind;

  /** {@code EXTERNAL_LINK} 전용 — 외부 영상/콘텐츠 페이지 URL. 서버로 복제하지 않고 이 값만 저장한다. */
  @Column(name = "external_url", length = 2048)
  private String externalUrl;

  /** {@code EXTERNAL_LINK} 전용 — host 로만 판정한 출처 분류. */
  @Enumerated(EnumType.STRING)
  @Column(name = "external_provider", length = 20)
  private AcademyExternalVideoProvider externalProvider;

  /**
   * {@code EXTERNAL_LINK} 전용 — provider 가 공식적으로 iframe embed 를 허용할 때만 채워지는
   * 카드 내부 재생 URL(예: {@code https://www.youtube.com/embed/<id>}). null 이면 "[영상 보기]"
   * CTA 로만 노출한다(작업지시서 §13).
   */
  @Column(name = "external_embed_url", length = 2048)
  private String externalEmbedUrl;

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

  /**
   * 업로드 시점에 고른 용도(원장 소개 vs 수업). null이면 이 필드 도입 전 업로드된
   * 레거시 행이라 용도 미상 — 두 선택 목록 모두에 계속 노출한다({@link AcademyVideoPurpose}).
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", length = 20)
  private AcademyVideoPurpose purpose;

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
      AcademyVideoPurpose purpose,
      AcademyVideoSourceKind sourceKind,
      String externalUrl,
      AcademyExternalVideoProvider externalProvider,
      String externalEmbedUrl,
      // V930 synchronous callers remain source-compatible until their story removes the old endpoint.
      String storageKey,
      Integer durationSec,
      Long sizeBytes,
      VideoStatus status) {
    this.academy = academy;
    this.originalFilename = originalFilename;
    this.purpose = purpose;
    this.sourceKind = sourceKind != null ? sourceKind : AcademyVideoSourceKind.DIRECT_UPLOAD;
    this.externalUrl = externalUrl;
    this.externalProvider = externalProvider;
    this.externalEmbedUrl = externalEmbedUrl;
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

  /**
   * 외부에 이미 올라간 영상을 URL 자산으로만 등록한다(작업지시서 §3·§4). 서버로 다운로드/복제하지
   * 않으므로 storage key·트랜스코딩·썸네일이 없고 즉시 공개 가능(READY)하다. {@code embedUrl} 은
   * provider 가 iframe embed 를 공식 허용할 때만 넘긴다(없으면 CTA 링크로만 노출).
   */
  public static AcademyVideo externalLink(
      Academy academy,
      String title,
      String externalUrl,
      AcademyExternalVideoProvider provider,
      String embedUrl,
      AcademyVideoPurpose purpose) {
    return AcademyVideo.builder()
        .academy(academy)
        .originalFilename(title)
        .lifecycleStatus(VideoLifecycleStatus.READY)
        .activeSource(VideoActiveSource.EXTERNAL)
        .sourceKind(AcademyVideoSourceKind.EXTERNAL_LINK)
        .externalUrl(externalUrl)
        .externalProvider(provider)
        .externalEmbedUrl(embedUrl)
        .sourceVersion(0L)
        .rawCleanupStatus(VideoRawCleanupStatus.NOT_REQUIRED)
        .purpose(purpose)
        .build();
  }

  public boolean belongsToAcademy(Long academyId) {
    return academyId != null && academy != null && academyId.equals(academy.getAcademyId());
  }

  /** 외부 링크 영상이면 true — storage/스트리밍/tombstone 파이프라인 대신 URL 자산으로 다룬다. */
  public boolean isExternal() {
    return sourceKind == AcademyVideoSourceKind.EXTERNAL_LINK;
  }

  /**
   * 영상 관리 화면에서 용도를 명시적으로 (재)지정한다. 레거시 {@code purpose IS NULL}
   * ("용도 미지정") 영상의 최초 분류도 이 경로로 이루어진다 — 파일명/썸네일 등으로 자동
   * 추측하지 않고 사용자가 직접 고른 값만 받는다(Phase 3 정책).
   */
  public void reassignPurpose(AcademyVideoPurpose purpose) {
    if (purpose == null) {
      throw new IllegalArgumentException("purpose is required");
    }
    this.purpose = purpose;
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
    if (activeSource == VideoActiveSource.EXTERNAL) {
      if (sourceKind != AcademyVideoSourceKind.EXTERNAL_LINK) {
        throw new IllegalArgumentException("EXTERNAL active source requires EXTERNAL_LINK source kind");
      }
      if (isBlank(externalUrl)) {
        throw new IllegalArgumentException("external video URL is required");
      }
      if (!isBlank(rawStorageKey) || !isBlank(normalizedStorageKey) || !isBlank(thumbnailKey)) {
        throw new IllegalArgumentException("external video must not carry storage keys");
      }
      if (rawCleanupStatus != VideoRawCleanupStatus.NOT_REQUIRED) {
        throw new IllegalArgumentException("external video has no raw cleanup");
      }
      if (lifecycleStatus != VideoLifecycleStatus.READY
          && lifecycleStatus != VideoLifecycleStatus.DELETING) {
        throw new IllegalArgumentException("external video lifecycle must be READY or DELETING");
      }
      return;
    }
    if (sourceKind == AcademyVideoSourceKind.EXTERNAL_LINK) {
      throw new IllegalArgumentException("EXTERNAL_LINK source kind requires EXTERNAL active source");
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
