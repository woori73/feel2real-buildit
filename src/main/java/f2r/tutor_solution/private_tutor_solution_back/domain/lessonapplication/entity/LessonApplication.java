package f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "lesson_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonApplication extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "application_id")
  private Long applicationId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lesson_id", nullable = false)
  private LessonProduct lesson;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id", nullable = false)
  private Student student;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "conversation_id")  // nullable
  private Conversation conversation;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private LessonApplicationStatus status;

  @Column(name = "applied_at", nullable = false)
  private LocalDateTime appliedAt;

  @Column(name = "canceled_at")
  private LocalDateTime canceledAt;

  @Builder
  public LessonApplication(LessonProduct lesson, Student student, Conversation conversation,
      LessonApplicationStatus status, LocalDateTime appliedAt, LocalDateTime canceledAt) {
    this.lesson = lesson;
    this.student = student;
    this.conversation = conversation;
    this.status = status != null ? status : LessonApplicationStatus.APPROVED;
    this.appliedAt = appliedAt != null ? appliedAt : LocalDateTime.now();
    this.canceledAt = canceledAt;
  }

  public void linkConversation(Conversation c) {
    this.conversation = c;
  }

  public boolean isApproved() {
    return status == LessonApplicationStatus.APPROVED;
  }

  public void reactivate(LocalDateTime appliedAt) {
    this.status = LessonApplicationStatus.APPROVED;
    this.appliedAt = appliedAt != null ? appliedAt : LocalDateTime.now();
    this.canceledAt = null;
  }

  public void cancel(LocalDateTime canceledAt) {
    if (status == LessonApplicationStatus.CANCELED) {
      throw new IllegalStateException("이미 취소된 신청입니다.");
    }
    this.status = LessonApplicationStatus.CANCELED;
    this.canceledAt = canceledAt != null ? canceledAt : LocalDateTime.now();
  }
}
