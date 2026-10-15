package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tp_questions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TpQuestion extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "question_id")
  private Long questionId;

  @Column(name = "exam_id", nullable = false)
  private Long examId;

  @Column(name = "section", nullable = false, length = 10)
  private String section;

  @Column(name = "task_type", nullable = false, length = 40)
  private String taskType;

  @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
  private String prompt;

  @Column(name = "body_text", columnDefinition = "MEDIUMTEXT")
  private String bodyText;

  @Column(name = "audio_mode", nullable = false, length = 10)
  private String audioMode;

  @Column(name = "choices_json", columnDefinition = "TEXT")
  private String choicesJson;

  @Column(name = "answer_key", length = 500)
  private String answerKey;

  @Column(name = "auto_score", nullable = false)
  private Boolean autoScore;

  @Column(name = "explanation", columnDefinition = "TEXT")
  private String explanation;

  @Column(name = "order_no", nullable = false)
  private Integer orderNo;
}
