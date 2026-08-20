package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lesson_community_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonCommunityAttachment extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "attachment_id")
  private Long attachmentId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "post_id")
  private LessonCommunityPost post;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "comment_id")
  private LessonCommunityComment comment;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "uploader_user_id", nullable = false)
  private User uploader;

  @Column(name = "original_file_name", nullable = false, length = 255)
  private String originalFileName;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Column(name = "is_visible", nullable = false)
  private boolean visible = true;

  @Builder
  public LessonCommunityAttachment(LessonCommunityPost post, LessonCommunityComment comment, User uploader,
      String originalFileName, String contentType, long fileSize, String storageKey) {
    this.post = post;
    this.comment = comment;
    this.uploader = uploader;
    this.originalFileName = originalFileName;
    this.contentType = contentType;
    this.fileSize = fileSize;
    this.storageKey = storageKey;
  }
}
