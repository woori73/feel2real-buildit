package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BuildIt AI 작업 로그 (P1).
 *
 * <p>AI의 모든 작업을 기록한다 (감사 + 비용 추적).
 * 적용된 작업만 approved=true이다.
 */
@Entity
@Table(
    name = "academy_ai_actions",
    indexes = {
      @Index(name = "idx_session", columnList = "sessionId"),
      @Index(name = "idx_academy_action", columnList = "academyId, createdAt")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyAiAction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long sessionId;

  @Column(nullable = false)
  private Long academyId;

  @Column(nullable = false, length = 50)
  private String actionType;

  @Column(columnDefinition = "MEDIUMTEXT")
  private String fieldChangesJson;

  @Column(nullable = false)
  private Integer tokensUsed = 0;

  @Column(nullable = false)
  private Boolean approved = false;

  private LocalDateTime appliedAt;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  public AcademyAiAction(
      Long sessionId, Long academyId, String actionType, String fieldChangesJson, int tokensUsed) {
    this.sessionId = sessionId;
    this.academyId = academyId;
    this.actionType = actionType;
    this.fieldChangesJson = fieldChangesJson;
    this.tokensUsed = tokensUsed;
    this.createdAt = LocalDateTime.now();
  }

  public void markApproved() {
    this.approved = true;
    this.appliedAt = LocalDateTime.now();
  }
}
