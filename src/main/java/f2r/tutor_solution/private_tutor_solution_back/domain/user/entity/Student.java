package f2r.tutor_solution.private_tutor_solution_back.domain.user.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "students")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Student extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "student_id")
  private Long studentId;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(name = "school_name", nullable = false, length = 100)
  private String schoolName;

  @Column(name = "grade", nullable = false)
  private Byte grade;

  @Column(name = "parent_name", length = 30)
  private String parentName;

  @Column(name = "parent_phone", length = 20)
  private String parentPhone;

  @Builder
  public Student(User user, String schoolName, Byte grade,
      String parentName, String parentPhone) {
    this.user = user;
    this.schoolName = schoolName;
    this.grade = grade;
    this.parentName = parentName;
    this.parentPhone = parentPhone;
  }

  public void updateSchoolInfo(String schoolName, Byte grade) {
    this.schoolName = schoolName;
    this.grade = grade;
  }

  public void updateParentInfo(String parentName, String parentPhone) {
    this.parentName = parentName;
    this.parentPhone = parentPhone;
  }
}
