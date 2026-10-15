package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Business-scoped merchant → account mapping for classification recommendations.
 * Priority 1: ADMIN_MAPPING (explicit admin save)
 * Priority 2: HISTORICAL_EXACT (exact historical match)
 * Priority 3: HISTORICAL_NORMALIZED (normalized merchant match)
 * Priority 4: RULE (rule-based category match)
 * Source determines recommendation confidence and transparency.
 */
@Entity
@Table(name = "easy_merchant_account_mappings",
    uniqueConstraints = @UniqueConstraint(name = "uq_easy_merchant_mapping_business_merchant",
        columnNames = {"business_id", "normalized_merchant"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyMerchantAccountMapping extends BaseEntity {

  public enum Source {
    ADMIN_MAPPING,          // Priority 1: Explicit admin save
    HISTORICAL_EXACT,       // Priority 2: Exact historical confirmed match
    HISTORICAL_NORMALIZED,  // Priority 3: Normalized merchant match
    RULE                    // Priority 4: Rule-based category match
  }

  public enum Confidence {
    HIGH, MEDIUM, LOW
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "mapping_id")
  private Long mappingId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Column(name = "normalized_merchant", nullable = false, length = 160)
  private String normalizedMerchant;

  @Column(name = "raw_merchant", nullable = false, length = 160)
  private String rawMerchant;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private EasyAccount account;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 30)
  private Source source;

  @Enumerated(EnumType.STRING)
  @Column(name = "confidence", nullable = false, length = 10)
  private Confidence confidence = Confidence.MEDIUM;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "created_by", length = 50)
  private String createdBy;

  @Column(name = "updated_by", length = 50)
  private String updatedBy;

  @Builder
  public EasyMerchantAccountMapping(EasyBusiness business, String normalizedMerchant,
      String rawMerchant, EasyAccount account, Source source, Confidence confidence,
      String createdBy) {
    this.business = business;
    this.normalizedMerchant = normalizedMerchant;
    this.rawMerchant = rawMerchant;
    this.account = account;
    this.source = source;
    this.confidence = confidence != null ? confidence : Confidence.MEDIUM;
    this.createdBy = createdBy;
  }

  public void deactivate(String updatedBy) {
    this.active = false;
    this.updatedBy = updatedBy;
  }

  public void reactivate(String updatedBy) {
    this.active = true;
    this.updatedBy = updatedBy;
  }

  public void updateAccount(EasyAccount account, Source source, Confidence confidence, String updatedBy) {
    this.account = account;
    this.source = source;
    this.confidence = confidence;
    this.updatedBy = updatedBy;
  }
}