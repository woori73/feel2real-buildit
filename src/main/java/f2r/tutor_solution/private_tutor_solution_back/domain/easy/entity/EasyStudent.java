package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Easy 학생 원장 — 회계 내부 독립 운영. Wiki users와 연결하지 않는다.
 */
@Entity
@Table(name = "easy_students")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyStudent extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "student_id")
  private Long studentId;

  @Column(name = "name", nullable = false, length = 30)
  private String name;

  @Column(name = "school", nullable = false, length = 100)
  private String school = "";

  @Column(name = "grade", nullable = false)
  private int grade;

  @Column(name = "phone", length = 20)
  private String phone;

  @Column(name = "parent_phone", length = 20)
  private String parentPhone;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private EasyStudentStatus status = EasyStudentStatus.ENROLLED;

  @Column(name = "enrolled_on")
  private LocalDate enrolledOn;

  @Column(name = "memo", length = 500)
  private String memo;

  @Builder
  public EasyStudent(String name, String school, int grade, String phone,
      String parentPhone, EasyStudentStatus status, LocalDate enrolledOn, String memo) {
    this.name = name;
    this.school = school == null ? "" : school;
    this.grade = grade;
    this.phone = phone;
    this.parentPhone = parentPhone;
    this.status = status == null ? EasyStudentStatus.ENROLLED : status;
    this.enrolledOn = enrolledOn;
    this.memo = memo;
  }

  public void update(String name, String school, int grade, String phone,
      String parentPhone, EasyStudentStatus status, LocalDate enrolledOn, String memo) {
    this.name = name;
    this.school = school == null ? "" : school;
    this.grade = grade;
    this.phone = phone;
    this.parentPhone = parentPhone;
    if (status != null) {
      this.status = status;
    }
    this.enrolledOn = enrolledOn;
    this.memo = memo;
  }
}
