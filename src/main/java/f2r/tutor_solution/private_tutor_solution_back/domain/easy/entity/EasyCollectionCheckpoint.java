package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/** Last fully committed provider boundary, scoped by connection and dataset. */
@Entity
@Table(name = "easy_collection_checkpoints",
    uniqueConstraints = @UniqueConstraint(name = "uq_collection_checkpoint",
        columnNames = {"connection_id", "dataset_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyCollectionCheckpoint extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "checkpoint_id")
  private Long checkpointId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "connection_id", nullable = false)
  private EasyCollectionConnection connection;

  @Enumerated(EnumType.STRING)
  @Column(name = "dataset_type", nullable = false, length = 20)
  private EasyCollectionDatasetType datasetType;

  @Column(name = "cursor_value", length = 500)
  private String cursorValue;

  @Column(name = "checkpoint_value", length = 500)
  private String checkpointValue;

  @Column(name = "last_success_at")
  private LocalDateTime lastSuccessAt;

  @Builder
  public EasyCollectionCheckpoint(EasyCollectionConnection connection,
      EasyCollectionDatasetType datasetType) {
    this.connection = connection;
    this.datasetType = datasetType;
  }

  /** Called only by the successful persistence transaction. */
  public void advance(String cursorValue, String checkpointValue, LocalDateTime when) {
    this.cursorValue = cursorValue;
    this.checkpointValue = checkpointValue;
    this.lastSuccessAt = when;
  }
}
