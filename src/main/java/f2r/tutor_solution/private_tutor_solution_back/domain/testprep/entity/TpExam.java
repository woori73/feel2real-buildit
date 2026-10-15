package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tp_exams")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TpExam extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "exam_id")
  private Long examId;

  @Column(name = "exam_code", nullable = false, length = 40, unique = true)
  private String examCode;

  @Column(name = "kind", nullable = false, length = 20)
  private String kind;

  @Column(name = "title", nullable = false, length = 150)
  private String title;

  @Column(name = "subtitle", length = 255)
  private String subtitle;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "difficulty", length = 10)
  private String difficulty;

  @Column(name = "time_limit_sec", nullable = false)
  private Integer timeLimitSec;

  @Column(name = "status", nullable = false, length = 12)
  private String status;
}
