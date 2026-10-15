package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재원생 시험 전 복습영상(작업지시서 §2). {@link LessonProduct}(강좌) 하위의 실제 학습 콘텐츠다.
 *
 * <p>홈페이지 홍보영상({@code AcademyVideo})과 저장/재생 "패턴"만 공유하고(FileStorage,
 * VideoTranscoder, AcademyVideoDeliveryStorage) 행은 완전히 분리한다 — 정책(길이 제한, 공개 대상,
 * 삭제 규칙)이 서로 다르기 때문이다. Phase 1은 업로드가 요청 스레드에서 동기로 끝나므로
 * {@code AcademyVideo}처럼 raw/normalized 이중 소스나 CAS 버전을 두지 않는다 — 단일
 * {@code storageKey}(정규화된 mp4)만 가진다.
 */
@Entity
@Table(name = "lms_contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LmsContent extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "lms_content_id")
  private Long lmsContentId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "academy_id", nullable = false)
  private Academy academy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lesson_id", nullable = false)
  private LessonProduct lessonProduct;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Lob
  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "content_type", nullable = false, length = 20)
  private LmsContentType contentType;

  @Column(name = "original_filename", length = 255)
  private String originalFilename;

  @Column(name = "storage_key", length = 500)
  private String storageKey;

  @Column(name = "thumbnail_key", length = 500)
  private String thumbnailKey;

  @Column(name = "duration_sec")
  private Integer durationSec;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  @Column(name = "width")
  private Integer width;

  @Column(name = "height")
  private Integer height;

  /** 강좌(LessonProduct)의 학교/학년과 별개로, 콘텐츠 단위로 다시 지정할 수 있는 태그. */
  @Column(name = "school_name", length = 100)
  private String schoolName;

  @Column(name = "grade_label", length = 50)
  private String gradeLabel;

  @Column(name = "exam_name", length = 60)
  private String examName;

  @Column(name = "unit_label", length = 100)
  private String unitLabel;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private LmsContentStatus status;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Builder
  private LmsContent(
      Academy academy,
      LessonProduct lessonProduct,
      String title,
      String description,
      LmsContentType contentType,
      String originalFilename,
      String storageKey,
      String thumbnailKey,
      Integer durationSec,
      Long sizeBytes,
      Integer width,
      Integer height,
      String schoolName,
      String gradeLabel,
      String examName,
      String unitLabel,
      int displayOrder,
      LmsContentStatus status) {
    if (academy == null) throw new IllegalArgumentException("academy is required");
    if (lessonProduct == null) throw new IllegalArgumentException("lessonProduct is required");
    if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
    this.academy = academy;
    this.lessonProduct = lessonProduct;
    this.title = title;
    this.description = description;
    this.contentType = contentType != null ? contentType : LmsContentType.VIDEO;
    this.originalFilename = originalFilename;
    this.storageKey = storageKey;
    this.thumbnailKey = thumbnailKey;
    this.durationSec = durationSec;
    this.sizeBytes = sizeBytes;
    this.width = width;
    this.height = height;
    this.schoolName = schoolName;
    this.gradeLabel = gradeLabel;
    this.examName = examName;
    this.unitLabel = unitLabel;
    this.displayOrder = displayOrder;
    this.status = status != null ? status : LmsContentStatus.READY;
  }

  public void updateDetails(
      String title, String description, String schoolName, String gradeLabel,
      String examName, String unitLabel, int displayOrder) {
    if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
    this.title = title;
    this.description = description;
    this.schoolName = schoolName;
    this.gradeLabel = gradeLabel;
    this.examName = examName;
    this.unitLabel = unitLabel;
    this.displayOrder = displayOrder;
  }

  public void publish(LocalDateTime now) {
    if (storageKey == null || storageKey.isBlank()) {
      throw new IllegalStateException("영상이 업로드되지 않은 콘텐츠는 공개할 수 없습니다.");
    }
    this.status = LmsContentStatus.PUBLISHED;
    if (this.publishedAt == null) {
      this.publishedAt = now;
    }
  }

  public void hide() {
    this.status = LmsContentStatus.HIDDEN;
  }
}
