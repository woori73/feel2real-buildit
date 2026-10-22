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
  @Column(length=40) private String operation;
  private Long academyId;
  private Long businessId;
  @Column(nullable=false) private LocalDateTime createdAt;
  public AssistantAudit(Long ownerId,AssistantDraft draft,String event) {
    this.ownerId=ownerId; this.draftId=draft.getId(); this.operation=draft.getOperation();
    this.academyId=draft.getAcademyId(); this.businessId=draft.getBusinessId(); this.event=event; this.createdAt=LocalDateTime.now();
  }
}
