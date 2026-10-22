package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name="unified_assistant_progress") @Getter @NoArgsConstructor
public class AssistantProgress {
  @Id private Long userId;
  @Column(nullable=false, length=10) private String role;
  @Column(nullable=false) private int step;
  @Column(nullable=false) private boolean completed;
  @Version private long version;
  public AssistantProgress(Long userId, String role) { this.userId=userId; this.role=role; }
  public void update(String role, int step, boolean completed) {
    this.role=role; this.step=step; this.completed=completed;
  }
}
