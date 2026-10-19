package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** 문제 선택함(BASKET)과 문제집 제작 목록(BOOK). */
@Entity
@Table(name = "grammar_collections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrammarCollection extends BaseEntity {

  public enum CollectionKind { BASKET, BOOK }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "collection_id")
  private Long collectionId;

  @Column(name = "owner_user_id", nullable = false)
  private Long ownerUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private CollectionKind kind;

  @Column(nullable = false, length = 180)
  private String title;

  @Lob
  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "designcraft_job_id")
  private Long designcraftJobId;

  /** DesignCraft 전달 옵션(JSON): template, 문제지/정답해설지 분리, 페이지 크기 등. */
  @Column(name = "publish_options", columnDefinition = "JSON")
  private String publishOptions;

  @Builder
  public GrammarCollection(Long ownerUserId, CollectionKind kind, String title, String description) {
    this.ownerUserId = ownerUserId;
    this.kind = kind;
    this.title = title;
    this.description = description;
  }

  public void updateMeta(String title, String description) {
    if (title != null && !title.isBlank()) {
      this.title = title;
    }
    if (description != null) {
      this.description = description;
    }
  }

  public void markDesignCraftJob(Long jobId, String publishOptions) {
    this.designcraftJobId = jobId;
    if (publishOptions != null) {
      this.publishOptions = publishOptions;
    }
  }
}
