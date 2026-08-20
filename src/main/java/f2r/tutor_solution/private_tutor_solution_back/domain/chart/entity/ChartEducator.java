package f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chart_educators")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChartEducator extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "educator_id")
  private Long educatorId;

  @Column(name = "person_key", nullable = false, unique = true, length = 180)
  private String personKey;

  @Column(name = "name", nullable = false, length = 80)
  private String name;

  @Column(name = "role", nullable = false, length = 40)
  private String role;

  @Column(name = "academy_name", length = 160)
  private String academyName;

  @Column(name = "activity_region", nullable = false, length = 120)
  private String activityRegion;

  @Column(name = "zone_id", nullable = false, length = 60)
  private String zoneId;

  @Column(name = "approved_from_candidate_id", nullable = false)
  private Long approvedFromCandidateId;

  @Column(name = "approved_at", nullable = false)
  private LocalDateTime approvedAt;

  private ChartEducator(String personKey, String name, String role, String academyName, String activityRegion, String zoneId, Long approvedFromCandidateId, LocalDateTime approvedAt) {
    this.personKey = personKey;
    this.name = name;
    this.role = role;
    this.academyName = academyName;
    this.activityRegion = activityRegion;
    this.zoneId = zoneId;
    this.approvedFromCandidateId = approvedFromCandidateId;
    this.approvedAt = approvedAt;
  }

  public static ChartEducator from(ChartCandidate candidate, LocalDateTime approvedAt) {
    return new ChartEducator(candidate.getPersonKey(), candidate.getName(), candidate.getRole(), candidate.getAcademyName(),
        candidate.getActivityRegion(), candidate.getZoneId(), candidate.getCandidateId(), approvedAt);
  }

  public void refreshFrom(ChartCandidate candidate) {
    this.name = candidate.getName();
    this.role = candidate.getRole();
    this.academyName = candidate.getAcademyName();
    this.activityRegion = candidate.getActivityRegion();
    this.zoneId = candidate.getZoneId();
    this.approvedFromCandidateId = candidate.getCandidateId();
  }
}
