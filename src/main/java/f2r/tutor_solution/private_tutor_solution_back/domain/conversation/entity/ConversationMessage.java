package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "message_id")
  private Long messageId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "conversation_id", nullable = false)
  private Conversation conversation;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 10)
  private MessageRole role;

  @Lob
  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Builder
  public ConversationMessage(Conversation conversation, MessageRole role, String content) {
    this.conversation = conversation;
    this.role = role;
    this.content = content;
    this.createdAt = LocalDateTime.now();
  }
}
