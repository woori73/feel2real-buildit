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

/** TUTOR Wiki 자료의 공개 여부만 저장한다. 자료 metadata와 HTML은 Wiki가 Source of Truth다. */
@Entity
@Table(name = "midterm_wiki_source_exposures")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MidtermWikiSourceExposure extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "exposure_id")
  private Long exposureId;

  @Column(name = "campaign_id", nullable = false)
  private Long campaignId;

  @Column(name = "source_kind", nullable = false, length = 20)
  private String sourceKind;

  @Column(name = "source_id", nullable = false)
  private long sourceId;

  @Column(name = "visible", nullable = false)
  private boolean visible;

  @Column(name = "featured", nullable = false)
  private boolean featured;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  private MidtermWikiSourceExposure(
      Long campaignId, String sourceKind, long sourceId,
      boolean visible, boolean featured, int sortOrder) {
    this.campaignId = campaignId;
    this.sourceKind = sourceKind;
    this.sourceId = sourceId;
    update(visible, featured, sortOrder);
  }

  public void update(boolean visible, boolean featured, int sortOrder) {
    this.visible = visible;
    this.featured = visible && featured;
    this.sortOrder = sortOrder;
  }
}
