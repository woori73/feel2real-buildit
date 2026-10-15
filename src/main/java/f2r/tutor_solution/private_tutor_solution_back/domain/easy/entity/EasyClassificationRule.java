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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Easy 자동분류 규칙 — 거래처/적요 패턴 → 내부 계정 (우선순위·금액구간·방향 조건).
 * 발상 참고: kimlawtech/korean-jangbu-for (Apache-2.0), 코드 복사 없음.
 */
@Entity
@Table(name = "easy_classification_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyClassificationRule extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "rule_id")
  private Long ruleId;

  @Enumerated(EnumType.STRING)
  @Column(name = "pattern_type", nullable = false, length = 20)
  private EasyRulePatternType patternType;

  @Column(name = "pattern", nullable = false, length = 255)
  private String pattern;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false)
  private EasyAccount account;

  @Column(name = "priority", nullable = false)
  private int priority = 100;

  @Column(name = "amount_min")
  private Long amountMin;

  @Column(name = "amount_max")
  private Long amountMax;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", length = 10)
  private EasyTxnDirection direction;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Builder
  public EasyClassificationRule(EasyRulePatternType patternType, String pattern,
      EasyAccount account, int priority, Long amountMin, Long amountMax,
      EasyTxnDirection direction, boolean active) {
    this.patternType = patternType;
    this.pattern = pattern;
    this.account = account;
    this.priority = priority;
    this.amountMin = amountMin;
    this.amountMax = amountMax;
    this.direction = direction;
    this.active = active;
  }

  public void update(String pattern, int priority, Long amountMin, Long amountMax,
      EasyTxnDirection direction, boolean active) {
    this.pattern = pattern;
    this.priority = priority;
    this.amountMin = amountMin;
    this.amountMax = amountMax;
    this.direction = direction;
    this.active = active;
  }
}
