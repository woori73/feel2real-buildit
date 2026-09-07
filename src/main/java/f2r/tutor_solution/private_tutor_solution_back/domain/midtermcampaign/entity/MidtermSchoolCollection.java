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

/**
 * 학교 + 학년 단위 컬렉션(Shopify Product Collection, 작업지시서 §6·§7). 원장 브리핑 영상은
 * 기존 {@code academy_videos} 를 {@link #briefingVideoId} 로 참조만 한다(연결/해제).
 */
@Entity
@Table(name = "midterm_school_collection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MidtermSchoolCollection extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "school_collection_id")
  private Long schoolCollectionId;

  @Column(name = "campaign_id", nullable = false)
  private Long campaignId;

  @Column(name = "school_name", nullable = false, length = 100)
  private String schoolName;

  @Column(nullable = false)
  private int grade;

  @Column(name = "exam_title", length = 120)
  private String examTitle;

  @Column(name = "status_label", length = 60)
  private String statusLabel;

  @Column(name = "prep_headline", length = 200)
  private String prepHeadline;

  @Column(name = "briefing_video_id")
  private Long briefingVideoId;

  @Column(name = "briefing_title", length = 160)
  private String briefingTitle;

  @Column(name = "briefing_description", length = 600)
  private String briefingDescription;

  @Column(name = "briefing_shoot_date")
  private LocalDate briefingShootDate;

  @Column(name = "briefing_publish_date")
  private LocalDate briefingPublishDate;

  @Column(name = "briefing_published", nullable = false)
  private boolean briefingPublished;

  @Column(name = "exam_feature_note", columnDefinition = "TEXT")
  private String examFeatureNote;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean published;

  @Builder
  private MidtermSchoolCollection(Long campaignId, String schoolName, int grade, String examTitle,
      String statusLabel, String prepHeadline, Long briefingVideoId, String briefingTitle,
      String briefingDescription, LocalDate briefingShootDate, LocalDate briefingPublishDate,
      boolean briefingPublished, String examFeatureNote, int sortOrder, boolean published) {
    this.campaignId = campaignId;
    this.schoolName = schoolName;
    this.grade = grade;
    this.examTitle = examTitle;
    this.statusLabel = statusLabel;
    this.prepHeadline = prepHeadline;
    this.briefingVideoId = briefingVideoId;
    this.briefingTitle = briefingTitle;
    this.briefingDescription = briefingDescription;
    this.briefingShootDate = briefingShootDate;
    this.briefingPublishDate = briefingPublishDate;
    this.briefingPublished = briefingPublished;
    this.examFeatureNote = examFeatureNote;
    this.sortOrder = sortOrder;
    this.published = published;
  }

  public void updateBasics(String schoolName, int grade, String examTitle, String statusLabel,
      String prepHeadline, String examFeatureNote, int sortOrder, boolean published) {
    this.schoolName = schoolName;
    this.grade = grade;
    this.examTitle = examTitle;
    this.statusLabel = statusLabel;
    this.prepHeadline = prepHeadline;
    this.examFeatureNote = examFeatureNote;
    this.sortOrder = sortOrder;
    this.published = published;
  }

  public void updateBriefing(Long briefingVideoId, String briefingTitle, String briefingDescription,
      LocalDate briefingShootDate, LocalDate briefingPublishDate, boolean briefingPublished) {
    this.briefingVideoId = briefingVideoId;
    this.briefingTitle = briefingTitle;
    this.briefingDescription = briefingDescription;
    this.briefingShootDate = briefingShootDate;
    this.briefingPublishDate = briefingPublishDate;
    this.briefingPublished = briefingPublished;
  }
}
