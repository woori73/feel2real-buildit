package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** Many-to-many allocation between a bank payment and card payables. */
@Entity
@Table(name = "easy_card_settlement_allocations",
    uniqueConstraints = @UniqueConstraint(name = "uq_easy_card_settlement_allocation",
        columnNames = {"settlement_id", "payable_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyCardSettlementAllocation extends BaseEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "allocation_id") private Long allocationId;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "settlement_id", nullable = false) private EasyCardSettlement settlement;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payable_id", nullable = false) private EasyCardPayable payable;
  @Column(name = "allocated_amount", nullable = false) private long allocatedAmount;

  @Builder
  public EasyCardSettlementAllocation(EasyCardSettlement settlement, EasyCardPayable payable,
      long allocatedAmount) {
    if (settlement == null || payable == null || allocatedAmount <= 0)
      throw new IllegalArgumentException("Invalid settlement allocation.");
    this.settlement = settlement; this.payable = payable; this.allocatedAmount = allocatedAmount;
  }
}
