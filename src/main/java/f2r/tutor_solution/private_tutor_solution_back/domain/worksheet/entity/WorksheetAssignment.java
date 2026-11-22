package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 학습지 배정(V1134 easy_worksheet_assignments). 학생마다 한 줄, 빼면 removedAt만 채운다. */
@Entity
@Table(name = "easy_worksheet_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorksheetAssignment {

  /** 배정 상태: 시작 전 / 진행 중 / 완료. */
  public enum Status { NOT_STARTED, IN_PROGRESS, DONE }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assignment_id")
  private Long assignmentId;

  @Column(name = "worksheet_id", nullable = false)
  private Long worksheetId;

  @Column(name = "business_id", nullable = false)
  private Long businessId;

  @Column(name = "student_id", nullable = false)
  private Long studentId;

  @Column(name = "class_id")
  private Long classId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 12)
  private Status status;

  @Column(name = "assigned_by")
  private Long assignedBy;

  @Column(name = "assigned_at", nullable = false)
  private LocalDateTime assignedAt;

  @Column(name = "status_changed_at")
  private LocalDateTime statusChangedAt;

  @Column(name = "removed_at")
  private LocalDateTime removedAt;

  public WorksheetAssignment(Long worksheetId, Long businessId, Long studentId, Long classId, Long assignedBy,
      LocalDateTime now) {
    this.worksheetId = worksheetId;
    this.businessId = businessId;
    this.studentId = studentId;
    this.classId = classId;
    this.assignedBy = assignedBy;
    this.assignedAt = now;
    this.status = Status.NOT_STARTED;
  }

  public boolean isActive() {
    return removedAt == null;
  }

  public void changeStatus(Status next, LocalDateTime now) {
    if (this.status != next) {
      this.status = next;
      this.statusChangedAt = now;
    }
  }

  public void remove(LocalDateTime now) {
    if (removedAt == null) removedAt = now;
  }

  /** 뺐던 학생에게 다시 배정한다. 상태는 시작 전으로 돌아간다. */
  public void reassign(Long classId, Long by, LocalDateTime now) {
    this.removedAt = null;
    this.classId = classId;
    this.assignedBy = by;
    this.assignedAt = now;
    this.status = Status.NOT_STARTED;
    this.statusChangedAt = null;
  }
}
