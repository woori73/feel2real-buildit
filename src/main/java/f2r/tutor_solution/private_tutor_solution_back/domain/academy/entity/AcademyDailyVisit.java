package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "academy_daily_visits",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_academy_daily_visits_academy_date",
        columnNames = {"academy_id", "visit_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyDailyVisit extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "daily_visit_id")
  private Long dailyVisitId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "academy_id", nullable = false)
  private Academy academy;

  @Column(name = "visit_date", nullable = false)
  private LocalDate visitDate;

  @Column(name = "visit_count", nullable = false)
  private long visitCount;

  private AcademyDailyVisit(Academy academy, LocalDate visitDate, long visitCount) {
    this.academy = academy;
    this.visitDate = visitDate;
    this.visitCount = visitCount;
  }

  public static AcademyDailyVisit of(Academy academy, LocalDate visitDate, long visitCount) {
    return new AcademyDailyVisit(academy, visitDate, visitCount);
  }
}

