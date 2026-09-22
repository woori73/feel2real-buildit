package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * A registered local collector agent — one process running on one business's own PC, driving a
 * visible browser the human authenticates in. EASY only ever stores a hash of its pairing token,
 * never the token itself.
 */
@Entity
@Table(name = "easy_local_collectors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyLocalCollector extends BaseEntity {
  public enum Status { ACTIVE, REVOKED }

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "collector_id")
  private Long collectorId;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;
  @Column(name = "name", nullable = false, length = 100)
  private String name;
  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private Status status = Status.ACTIVE;
  @Column(name = "last_heartbeat_at")
  private LocalDateTime lastHeartbeatAt;

  @Builder
  public EasyLocalCollector(EasyBusiness business, String name, String tokenHash) {
    if (business == null) throw new IllegalArgumentException("business is required.");
    if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required.");
    if (tokenHash == null || !tokenHash.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("tokenHash must be SHA-256.");
    this.business = business;
    this.name = name.trim();
    this.tokenHash = tokenHash;
  }

  public void heartbeat() {
    this.lastHeartbeatAt = LocalDateTime.now();
  }

  public void revoke() {
    this.status = Status.REVOKED;
  }

  public boolean isActive() {
    return status == Status.ACTIVE;
  }
}
