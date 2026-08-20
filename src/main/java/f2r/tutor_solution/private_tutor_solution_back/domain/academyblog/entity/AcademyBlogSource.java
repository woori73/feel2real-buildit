package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "academy_blog_source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyBlogSource extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "source_id")
  private Long sourceId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "academy_id", nullable = false)
  private Academy academy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "post_id")
  private AcademyBlogPost post;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 20)
  private AcademyBlogSourceType sourceType = AcademyBlogSourceType.IMAGE;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "stored_filename", length = 255)
  private String storedFilename;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Column(name = "file_url", length = 500)
  private String fileUrl;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  public AcademyBlogSource(Academy academy, AcademyBlogPost post, AcademyBlogSourceType sourceType,
      String originalFilename, String storedFilename, String storageKey, String fileUrl,
      String contentType, long fileSize, int sortOrder) {
    this.academy = academy;
    this.post = post;
    this.sourceType = sourceType == null ? AcademyBlogSourceType.IMAGE : sourceType;
    this.originalFilename = originalFilename;
    this.storedFilename = storedFilename;
    this.storageKey = storageKey;
    this.fileUrl = fileUrl;
    this.contentType = contentType;
    this.fileSize = fileSize;
    this.sortOrder = sortOrder;
  }

  public void attachTo(AcademyBlogPost post, int sortOrder, String fileUrl) {
    this.post = post;
    this.sortOrder = sortOrder;
    this.fileUrl = fileUrl;
  }
}
