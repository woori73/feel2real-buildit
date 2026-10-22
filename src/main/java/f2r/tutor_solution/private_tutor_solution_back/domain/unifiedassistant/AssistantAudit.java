package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
@Entity @Table(name="unified_assistant_audit") @Getter @NoArgsConstructor
public class AssistantAudit {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(nullable=false) private Long ownerId;
  @Column(length=36) private String draftId;
  @Column(nullable=false,length=40) private String event;
  @Column(nullable=false) private LocalDateTime createdAt;
  public AssistantAudit(Long ownerId,String draftId,String event) {
    this.ownerId=ownerId; this.draftId=draftId; this.event=event; this.createdAt=LocalDateTime.now();
  }
}
