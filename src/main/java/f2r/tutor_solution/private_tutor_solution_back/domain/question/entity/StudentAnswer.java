package f2r.tutor_solution.private_tutor_solution_back.domain.question.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentAnswer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "answer_id")
  private Long answerId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id", nullable = false)
  private Student student;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @Column(name = "exam_id")
  private Long examId;

  @Column(name = "student_answer", length = 500)
  private String studentAnswer;

  @Column(name = "is_correct")
  private Boolean isCorrect;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Builder
  public StudentAnswer(Student student, Question question, Long examId, String studentAnswer) {
    this.student = student;
    this.question = question;
    this.examId = examId;
    this.studentAnswer = studentAnswer;
    this.createdAt = LocalDateTime.now();
  }

  public void grade(boolean isCorrect) {
    this.isCorrect = isCorrect;
  }
}
