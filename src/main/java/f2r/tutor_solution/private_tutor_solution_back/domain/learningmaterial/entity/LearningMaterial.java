package f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "learning_materials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningMaterial extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "material_id")
  private Long materialId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id", nullable = false)
  private Student student;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "conversation_id", nullable = false)
  private Conversation conversation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "problem_source_id")  // nullable (V13): PDF 없이 부여 가능
  private Source problemSource;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_by")
  private User assignedBy;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private LearningMaterialStatus status = LearningMaterialStatus.ASSIGNED;

  @Column(name = "assigned_at", nullable = false)
  private LocalDateTime assignedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "reviewed", nullable = false)
  private boolean reviewed = false;

  @Column(name = "reviewed_at")
  private LocalDateTime reviewedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewed_by")
  private User reviewedBy;

  @Builder
  public LearningMaterial(Student student, Conversation conversation, Source problemSource,
      User assignedBy, String title, String description) {
    this.student = student;
    this.conversation = conversation;
    this.problemSource = problemSource;
    this.assignedBy = assignedBy;
    this.title = title;
    this.description = description;
    this.assignedAt = LocalDateTime.now();
  }

  public void markCompleted() {
    this.status = LearningMaterialStatus.COMPLETED;
    this.completedAt = LocalDateTime.now();
  }

  public void markReviewed(User user) {
    this.reviewed = true;
    this.reviewedAt = LocalDateTime.now();
    this.reviewedBy = user;
  }

  public void unmarkReviewed() {
    this.reviewed = false;
    this.reviewedAt = null;
    this.reviewedBy = null;
  }
}
