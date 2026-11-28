package f2r.tutor_solution.private_tutor_solution_back.domain.parentnotice;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "codi_usage_counter", uniqueConstraints = @UniqueConstraint(
    name = "uq_codi_counter_period", columnNames = {"academy_id", "period_start", "task_type"}),
    check = @CheckConstraint(name = "ck_codi_counter_quantities",
        constraint = "confirmed_qty >= 0 AND reserved_qty >= 0"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodiUsageCounter {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "academy_id", nullable = false)
  private Long academyId;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "task_type", nullable = false, length = 32)
  private String taskType;

  @Column(name = "confirmed_qty", nullable = false)
  @ColumnDefault("0")
  private long confirmedQty;

  @Column(name = "reserved_qty", nullable = false)
  @ColumnDefault("0")
  private long reservedQty;

  @Column(name = "plan_version", length = 32)
  private String planVersion;
}
