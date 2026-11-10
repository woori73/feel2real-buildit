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

  /** 비민감 조회 조건 JSON(기관코드·고객구분·계좌번호). 비밀번호·인증서·OTP는 절대 넣지 않는다. */
  @Column(name = "provider_config", length = 500)
  private String providerConfig;

  /** 마지막 실패 분류 코드(AUTH_REQUIRED 등). 기관 원문 오류·자격증명은 저장하지 않는다. */
  @Column(name = "last_error_code", length = 40)
  private String lastErrorCode;

  @Column(name = "disconnected_at")
  private LocalDateTime disconnectedAt;

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
    this.lastErrorCode = null;
  }

  public void recordFailure(String errorCode) {
    this.lastErrorCode = errorCode;
  }

  public void updateProviderConfig(String providerConfig) {
    this.providerConfig = providerConfig;
  }

  /**
   * 연결 해제 — 조회를 멈추고 인증 참조(connectedId 등)를 비활성화한다.
   * 이미 저장된 장부·증빙·원본은 건드리지 않는다. 다시 쓰려면 인증 참조를 새로 등록해야 한다.
   */
  public void disconnect(LocalDateTime when) {
    this.status = EasyCollectionConnectionStatus.DISCONNECTED;
    this.connectionRef = null;
    this.disconnectedAt = when;
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
      this.lastErrorCode = null;
    }
    if (this.status == EasyCollectionConnectionStatus.DISCONNECTED
        && connectionRef != null && !connectionRef.isBlank()) {
      // 재연결은 새 인증 참조 등록으로만 가능하다. 자동수집 켜기는 별도 동작(enable)이다.
      this.status = EasyCollectionConnectionStatus.DISABLED;
      this.disconnectedAt = null;
    }
  }
}
