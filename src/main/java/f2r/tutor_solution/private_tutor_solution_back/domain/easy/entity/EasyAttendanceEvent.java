package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 출결 기록. 지우지 않는다. 정정은 새 행(correctsEventId·사유)을 만들고 이전 행은 superseded로 남긴다. */
@Entity
@Table(name = "easy_attendance_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyAttendanceEvent extends BaseEntity {

  public enum Type {
    CHECK_IN("등원"), CHECK_OUT("하원"), ABSENT("결석"), LATE("지각"), EARLY_LEAVE("조퇴");

    private final String label;

    Type(String label) { this.label = label; }

    public String label() { return label; }
  }

  public enum Source { MANUAL, ROSTER_IMPORT }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "event_id")
  private Long eventId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "class_id")
  private EasyClass easyClass;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id", nullable = false)
  private EasyStudent student;

  @Column(name = "occurred_on", nullable = false)
  private LocalDate occurredOn;

  @Column(name = "occurred_at", nullable = false)
  private LocalDateTime occurredAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 12)
  private Type eventType;

  @Column(name = "note", length = 200)
  private String note;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 15)
  private Source source = Source.MANUAL;

  @Column(name = "source_key", length = 120, unique = true)
  private String sourceKey;

  @Column(name = "corrects_event_id")
  private Long correctsEventId;

  @Column(name = "correction_reason", length = 200)
  private String correctionReason;

  @Column(name = "superseded", nullable = false)
  private boolean superseded;

  @Builder
  public EasyAttendanceEvent(EasyBusiness business, EasyClass easyClass, EasyStudent student,
      LocalDateTime occurredAt, Type eventType, String note, Source source, String sourceKey,
      Long correctsEventId, String correctionReason) {
    this.business = business;
    this.easyClass = easyClass;
    this.student = student;
    this.occurredAt = occurredAt;
    this.occurredOn = occurredAt.toLocalDate();
    this.eventType = eventType;
    this.note = note;
    this.source = source == null ? Source.MANUAL : source;
    this.sourceKey = sourceKey;
    this.correctsEventId = correctsEventId;
    this.correctionReason = correctionReason;
  }

  /** 정정되어 현재 상태에서 빠진다(행은 그대로 남는다). */
  public void supersede() {
    this.superseded = true;
  }
}
