package f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "exams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exam extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "exam_id")
  private Long examId;

  @Column(name = "school_name", nullable = false, length = 100)
  private String schoolName;

  @Column(name = "grade", nullable = false)
  private Byte grade;

  @Enumerated(EnumType.STRING)
  @Column(name = "exam_type", nullable = false, length = 10)
  private ExamType examType;

  @Column(name = "exam_year")
  private Short examYear;

  @Column(name = "exam_semester")
  private Byte examSemester;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subject_id", nullable = false)
  private Subject subject;

  @Column(name = "display_name", length = 200)
  private String displayName;

  @Column(name = "exam_date")
  private LocalDate examDate;

  @Builder
  public Exam(String schoolName, Byte grade, ExamType examType, Short examYear,
      Byte examSemester, Subject subject, String displayName, LocalDate examDate) {
    this.schoolName   = schoolName;
    this.grade        = grade;
    this.examType     = examType;
    this.examYear     = examYear;
    this.examSemester = examSemester;
    this.subject      = subject;
    this.displayName  = displayName;
    this.examDate     = examDate;
  }

  /**
   * 학생 검증 페이지에서 메타를 보정하는 경우의 update (MVP §5.0 6단계).
   *
   * <p>exam_id는 그대로 유지하며 dedup 재시도/마이그레이션은 하지 않는다 (중복은 추후 배치로 정리).
   * NOT NULL 컬럼(schoolName/grade/examType)은 null이 들어오면 기존 값을 유지한다.
   * nullable 컬럼(examYear/examSemester)은 null도 그대로 반영한다 (학생이 "모름"을 표현).
   * displayName도 호출자가 재계산해서 넘긴다.
   */
  public void updateMeta(String schoolName, Byte grade, ExamType examType,
      Short examYear, Byte examSemester, String displayName) {
    if (schoolName != null) this.schoolName = schoolName;
    if (grade != null) this.grade = grade;
    if (examType != null) this.examType = examType;
    this.examYear = examYear;
    this.examSemester = examSemester;
    if (displayName != null) this.displayName = displayName;
  }
}
