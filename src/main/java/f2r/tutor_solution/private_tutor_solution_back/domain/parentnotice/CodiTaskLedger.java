package f2r.tutor_solution.private_tutor_solution_back.domain.parentnotice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "codi_task_ledger", uniqueConstraints = {
    @UniqueConstraint(name = "uq_codi_ledger_request", columnNames = "request_key"),
    @UniqueConstraint(name = "uq_codi_ledger_result", columnNames = "result_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodiTaskLedger {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "task_id", nullable = false, length = 36)
  private UUID taskId;

  @Column(name = "academy_id", nullable = false)
  private Long academyId;

  @Column(name = "task_type", nullable = false, length = 32)
  private String taskType;

  @Column(nullable = false, length = 16)
  private String channel;

  @Column(name = "request_key", nullable = false, length = 100)
  private String requestKey;

  @Column(name = "result_kind", nullable = false, length = 32)
  private String resultKind;

  @Column(name = "result_id", nullable = false, length = 100)
  private String resultId;

  @Column(nullable = false)
  @ColumnDefault("1")
  private int qty = 1;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME")
  private LocalDateTime createdAt;
}
