package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Easy 계정과목 master — 내부 계정. Work 실데이터 도착 후 확정한다 (현재 잠정).
 */
@Entity
@Table(name = "easy_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyAccount extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "account_id")
  private Long accountId;

  @Column(name = "code", nullable = false, unique = true, length = 40)
  private String code;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 10)
  private EasyAccountDirection direction;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Builder
  public EasyAccount(String code, String name, EasyAccountDirection direction,
      int sortOrder, boolean active) {
    this.code = code;
    this.name = name;
    this.direction = direction;
    this.sortOrder = sortOrder;
    this.active = active;
  }

  public void update(String name, int sortOrder, boolean active) {
    this.name = name;
    this.sortOrder = sortOrder;
    this.active = active;
  }
}
