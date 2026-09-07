package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AFTER THE EXAM 기록(학교 컬렉션당 1건, 작업지시서 §29·§30). Phase 1 은 운영자 입력만.
 * 확인되지 않은 점수·적중률은 저장하지 않는다 — 근거 있는 서술만.
 */
@Entity
@Table(name = "midterm_after_exam")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MidtermAfterExam extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "after_exam_id")
  private Long afterExamId;

  @Column(name = "school_collection_id", nullable = false, unique = true)
  private Long schoolCollectionId;

  @Column(name = "exam_feature", columnDefinition = "TEXT")
  private String examFeature;

  @Column(name = "prepared_note", columnDefinition = "TEXT")
  private String preparedNote;

  @Column(name = "connection_note", columnDefinition = "TEXT")
  private String connectionNote;

  @Column(name = "next_improvement", columnDefinition = "TEXT")
  private String nextImprovement;

  @Column(nullable = false)
  private boolean published;

  @Builder
  private MidtermAfterExam(Long schoolCollectionId, String examFeature, String preparedNote,
      String connectionNote, String nextImprovement, boolean published) {
    this.schoolCollectionId = schoolCollectionId;
    this.examFeature = examFeature;
    this.preparedNote = preparedNote;
    this.connectionNote = connectionNote;
    this.nextImprovement = nextImprovement;
    this.published = published;
  }

  public void update(String examFeature, String preparedNote, String connectionNote,
      String nextImprovement, boolean published) {
    this.examFeature = examFeature;
    this.preparedNote = preparedNote;
    this.connectionNote = connectionNote;
    this.nextImprovement = nextImprovement;
    this.published = published;
  }
}
