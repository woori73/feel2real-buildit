package f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 연결된 인스타그램 계정. 토큰은 암호화해서만 저장한다(SnsTokenCipher). */
@Entity
@Table(name = "sns_accounts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SnsAccount {

  public enum Platform { INSTAGRAM }

  public enum Status { CONNECTED, EXPIRED, DISCONNECTED, ERROR }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "platform", nullable = false, length = 20)
  private Platform platform;

  @Column(name = "ig_user_id", length = 64)
  private String igUserId;

  @Column(name = "username", length = 100)
  private String username;

  @Column(name = "access_token_enc", columnDefinition = "TEXT")
  private String accessTokenEnc;

  @Column(name = "token_expires_at")
  private LocalDateTime tokenExpiresAt;

  @Column(name = "token_refreshed_at")
  private LocalDateTime tokenRefreshedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private Status status;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public static SnsAccount instagram(String igUserId, String username) {
    SnsAccount a = new SnsAccount();
    a.platform = Platform.INSTAGRAM;
    a.igUserId = igUserId;
    a.username = username;
    a.status = Status.CONNECTED;
    return a;
  }

  @PrePersist
  void onCreate() {
    createdAt = SnsTime.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = SnsTime.now();
  }
}
