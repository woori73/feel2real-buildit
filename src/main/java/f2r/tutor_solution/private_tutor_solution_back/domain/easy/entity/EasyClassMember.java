package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 반 배정. 해제해도 지우지 않고 released_on·사유를 남긴다. */
@Entity
@Table(name = "easy_class_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyClassMember extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "member_id")
  private Long memberId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "class_id", nullable = false)
  private EasyClass easyClass;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id", nullable = false)
  private EasyStudent student;

  @Column(name = "assigned_on", nullable = false)
  private LocalDate assignedOn;

  @Column(name = "released_on")
  private LocalDate releasedOn;

  @Column(name = "release_reason", length = 20)
  private String releaseReason;

  public EasyClassMember(EasyClass easyClass, EasyStudent student, LocalDate assignedOn) {
    this.easyClass = easyClass;
    this.student = student;
    this.assignedOn = assignedOn;
  }

  public void release(LocalDate on, String reason) {
    this.releasedOn = on;
    this.releaseReason = reason;
  }

  public boolean isActive() {
    return releasedOn == null;
  }

  /** 날짜 기준 그 반에 배정돼 있었는지(해제일 당일은 포함하지 않는다). */
  public boolean activeOn(LocalDate day) {
    return !assignedOn.isAfter(day) && (releasedOn == null || releasedOn.isAfter(day));
  }
}
