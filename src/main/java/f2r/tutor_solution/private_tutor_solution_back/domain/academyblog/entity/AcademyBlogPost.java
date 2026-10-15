package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "academy_blog_posts",
    uniqueConstraints = @UniqueConstraint(name = "uk_academy_blog_posts_academy_slug", columnNames = {"academy_id", "slug"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyBlogPost extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "post_id")
  private Long postId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "academy_id", nullable = false)
  private Academy academy;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "slug", nullable = false, length = 160)
  private String slug;

  @Column(name = "summary", length = 500)
  private String summary;

  @Lob
  @Column(name = "content_json", nullable = false, columnDefinition = "LONGTEXT")
  private String contentJson;

  @Lob
  @Column(name = "content_html", nullable = false, columnDefinition = "LONGTEXT")
  private String contentHtml;

  @Column(name = "meta_title", length = 200)
  private String metaTitle;

  @Column(name = "meta_description", length = 300)
  private String metaDescription;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private AcademyBlogPostStatus status = AcademyBlogPostStatus.DRAFT;

  @Column(name = "is_visible", nullable = false)
  private boolean visible = true;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Builder
  public AcademyBlogPost(Academy academy, String title, String slug, String summary,
      String contentJson, String contentHtml, String metaTitle, String metaDescription,
      AcademyBlogPostStatus status, Boolean visible, LocalDateTime publishedAt) {
    this.academy = academy;
    this.title = title;
    this.slug = slug;
    this.summary = summary;
    this.contentJson = contentJson;
    this.contentHtml = contentHtml;
    this.metaTitle = metaTitle;
    this.metaDescription = metaDescription;
    this.status = status == null ? AcademyBlogPostStatus.DRAFT : status;
    this.visible = visible == null || visible;
    this.publishedAt = publishedAt;
  }

  public void update(String title, String slug, String summary, String contentJson,
      String contentHtml, String metaTitle, String metaDescription,
      AcademyBlogPostStatus status, boolean visible) {
    this.title = title;
    this.slug = slug;
    this.summary = summary;
    this.contentJson = contentJson;
    this.contentHtml = contentHtml;
    this.metaTitle = metaTitle;
    this.metaDescription = metaDescription;
    if (status != null) this.status = status;
    this.visible = visible;
    if (this.status == AcademyBlogPostStatus.PUBLISHED && this.publishedAt == null) {
      this.publishedAt = LocalDateTime.now();
    }
    if (this.status == AcademyBlogPostStatus.DRAFT) {
      this.publishedAt = null;
    }
  }

  public boolean isPublicVisible() {
    return visible && status == AcademyBlogPostStatus.PUBLISHED;
  }
}
