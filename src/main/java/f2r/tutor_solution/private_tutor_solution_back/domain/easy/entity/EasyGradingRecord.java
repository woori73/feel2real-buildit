package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 채점 기록: 이미 채점된 답지(사진)를 보고 남긴 결과 — 맞은 수·전체 수·틀린 문항(오답 기록, JSON)·피드백. 관리자 전용.
 * 수정·삭제 가능(삭제하면 사진 파일도 지운다).
 */
@Entity
@Table(name = "easy_grading_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyGradingRecord extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "grading_id")
  private Long gradingId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id", nullable = false)
  private EasyStudent student;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lesson_record_id")
  private EasyLessonRecord lessonRecord;

  @Column(name = "graded_on", nullable = false)
  private LocalDate gradedOn;

  @Column(name = "subject_area", length = 100)
  private String subjectArea;

  @Column(name = "material", length = 200)
  private String material;

  @Column(name = "total_count")
  private Integer totalCount;

  @Column(name = "correct_count")
  private Integer correctCount;

  /** JSON 배열 [{"no":"3","topic":"관계대명사","reason":"what/that 혼동"}]. */
  @Column(name = "wrong_items", columnDefinition = "TEXT")
  private String wrongItems;

  @Column(name = "feedback", length = 2000)
  private String feedback;

  @Column(name = "source", nullable = false, length = 10)
  private String source;

  @Builder
  public EasyGradingRecord(EasyBusiness business, EasyStudent student, EasyLessonRecord lessonRecord,
      LocalDate gradedOn, String subjectArea, String material, Integer totalCount, Integer correctCount,
      String wrongItems, String feedback, String source) {
    this.business = business;
    this.student = student;
    this.lessonRecord = lessonRecord;
    this.gradedOn = gradedOn;
    this.subjectArea = subjectArea;
    this.material = material;
    this.totalCount = totalCount;
    this.correctCount = correctCount;
    this.wrongItems = wrongItems;
    this.feedback = feedback;
    this.source = source == null ? "WEB" : source;
  }

  public void update(EasyLessonRecord lessonRecord, LocalDate gradedOn, String subjectArea, String material,
      Integer totalCount, Integer correctCount, String wrongItems, String feedback) {
    this.lessonRecord = lessonRecord;
    this.gradedOn = gradedOn;
    this.subjectArea = subjectArea;
    this.material = material;
    this.totalCount = totalCount;
    this.correctCount = correctCount;
    this.wrongItems = wrongItems;
    this.feedback = feedback;
  }

  /** 연결된 수업 기록을 지울 때 연결만 끊는다(채점 기록은 남김). */
  public void detachLesson() {
    this.lessonRecord = null;
  }
}
