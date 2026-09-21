package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "newstart_official_updates", indexes = {
    @Index(name = "idx_newstart_updates_public_feed", columnList = "review_status, featured, published_at"),
    @Index(name = "idx_newstart_updates_source", columnList = "review_status, source_type"),
    @Index(name = "idx_newstart_updates_dedup", columnList = "dedup_key", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewStartOfficialUpdate {

  public static final int TITLE_MAX_LENGTH = 200;
  public static final int SOURCE_NAME_MAX_LENGTH = 100;
  public static final int URL_MAX_LENGTH = 500;
  public static final int DOCUMENT_ID_MAX_LENGTH = 200;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "update_id")
  private Long updateId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 30)
  private SourceType sourceType;

  @Column(name = "source_name", nullable = false, length = SOURCE_NAME_MAX_LENGTH)
  private String sourceName;

  @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
  private String title;

  @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
  private String summary;

  @Column(name = "original_url", length = URL_MAX_LENGTH)
  private String originalUrl;

  @Column(name = "official_document_id", length = DOCUMENT_ID_MAX_LENGTH)
  private String officialDocumentId;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Column(name = "effective_from")
  private LocalDateTime effectiveFrom;

  @Column(name = "effective_to")
  private LocalDateTime effectiveTo;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 30)
  private Category category;

  @Enumerated(EnumType.STRING)
  @Column(name = "review_status", nullable = false, length = 20)
  private ReviewStatus reviewStatus = ReviewStatus.REVIEW;

  @Column(name = "featured", nullable = false)
  private boolean featured;

  @Column(name = "dedup_key", nullable = false, length = 512, unique = true)
  private String dedupKey;

  @Column(name = "reviewed_by", length = 100)
  private String reviewedBy;

  @Column(name = "reviewed_at")
  private LocalDateTime reviewedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  private NewStartOfficialUpdate(SourceType sourceType, String sourceName, String title,
      String summary, String originalUrl, String officialDocumentId, LocalDateTime publishedAt,
      LocalDateTime effectiveFrom, LocalDateTime effectiveTo, Category category, String dedupKey) {
    this.sourceType = sourceType;
    this.sourceName = sourceName;
    this.title = title;
    this.summary = summary;
    this.originalUrl = originalUrl;
    this.officialDocumentId = officialDocumentId;
    this.publishedAt = publishedAt;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.category = category;
    this.dedupKey = dedupKey;
    this.reviewStatus = ReviewStatus.REVIEW;
    this.featured = false;
  }

  public static NewStartOfficialUpdate review(SourceType sourceType, String sourceName, String title,
      String summary, String originalUrl, String officialDocumentId, LocalDateTime publishedAt,
      LocalDateTime effectiveFrom, LocalDateTime effectiveTo, Category category, String dedupKey) {
    return new NewStartOfficialUpdate(sourceType, sourceName, title, summary, originalUrl,
        officialDocumentId, publishedAt, effectiveFrom, effectiveTo, category, dedupKey);
  }

  public void publish(String reviewer) {
    if (this.reviewStatus != ReviewStatus.REVIEW) {
      throw new IllegalArgumentException("REVIEW 상태 자료만 게시할 수 있습니다.");
    }
    if (originalUrl == null || originalUrl.isBlank()) {
      throw new IllegalArgumentException("공식 원문 URL이 있어야 게시할 수 있습니다.");
    }
    this.reviewStatus = ReviewStatus.PUBLISHED;
    this.reviewedBy = reviewer;
    this.reviewedAt = LocalDateTime.now();
  }

  public void reject(String reviewer) {
    if (this.reviewStatus != ReviewStatus.REVIEW) {
      throw new IllegalArgumentException("REVIEW 상태 자료만 거절할 수 있습니다.");
    }
    this.reviewStatus = ReviewStatus.REJECTED;
    this.featured = false;
    this.reviewedBy = reviewer;
    this.reviewedAt = LocalDateTime.now();
  }

  public void archive(String reviewer) {
    if (this.reviewStatus != ReviewStatus.PUBLISHED) {
      throw new IllegalArgumentException("게시된 자료만 보관 처리할 수 있습니다.");
    }
    this.reviewStatus = ReviewStatus.ARCHIVED;
    this.featured = false;
    this.reviewedBy = reviewer;
    this.reviewedAt = LocalDateTime.now();
  }

  public void returnToReview(String reviewer) {
    if (this.reviewStatus != ReviewStatus.REJECTED) {
      throw new IllegalArgumentException("거절된 자료만 재검토로 되돌릴 수 있습니다.");
    }
    this.reviewStatus = ReviewStatus.REVIEW;
    this.reviewedBy = reviewer;
    this.reviewedAt = LocalDateTime.now();
  }

  public void setFeatured(boolean featured) {
    if (featured && this.reviewStatus != ReviewStatus.PUBLISHED) {
      throw new IllegalArgumentException("게시된 자료만 주요 업데이트로 지정할 수 있습니다.");
    }
    this.featured = featured;
  }

  public enum SourceType {
    NEWSTART_FUND,
    FSC,
    CCRS,
    FSS,
    PUBLIC_AGENCY
  }

  public enum Category {
    POLICY,
    ELIGIBILITY,
    APPLICATION,
    DEBT_ADJUSTMENT,
    NOTICE,
    OTHER
  }

  public enum ReviewStatus {
    REVIEW,
    PUBLISHED,
    REJECTED,
    ARCHIVED
  }
}
