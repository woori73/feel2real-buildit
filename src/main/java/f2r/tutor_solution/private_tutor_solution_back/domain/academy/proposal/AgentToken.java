package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 시각 편집기 Agent Bearer 토큰. 학원 1개에 바인딩되고 SHA-256 해시만 저장한다. */
@Entity
@Table(name = "academy_visual_editor_agent_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentToken {

  public static final String SCOPE_READ = "ve:read";
  public static final String SCOPE_PROPOSE = "ve:propose";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "token_id")
  private Long tokenId;

  @Column(name = "academy_id", nullable = false)
  private Long academyId;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "label", nullable = false, length = 100)
  private String label;

  @Column(name = "scopes", nullable = false, length = 100)
  private String scopes;

  @Column(name = "created_by", nullable = false)
  private Long createdBy;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "last_used_at")
  private LocalDateTime lastUsedAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public AgentToken(Long academyId, String tokenHash, String label, String scopes,
      Long createdBy, LocalDateTime expiresAt) {
    this.academyId = academyId;
    this.tokenHash = tokenHash;
    this.label = label;
    this.scopes = scopes;
    this.createdBy = createdBy;
    this.expiresAt = expiresAt;
  }

  public boolean isActive(LocalDateTime now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  public boolean hasScope(String scope) {
    for (String s : scopes.split(",")) {
      if (s.trim().equals(scope)) {
        return true;
      }
    }
    return false;
  }

  public void revoke(LocalDateTime now) {
    if (revokedAt == null) {
      this.revokedAt = now;
    }
  }

  public void touch(LocalDateTime now) {
    this.lastUsedAt = now;
  }
}
