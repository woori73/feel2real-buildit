package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "codi_call_attempts") @Getter @NoArgsConstructor
class CodiCallAttempt {
  @Id @Column(length = 36) private String id;
  @Column(nullable = false) private Long ownerId;
  @Column(nullable = false) private Long academyId;
  @Column(nullable = false, length = 64) private String fingerprint;
  @Column(length = 128) private String conversationId;
  @Column(nullable = false, length = 16) private String status;

  CodiCallAttempt(String id, Long ownerId, Long academyId, String fingerprint) {
    this.id = id;
    this.ownerId = ownerId;
    this.academyId = academyId;
    this.fingerprint = fingerprint;
    status = "unconfirmed";
  }
}
