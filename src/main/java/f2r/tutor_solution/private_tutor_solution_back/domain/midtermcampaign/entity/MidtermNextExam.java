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
 * NEXT EXAM teaser CTA(캠페인당 1건, 작업지시서 §31). 중간고사 기록을 다음 기말고사 상담으로
 * 잇는 장기 마케팅 연결. 운영자가 활성/문구/목적지를 관리한다.
 */
@Entity
@Table(name = "midterm_next_exam")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MidtermNextExam extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "next_exam_id")
  private Long nextExamId;

  @Column(name = "campaign_id", nullable = false, unique = true)
  private Long campaignId;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "next_exam_name", length = 120)
  private String nextExamName;

  @Column(length = 200)
  private String headline;

  @Column(length = 1000)
  private String description;

  @Column(name = "cta_label", length = 40)
  private String ctaLabel;

  @Column(name = "cta_destination", length = 300)
  private String ctaDestination;

  @Builder
  private MidtermNextExam(Long campaignId, boolean enabled, String nextExamName, String headline,
      String description, String ctaLabel, String ctaDestination) {
    this.campaignId = campaignId;
    this.enabled = enabled;
    this.nextExamName = nextExamName;
    this.headline = headline;
    this.description = description;
    this.ctaLabel = ctaLabel;
    this.ctaDestination = ctaDestination;
  }

  public void update(boolean enabled, String nextExamName, String headline, String description,
      String ctaLabel, String ctaDestination) {
    this.enabled = enabled;
    this.nextExamName = nextExamName;
    this.headline = headline;
    this.description = description;
    this.ctaLabel = ctaLabel;
    this.ctaDestination = ctaDestination;
  }
}
