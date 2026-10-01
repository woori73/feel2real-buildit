package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * NewStart 사이트 내부 1:1 쪽지. 네이버 카페 쪽지와 무관하다.
 *
 * <p>송수신자는 {@code users.user_id}로만 저장한다. 닉네임 문자열·loginId·이메일
 * 저장을 금지하고, 표시 이름은 조회 시
 * {@code NewStartMemberProfileService}로 resolve한다(채팅과 동일 정책).
 * 발신자는 서버 principal에서만 결정한다 — 요청값으로 받는 통로가 없다.
 */
@Entity
@Table(name = "newstart_direct_messages",
    indexes = {
        @Index(name = "idx_newstart_dm_inbox", columnList = "receiver_user_id, message_id"),
        @Index(name = "idx_newstart_dm_unread", columnList = "receiver_user_id, is_read, message_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewStartDirectMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "message_id")
  private Long messageId;

  @Column(name = "sender_user_id", nullable = false)
  private Long senderUserId;

  @Column(name = "receiver_user_id", nullable = false)
  private Long receiverUserId;

  @Column(name = "message", length = 1000, nullable = false)
  private String message;

  @Column(name = "is_read", nullable = false)
  @Builder.Default
  private Boolean isRead = false;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
