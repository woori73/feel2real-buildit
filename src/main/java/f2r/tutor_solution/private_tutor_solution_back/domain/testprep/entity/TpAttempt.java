package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tp_attempts")
@Getter
@Setter
@NoArgsConstructor
public class TpAttempt extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "attempt_id")
  private Long attemptId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "exam_id", nullable = false)
  private Long examId;

  @Column(name = "status", nullable = false, length = 12)
  private String status;

  @Column(name = "question_ids_json", columnDefinition = "TEXT")
  private String questionIdsJson;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "deadline_at", nullable = false)
  private LocalDateTime deadlineAt;

  @Column(name = "submitted_at")
  private LocalDateTime submittedAt;

  @Column(name = "overtime", nullable = false)
  private Boolean overtime;

  @Column(name = "auto_accuracy", precision = 5, scale = 2)
  private BigDecimal autoAccuracy;

  @Column(name = "overall_band", precision = 2, scale = 1)
  private BigDecimal overallBand;

  @Column(name = "section_scores_json", columnDefinition = "TEXT")
  private String sectionScoresJson;
}
