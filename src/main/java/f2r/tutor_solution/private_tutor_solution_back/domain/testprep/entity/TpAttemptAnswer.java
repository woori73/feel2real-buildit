package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tp_attempt_answers")
@Getter
@Setter
@NoArgsConstructor
public class TpAttemptAnswer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "answer_id")
  private Long answerId;

  @Column(name = "attempt_id", nullable = false)
  private Long attemptId;

  @Column(name = "question_id", nullable = false)
  private Long questionId;

  @Column(name = "answer_text", columnDefinition = "MEDIUMTEXT")
  private String answerText;

  @Column(name = "is_correct")
  private Boolean isCorrect;

  @Column(name = "answered_at", nullable = false)
  private LocalDateTime answeredAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
