package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.cafe;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** JDBC 쓰기 모델의 JPA schema-validation 매핑. 비밀 필드는 getter/직렬화를 제공하지 않는다. */
@Entity
@Table(name = "newstart_cafe_connections")
public class CafeConnection {
  @Id @Column(name = "owner_id") private Long ownerId;
  @Column(name = "board_ids", nullable = false, columnDefinition = "TEXT") private String boardIds;
  @Column(name = "access_token_enc", columnDefinition = "TEXT") private String accessTokenEnc;
  @Column(name = "refresh_token_enc", columnDefinition = "TEXT") private String refreshTokenEnc;
  @Column(name = "token_expires_at", columnDefinition = "TIMESTAMP(6)") private LocalDateTime tokenExpiresAt;
  @Column(name = "state_hash", length = 64, columnDefinition = "CHAR(64)") private String stateHash;
  @Column(name = "state_expires_at", columnDefinition = "TIMESTAMP(6)") private LocalDateTime stateExpiresAt;
  @Column(name = "connection_epoch", nullable = false, length = 36) private String connectionEpoch;
  @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP(6)") private LocalDateTime updatedAt;
  protected CafeConnection() {}
}
