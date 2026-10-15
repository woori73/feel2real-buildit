package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사업장(business) ↔ 수집원(source) 연결.
 * connection_ref에는 provider connectedId/동의ID 등 '참조자'만 저장한다 —
 * 고객 ID/PW·공동인증서·OTP는 구조적으로 저장 대상이 아니다(대법 2020도9972).
 */
@Entity
@Table(name = "easy_collection_connections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyCollectionConnection extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "connection_id")
  private Long connectionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id", nullable = false)
  private EasyCollectionSource source;

  @Column(name = "label", nullable = false, length = 100)
  private String label;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private EasyCollectionConnectionStatus status;

  @Column(name = "connection_ref", length = 200)
  private String connectionRef;

  @Column(name = "last_success_at")
  private LocalDateTime lastSuccessAt;

  @Builder
  public EasyCollectionConnection(EasyBusiness business, EasyCollectionSource source,
      String label) {
    this.business = business;
    this.source = source;
    this.label = label == null || label.isBlank() ? "default" : label.trim();
    this.status = EasyCollectionConnectionStatus.ACTIVE;
  }

  public void markSuccess(LocalDateTime when) {
    this.lastSuccessAt = when;
  }

  public void disable() {
    this.status = EasyCollectionConnectionStatus.DISABLED;
  }

  /** Activation is called only after the management readiness gate passes. */
  public void enable() {
    this.status = EasyCollectionConnectionStatus.ACTIVE;
  }

  public void markNeedsReauth() {
    this.status = EasyCollectionConnectionStatus.NEEDS_REAUTH;
  }

  public void updateConnectionRef(String connectionRef) {
    this.connectionRef = connectionRef;
    if (this.status == EasyCollectionConnectionStatus.NEEDS_REAUTH) {
      this.status = EasyCollectionConnectionStatus.ACTIVE;
    }
  }
}
