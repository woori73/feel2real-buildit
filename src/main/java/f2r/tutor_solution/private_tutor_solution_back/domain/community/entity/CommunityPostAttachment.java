package f2r.tutor_solution.private_tutor_solution_back.domain.community.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공개 커뮤니티 게시글 문서 첨부. (BuildIt Wiki 변형문제/자료 공유·요청에서 사용)
 * 본문 이미지({@link CommunityImage})와 별개 — 원본 문서를 그대로 보관한다.
 */
@Entity
@Table(name = "community_post_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPostAttachment extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "attachment_id")
  private Long attachmentId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id", nullable = false)
  private CommunityPost post;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
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
  public CommunityPostAttachment(CommunityPost post, User uploader, String originalFileName, String contentType,
      long fileSize, String storageKey) {
    this.post = post;
    this.uploader = uploader;
    this.originalFileName = originalFileName;
    this.contentType = contentType;
    this.fileSize = fileSize;
    this.storageKey = storageKey;
  }

  public void hide() {
    this.visible = false;
  }
}
