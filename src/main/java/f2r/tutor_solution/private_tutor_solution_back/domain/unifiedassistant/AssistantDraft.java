package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name="unified_assistant_drafts") @Getter @NoArgsConstructor
public class AssistantDraft {
  @Id @Column(length=36) private String id;
  @Column(nullable=false) private Long ownerId;
  @Column(nullable=false,length=10) private String role;
  private Long academyId;
  private Long businessId;
  @Column(nullable=false,length=40) private String operation;
  @Column(nullable=false,columnDefinition="MEDIUMTEXT") private String payload;
  @Column(nullable=false,columnDefinition="MEDIUMTEXT") private String preview;
  @Column(nullable=false,length=64) private String planHash;
  @Column(nullable=false,length=64) private String fingerprint;
  @Column(nullable=false,length=24) private String status="PENDING";
  @Column(columnDefinition="MEDIUMTEXT") private String result;
  @Column(nullable=false) private LocalDateTime createdAt;
  @Column(nullable=false) private LocalDateTime expiresAt;
  public AssistantDraft(Long ownerId,String role,Long academyId,Long businessId,String operation,
      String payload,String preview,String fingerprint) {
    this.id=UUID.randomUUID().toString(); this.ownerId=ownerId; this.role=role;
    this.academyId=academyId; this.businessId=businessId; this.operation=operation;
    this.payload=payload; this.preview=preview; this.fingerprint=fingerprint;
    this.createdAt=LocalDateTime.now(); this.expiresAt=createdAt.plusMinutes(10);
    this.planHash=AssistantJson.hash(id+"\n"+ownerId+"\n"+role+"\n"+academyId+"\n"+businessId+"\n"+operation+"\n"+payload+"\n"+preview+"\n"+fingerprint);
  }
  public String calculatedHash() { return AssistantJson.hash(id+"\n"+ownerId+"\n"+role+"\n"+academyId+"\n"+businessId+"\n"+operation+"\n"+payload+"\n"+preview+"\n"+fingerprint); }
  public void finish(String status,String result) { this.status=status; this.result=result; }
}
