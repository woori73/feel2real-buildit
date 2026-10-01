package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * NewStart 접속상태 + 쪽지 수신 설정. PK는 {@code users.user_id} 그대로이며
 * 별도 식별자를 만들지 않는다. 행은 첫 heartbeat ping 시 생성된다.
 */
@Entity
@Table(name = "newstart_member_presence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewStartMemberPresence {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "last_seen_at")
  private LocalDateTime lastSeenAt;

  @Column(name = "hide_online", nullable = false)
  @Builder.Default
  private Boolean hideOnline = false;

  @Column(name = "dm_blocked", nullable = false)
  @Builder.Default
  private Boolean dmBlocked = false;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
