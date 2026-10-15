package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "academy_handle_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyHandleHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "history_id")
  private Long historyId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "academy_id", nullable = false)
  private Academy academy;

  @Column(name = "handle", nullable = false, length = 30)
  private String handle;

  @Column(name = "valid_from_utc", nullable = false)
  private Instant validFromUtc;

  @Column(name = "valid_to_utc")
  private Instant validToUtc;

  public AcademyHandleHistory(Academy academy, String handle, Instant validFromUtc) {
    this.academy = academy;
    this.handle = handle;
    this.validFromUtc = validFromUtc;
  }

  public void closeAt(Instant instant) {
    this.validToUtc = instant;
  }
}
