package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문항 THREAD 글의 문서/이미지 첨부. {@code lesson_community_attachments} 와 동일한 형태의 별개 테이블이다.
 * 파일은 {@code FileStorage.saveRaw} 로 원본 그대로 저장하고 다운로드로만 제공한다.
 */
@Entity
@Table(name = "question_thread_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionThreadAttachment extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "attachment_id")
  private Long attachmentId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "post_id", nullable = false)
  private QuestionThreadPost post;

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
  public QuestionThreadAttachment(QuestionThreadPost post, User uploader, String originalFileName,
      String contentType, long fileSize, String storageKey) {
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
