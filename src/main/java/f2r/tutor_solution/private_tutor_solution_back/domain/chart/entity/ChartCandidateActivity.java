package f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chart_candidate_activities", uniqueConstraints = {
    @UniqueConstraint(name = "uk_chart_candidate_activity_url", columnNames = {"candidate_id", "source_url"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChartCandidateActivity extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "activity_id")
  private Long activityId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "candidate_id", nullable = false)
  private ChartCandidate candidate;

  @Column(name = "content_type", nullable = false, length = 80)
  private String contentType;

  @Lob
  @Column(name = "content_summary", nullable = false, columnDefinition = "TEXT")
  private String contentSummary;

  @Column(name = "source_url", nullable = false, length = 1000)
  private String sourceUrl;

  @Column(name = "discovered_at", nullable = false)
  private LocalDateTime discoveredAt;

  private ChartCandidateActivity(ChartCandidate candidate, String contentType, String contentSummary, String sourceUrl, LocalDateTime discoveredAt) {
    this.candidate = candidate;
    this.contentType = contentType;
    this.contentSummary = contentSummary;
    this.sourceUrl = sourceUrl;
    this.discoveredAt = discoveredAt;
  }

  public static ChartCandidateActivity of(ChartCandidate candidate, String contentType, String contentSummary, String sourceUrl, LocalDateTime discoveredAt) {
    return new ChartCandidateActivity(candidate, contentType, contentSummary, sourceUrl, discoveredAt);
  }
}
