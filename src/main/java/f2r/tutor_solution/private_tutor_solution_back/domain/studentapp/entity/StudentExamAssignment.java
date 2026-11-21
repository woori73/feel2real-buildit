package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 원장이 학생에게 배정한 디지털 모의고사(V1126 student_exam_assignments). 빼면 removedAt만 채운다. */
@Entity
@Table(name = "student_exam_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentExamAssignment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assignment_id")
  private Long assignmentId;

  @Column(name = "business_id", nullable = false)
  private Long businessId;

  @Column(name = "student_id", nullable = false)
  private Long studentId;

  @Column(name = "exam_code", nullable = false, length = 64)
  private String examCode;

  @Column(name = "assigned_by")
  private Long assignedBy;

  @Column(name = "assigned_at", nullable = false)
  private LocalDateTime assignedAt;

  @Column(name = "removed_at")
  private LocalDateTime removedAt;

  public StudentExamAssignment(Long businessId, Long studentId, String examCode, Long assignedBy, LocalDateTime now) {
    this.businessId = businessId;
    this.studentId = studentId;
    this.examCode = examCode;
    this.assignedBy = assignedBy;
    this.assignedAt = now;
  }

  public boolean isActive() {
    return removedAt == null;
  }

  public void remove(LocalDateTime now) {
    if (removedAt == null) removedAt = now;
  }

  public void restore(Long by, LocalDateTime now) {
    this.removedAt = null;
    this.assignedBy = by;
    this.assignedAt = now;
  }
}
