package f2r.tutor_solution.private_tutor_solution_back.domain.community.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 질문/답변 Tiptap 본문에 삽입되는 업로드 이미지. JPEG 재인코딩 저장, 공개 serve. */
@Entity
@Table(name = "community_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityImage extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "image_id")
  private Long imageId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "uploaded_by", nullable = false)
  private User uploadedBy;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Column(name = "content_type", length = 100)
  private String contentType;

  @Column(name = "file_size")
  private Long fileSize;

  @Builder
  public CommunityImage(User uploadedBy, String storageKey, String contentType, Long fileSize) {
    this.uploadedBy = uploadedBy;
    this.storageKey = storageKey;
    this.contentType = contentType;
    this.fileSize = fileSize;
  }
}
