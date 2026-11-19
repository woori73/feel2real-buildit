package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 학생 한 명의 하루 수업 기록(수업 범위·숙제 확인·낸 숙제·메모). 관리자 전용. 수정·삭제 가능. */
@Entity
@Table(name = "easy_lesson_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyLessonRecord extends BaseEntity {

  public enum HomeworkCheck {
    DONE("다 해 옴"), PARTIAL("일부 해 옴"), NOT_DONE("안 해 옴");

    private final String label;

    HomeworkCheck(String label) { this.label = label; }

    public String label() { return label; }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "lesson_record_id")
  private Long lessonRecordId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id", nullable = false)
  private EasyStudent student;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "class_id")
  private EasyClass easyClass;

  @Column(name = "lesson_date", nullable = false)
  private LocalDate lessonDate;

  @Column(name = "lesson_range", length = 300)
  private String lessonRange;

  @Enumerated(EnumType.STRING)
  @Column(name = "homework_check", length = 20)
  private HomeworkCheck homeworkCheck;

  @Column(name = "homework_done_note", length = 500)
  private String homeworkDoneNote;

  @Column(name = "next_homework", length = 500)
  private String nextHomework;

  @Column(name = "teacher_note", length = 1000)
  private String teacherNote;

  @Column(name = "source", nullable = false, length = 10)
  private String source;

  @Builder
  public EasyLessonRecord(EasyBusiness business, EasyStudent student, EasyClass easyClass, LocalDate lessonDate,
      String lessonRange, HomeworkCheck homeworkCheck, String homeworkDoneNote, String nextHomework,
      String teacherNote, String source) {
    this.business = business;
    this.student = student;
    this.easyClass = easyClass;
    this.lessonDate = lessonDate;
    this.lessonRange = lessonRange;
    this.homeworkCheck = homeworkCheck;
    this.homeworkDoneNote = homeworkDoneNote;
    this.nextHomework = nextHomework;
    this.teacherNote = teacherNote;
    this.source = source == null ? "WEB" : source;
  }

  public void update(EasyClass easyClass, LocalDate lessonDate, String lessonRange, HomeworkCheck homeworkCheck,
      String homeworkDoneNote, String nextHomework, String teacherNote) {
    this.easyClass = easyClass;
    this.lessonDate = lessonDate;
    this.lessonRange = lessonRange;
    this.homeworkCheck = homeworkCheck;
    this.homeworkDoneNote = homeworkDoneNote;
    this.nextHomework = nextHomework;
    this.teacherNote = teacherNote;
  }
}
