package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 기존 자료에서 확인한 요일과 별도로 보존하는 원장의 요일 수정값. */
@Entity
@Table(name = "easy_student_weekday_schedules", uniqueConstraints = @UniqueConstraint(name = "uk_easy_student_weekdays", columnNames = {"business_id", "student_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyStudentWeekdaySchedule extends BaseEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "schedule_id") private Long scheduleId;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id", nullable = false)
  private EasyStudent student;
  @Column(name = "weekdays", length = 40) private String weekdays;
  @Version @Column(name = "version", nullable = false) private Long version;

  public EasyStudentWeekdaySchedule(EasyBusiness business, EasyStudent student) {
    this.business = business;
    this.student = student;
  }

  public void setWeekdays(String weekdays) { this.weekdays = weekdays; }
}
