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

  /** 제작 공장 게시(FactoryExamPublisher)가 쓰는 새 시험 행. 그 밖의 시험은 마이그레이션으로 넣는다. */
  public static TpExam create(String examCode, String kind, String title, String subtitle, String description,
      int timeLimitSec, String status) {
    TpExam e = new TpExam();
    e.examCode = examCode;
    e.kind = kind;
    e.title = title;
    e.subtitle = subtitle;
    e.description = description;
    e.timeLimitSec = timeLimitSec;
    e.status = status;
    return e;
  }
}
