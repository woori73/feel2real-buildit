package f2r.tutor_solution.private_tutor_solution_back.domain.user.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "tutor_student_map")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class TutorStudentMap extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "mapping_id")
  private Long mappingId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tutor_user_id", nullable = false)
  private User tutor;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id", nullable = false)
  private Student student;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Builder
  public TutorStudentMap(User tutor, Student student, boolean active) {
    this.tutor = tutor;
    this.student = student;
    this.active = active;
  }

  public void activate() {
    this.active = true;
  }

  public void deactivate() {
    this.active = false;
  }
}
