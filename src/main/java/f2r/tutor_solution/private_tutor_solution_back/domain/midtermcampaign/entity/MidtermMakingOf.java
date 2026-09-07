package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MAKING OF 항목 1건(작업지시서 §18·§19). Phase 1 은 운영자 입력만 받는다 — Public UI(Swipe
 * Gallery)는 Phase 2. {@code stage} = SOURCE|ANALYSIS|WORKSHEET|VARIATION|FINAL.
 */
@Entity
@Table(name = "midterm_making_of")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MidtermMakingOf extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "making_of_id")
  private Long makingOfId;

  @Column(name = "campaign_id", nullable = false)
  private Long campaignId;

  @Column(name = "school_collection_id")
  private Long schoolCollectionId;

  @Column(nullable = false, length = 20)
  private String stage;

  @Column(length = 300)
  private String caption;

  @Column(name = "image_key", length = 500)
  private String imageKey;

  @Column(name = "video_id")
  private Long videoId;

  @Column(nullable = false)
  private boolean published;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  private MidtermMakingOf(Long campaignId, Long schoolCollectionId, String stage, String caption,
      String imageKey, Long videoId, boolean published, int sortOrder) {
    this.campaignId = campaignId;
    this.schoolCollectionId = schoolCollectionId;
    this.stage = stage;
    this.caption = caption;
    this.imageKey = imageKey;
    this.videoId = videoId;
    this.published = published;
    this.sortOrder = sortOrder;
  }

  public void update(Long schoolCollectionId, String stage, String caption, String imageKey,
      Long videoId, boolean published, int sortOrder) {
    this.schoolCollectionId = schoolCollectionId;
    this.stage = stage;
    this.caption = caption;
    this.imageKey = imageKey;
    this.videoId = videoId;
    this.published = published;
    this.sortOrder = sortOrder;
  }
}
