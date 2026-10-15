package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 상담 스레드의 메시지 한 줄. {@code consultation_id}로 스레드에 묶인다(단발 쪽지가 아님, 설계 §2).
 *
 * <p>{@code authorRole}이 {@link MessageAuthorRole#AI_RESEARCH}면 운영자 전용이다 — 회원 스레드
 * 조회에서 제외되고 회원에게 자동 전송되지 않는다(§5). 발신 주체는 서버가 결정하며 요청값으로
 * authorRole을 받지 않는다.
 */
@Entity
@Table(name = "newstart_consultation_messages",
    indexes = {
        @Index(name = "idx_newstart_consult_msg_thread", columnList = "consultation_id, message_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewStartConsultationMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "message_id")
  private Long messageId;

  @Column(name = "consultation_id", nullable = false)
  private Long consultationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "author_role", length = 20, nullable = false)
  private MessageAuthorRole authorRole;

  @Column(name = "content", columnDefinition = "TEXT", nullable = false)
  private String content;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
