package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "academy_access_statistics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyAccessStatistics {
  @Id
  @Column(name = "academy_id")
  private Long academyId;

  @Column(name = "total_request_count", nullable = false)
  private long totalRequestCount;

  @Column(name = "last_log_at")
  private Instant lastLogAt;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  private LocalDateTime updatedAt;
}
