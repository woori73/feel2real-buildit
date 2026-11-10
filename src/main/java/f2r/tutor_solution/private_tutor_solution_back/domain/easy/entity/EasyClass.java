package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** F2R 출결부의 반. 지우지 않고 종료(ENDED)한다. */
@Entity
@Table(name = "easy_classes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyClass extends BaseEntity {

  public enum Status { ACTIVE, ENDED }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "class_id")
  private Long classId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Column(name = "name", nullable = false, length = 60)
  private String name;

  @Column(name = "teacher_name", length = 30)
  private String teacherName;

  @Column(name = "description", length = 300)
  private String description;

  @Column(name = "weekdays", length = 30)
  private String weekdays;

  @Column(name = "start_time")
  private LocalTime startTime;

  @Column(name = "end_time")
  private LocalTime endTime;

  @Column(name = "room", length = 30)
  private String room;

  @Column(name = "capacity")
  private Integer capacity;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private Status status = Status.ACTIVE;

  @Column(name = "ended_at")
  private LocalDateTime endedAt;

  @Builder
  public EasyClass(EasyBusiness business, String name, String teacherName, String description,
      String weekdays, LocalTime startTime, LocalTime endTime, String room, Integer capacity) {
    this.business = business;
    update(name, teacherName, description, weekdays, startTime, endTime, room, capacity);
  }

  public final void update(String name, String teacherName, String description, String weekdays,
      LocalTime startTime, LocalTime endTime, String room, Integer capacity) {
    this.name = name;
    this.teacherName = teacherName;
    this.description = description;
    this.weekdays = weekdays;
    this.startTime = startTime;
    this.endTime = endTime;
    this.room = room;
    this.capacity = capacity;
  }

  public void end(LocalDateTime when) {
    this.status = Status.ENDED;
    this.endedAt = when;
  }

  public boolean isActive() {
    return status == Status.ACTIVE;
  }
}
