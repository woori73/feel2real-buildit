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
 * Easy 수정 감사 로그 — append-only. 수정·삭제하지 않는다.
 * 향후 AI 수정 (요청→확인→수정→재분개) 추적 기반이다. 가짜 AI API는 없다.
 */
@Entity
@Table(name = "easy_correction_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyCorrectionLog extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "log_id")
  private Long logId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "txn_id")
  private EasyTransaction txn;

  @Column(name = "action", nullable = false, length = 30)
  private String action;

  @Column(name = "before_json", nullable = false, columnDefinition = "TEXT")
  private String beforeJson;

  @Column(name = "after_json", nullable = false, columnDefinition = "TEXT")
  private String afterJson;

  @Column(name = "requested_by", length = 50)
  private String requestedBy;

  @Column(name = "confirmed_by", length = 50)
  private String confirmedBy;

  @Builder
  public EasyCorrectionLog(EasyBusiness business, EasyTransaction txn, String action,
      String beforeJson, String afterJson, String requestedBy, String confirmedBy) {
    if (business == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    if (action == null || action.isBlank()) {
      throw new IllegalArgumentException("수정 동작을 입력해 주세요.");
    }
    this.business = business;
    this.txn = txn;
    this.action = action.trim();
    this.beforeJson = beforeJson == null ? "{}" : beforeJson;
    this.afterJson = afterJson == null ? "{}" : afterJson;
    this.requestedBy = requestedBy;
    this.confirmedBy = confirmedBy;
  }
}
