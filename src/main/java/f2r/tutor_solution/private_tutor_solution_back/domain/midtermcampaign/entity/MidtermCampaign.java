package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 학원의 "내신 LIVE" 캠페인(학원당 활성 1개). 시험이 끝나도 삭제하지 않고 {@link #state}만
 * 전이한다(작업지시서 §28·§30). academy 스코프라 다른 Academy 홈페이지에 영향이 없다.
 */
@Entity
@Table(name = "midterm_campaign")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MidtermCampaign extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "campaign_id")
  private Long campaignId;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "academy_id", nullable = false, unique = true)
  private Academy academy;

  @Column(name = "exam_name", nullable = false, length = 120)
  private String examName;

  @Column(name = "exam_year", nullable = false)
  private int examYear;

  @Column(nullable = false, length = 20)
  private String semester;

  @Column(name = "exam_type", nullable = false, length = 40)
  private String examType;

  @Column(name = "period_start")
  private LocalDate periodStart;

  @Column(name = "period_end")
  private LocalDate periodEnd;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MidtermCampaignState state;

  @Column(name = "hero_eyebrow", length = 120)
  private String heroEyebrow;

  @Column(name = "hero_title", length = 200)
  private String heroTitle;

  @Column(name = "hero_body", length = 600)
  private String heroBody;

  @Column(name = "hero_cta_label", length = 40)
  private String heroCtaLabel;

  @Column(nullable = false)
  private boolean published;

  @Column(name = "popup_enabled", nullable = false)
  private boolean popupEnabled;

  @Column(name = "popup_headline", length = 120)
  private String popupHeadline;

  @Column(name = "popup_body", length = 400)
  private String popupBody;

  @Column(name = "popup_visual_key", length = 500)
  private String popupVisualKey;

  @Builder
  private MidtermCampaign(Academy academy, String examName, int examYear, String semester,
      String examType, LocalDate periodStart, LocalDate periodEnd, MidtermCampaignState state,
      String heroEyebrow, String heroTitle, String heroBody, String heroCtaLabel,
      boolean published, boolean popupEnabled, String popupHeadline, String popupBody,
      String popupVisualKey) {
    this.academy = academy;
    this.examName = examName;
    this.examYear = examYear;
    this.semester = semester;
    this.examType = examType;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.state = state != null ? state : MidtermCampaignState.PREPARING;
    this.heroEyebrow = heroEyebrow;
    this.heroTitle = heroTitle;
    this.heroBody = heroBody;
    this.heroCtaLabel = heroCtaLabel;
    this.published = published;
    this.popupEnabled = popupEnabled;
    this.popupHeadline = popupHeadline;
    this.popupBody = popupBody;
    this.popupVisualKey = popupVisualKey;
  }

  public void updateBasics(String examName, int examYear, String semester, String examType,
      LocalDate periodStart, LocalDate periodEnd, MidtermCampaignState state) {
    this.examName = examName;
    this.examYear = examYear;
    this.semester = semester;
    this.examType = examType;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    if (state != null) {
      this.state = state;
    }
  }

  public void updateHero(String heroEyebrow, String heroTitle, String heroBody, String heroCtaLabel) {
    this.heroEyebrow = heroEyebrow;
    this.heroTitle = heroTitle;
    this.heroBody = heroBody;
    this.heroCtaLabel = heroCtaLabel;
  }

  public void updatePopup(boolean popupEnabled, String popupHeadline, String popupBody,
      String popupVisualKey) {
    this.popupEnabled = popupEnabled;
    this.popupHeadline = popupHeadline;
    this.popupBody = popupBody;
    this.popupVisualKey = popupVisualKey;
  }

  public void updatePublished(boolean published) {
    this.published = published;
  }
}
