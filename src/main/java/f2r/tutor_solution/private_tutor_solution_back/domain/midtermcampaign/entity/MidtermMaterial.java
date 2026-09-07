package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MATERIAL SHOWCASE 항목 1건(작업지시서 §12·§13). 기존 Wiki 자료를 {@link #sourceType} +
 * {@link #sourceMaterialId} 로 참조하고, 학부모용 표현 메타데이터만 여기 둔다. 파일 접근 판정은
 * {@link MidtermMaterialVisibility} 를 서버가 해석한다.
 */
@Entity
@Table(name = "midterm_material")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MidtermMaterial extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "midterm_material_id")
  private Long midtermMaterialId;

  @Column(name = "campaign_id", nullable = false)
  private Long campaignId;

  @Column(name = "school_collection_id")
  private Long schoolCollectionId;

  @Column(nullable = false, length = 30)
  private String category;

  @Column(name = "display_title", nullable = false, length = 160)
  private String displayTitle;

  @Column(length = 1000)
  private String blurb;

  @Column(name = "principal_comment", length = 1000)
  private String principalComment;

  @Column(columnDefinition = "TEXT")
  private String checklist;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 20)
  private MidtermMaterialSourceType sourceType;

  @Column(name = "source_material_id")
  private Long sourceMaterialId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MidtermMaterialVisibility visibility;

  @Column(name = "preview_page_cap")
  private Integer previewPageCap;

  @Column(name = "thumbnail_key", length = 500)
  private String thumbnailKey;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean published;

  @Builder
  private MidtermMaterial(Long campaignId, Long schoolCollectionId, String category,
      String displayTitle, String blurb, String principalComment, String checklist,
      MidtermMaterialSourceType sourceType, Long sourceMaterialId,
      MidtermMaterialVisibility visibility, Integer previewPageCap, String thumbnailKey,
      int sortOrder, boolean published) {
    this.campaignId = campaignId;
    this.schoolCollectionId = schoolCollectionId;
    this.category = category != null ? category : "OTHER";
    this.displayTitle = displayTitle;
    this.blurb = blurb;
    this.principalComment = principalComment;
    this.checklist = checklist;
    this.sourceType = sourceType != null ? sourceType : MidtermMaterialSourceType.NONE;
    this.sourceMaterialId = sourceMaterialId;
    this.visibility = visibility != null ? visibility : MidtermMaterialVisibility.PRIVATE;
    this.previewPageCap = previewPageCap;
    this.thumbnailKey = thumbnailKey;
    this.sortOrder = sortOrder;
    this.published = published;
  }

  /** 소속 학교 컬렉션 재지정(null = 캠페인 공용 자료). */
  public void setSchoolCollectionId(Long schoolCollectionId) {
    this.schoolCollectionId = schoolCollectionId;
  }

  public void update(String category, String displayTitle, String blurb, String principalComment,
      String checklist, MidtermMaterialSourceType sourceType, Long sourceMaterialId,
      MidtermMaterialVisibility visibility, Integer previewPageCap, String thumbnailKey,
      int sortOrder, boolean published) {
    this.category = category != null ? category : "OTHER";
    this.displayTitle = displayTitle;
    this.blurb = blurb;
    this.principalComment = principalComment;
    this.checklist = checklist;
    this.sourceType = sourceType != null ? sourceType : MidtermMaterialSourceType.NONE;
    this.sourceMaterialId = sourceMaterialId;
    this.visibility = visibility != null ? visibility : MidtermMaterialVisibility.PRIVATE;
    this.previewPageCap = previewPageCap;
    this.thumbnailKey = thumbnailKey;
    this.sortOrder = sortOrder;
    this.published = published;
  }
}
