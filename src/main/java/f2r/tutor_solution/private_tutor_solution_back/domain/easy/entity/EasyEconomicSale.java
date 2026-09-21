package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Provider-neutral identity for one economic sale. It never creates an EASY transaction and
 * carries no decision about whether approval, purchase, or deposit is the accounting basis.
 */
@Entity
@Table(name = "easy_collection_economic_sales",
    uniqueConstraints = @UniqueConstraint(name = "uq_collection_sale_economic",
        columnNames = {"connection_id", "economic_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyEconomicSale extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "sale_id")
  private Long saleId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "connection_id", nullable = false)
  private EasyCollectionConnection connection;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Column(name = "economic_key", nullable = false, length = 160)
  private String economicKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private EasyEconomicSaleStatus status;

  @Builder
  public EasyEconomicSale(EasyCollectionConnection connection, EasyBusiness business,
      String economicKey) {
    this.connection = connection;
    this.business = business;
    this.economicKey = economicKey;
    this.status = EasyEconomicSaleStatus.OBSERVED;
  }

  public void markReversed() {
    this.status = EasyEconomicSaleStatus.REVERSED;
  }

  public void markSuperseded() {
    this.status = EasyEconomicSaleStatus.SUPERSEDED;
  }
}
