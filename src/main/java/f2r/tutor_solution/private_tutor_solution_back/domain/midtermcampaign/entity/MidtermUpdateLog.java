package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** MIDTERM LOG 항목 1건(작업지시서 §22). 시험기간 동안 자료가 계속 갱신된다는 증거. */
@Entity
@Table(name = "midterm_update_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MidtermUpdateLog extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "update_log_id")
  private Long updateLogId;

  @Column(name = "campaign_id", nullable = false)
  private Long campaignId;

  @Column(name = "school_collection_id")
  private Long schoolCollectionId;

  @Column(name = "log_date", nullable = false)
  private LocalDate logDate;

  @Column(nullable = false, length = 200)
  private String headline;

  @Column(length = 1000)
  private String body;

  @Column(nullable = false)
  private boolean published;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  private MidtermUpdateLog(Long campaignId, Long schoolCollectionId, LocalDate logDate,
      String headline, String body, boolean published, int sortOrder) {
    this.campaignId = campaignId;
    this.schoolCollectionId = schoolCollectionId;
    this.logDate = logDate;
    this.headline = headline;
    this.body = body;
    this.published = published;
    this.sortOrder = sortOrder;
  }

  public void update(Long schoolCollectionId, LocalDate logDate, String headline, String body,
      boolean published, int sortOrder) {
    this.schoolCollectionId = schoolCollectionId;
    this.logDate = logDate;
    this.headline = headline;
    this.body = body;
    this.published = published;
    this.sortOrder = sortOrder;
  }
}
