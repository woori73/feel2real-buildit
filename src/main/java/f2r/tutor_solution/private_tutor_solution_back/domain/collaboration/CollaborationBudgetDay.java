package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "ai_collaboration_budget_days")
public class CollaborationBudgetDay {
  @Id
  @Column(name = "budget_day")
  private java.time.LocalDate budgetDay;

  @Column(name = "reserved_microusd", nullable = false)
  private long reservedMicrousd;
}
