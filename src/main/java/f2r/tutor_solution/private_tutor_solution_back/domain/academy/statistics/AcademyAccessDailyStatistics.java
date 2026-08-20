package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "academy_access_daily_statistics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyAccessDailyStatistics {
  @EmbeddedId
  private Id id;

  @Column(name = "request_count", nullable = false)
  private long requestCount;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  private LocalDateTime updatedAt;

  @Embeddable
  public record Id(
      @Column(name = "academy_id") Long academyId,
      @Column(name = "access_date") LocalDate accessDate) implements Serializable {}
}
