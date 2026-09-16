package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * Easy 계정 매핑 — 내부 계정 ↔ 외부(이지샵) ↔ 국세청 표준.
 * 표준 코드/명은 Work 실데이터 후 확정한다.
 */
@Entity
@Table(name = "easy_account_mappings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyAccountMapping extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "mapping_id")
  private Long mappingId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false)
  private EasyAccount account;

  @Column(name = "external_code", nullable = false, length = 100)
  private String externalCode;

  @Column(name = "external_name", nullable = false, length = 200)
  private String externalName = "";

  @Column(name = "standard_code", length = 20)
  private String standardCode;

  @Column(name = "standard_name", nullable = false, length = 100)
  private String standardName = "";

  @Column(name = "note", length = 500)
  private String note;

  @Builder
  public EasyAccountMapping(EasyAccount account, String externalCode, String externalName,
      String standardCode, String standardName, String note) {
    this.account = account;
    this.externalCode = externalCode;
    this.externalName = externalName == null ? "" : externalName;
    this.standardCode = standardCode;
    this.standardName = standardName == null ? "" : standardName;
    this.note = note;
  }
}
