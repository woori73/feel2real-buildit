package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 학교 컬렉션의 준비 단계 1건(작업지시서 §17). 운영자가 입력한 상태만 노출한다. */
@Entity
@Table(name = "midterm_prep_status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MidtermPrepStatus extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "prep_status_id")
  private Long prepStatusId;

  @Column(name = "school_collection_id", nullable = false)
  private Long schoolCollectionId;

  @Column(name = "step_key", nullable = false, length = 20)
  private String stepKey;

  @Column(nullable = false, length = 80)
  private String label;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MidtermPrepState state;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  private MidtermPrepStatus(Long schoolCollectionId, String stepKey, String label,
      MidtermPrepState state, int sortOrder) {
    this.schoolCollectionId = schoolCollectionId;
    this.stepKey = stepKey;
    this.label = label;
    this.state = state != null ? state : MidtermPrepState.NOT_STARTED;
    this.sortOrder = sortOrder;
  }
}
