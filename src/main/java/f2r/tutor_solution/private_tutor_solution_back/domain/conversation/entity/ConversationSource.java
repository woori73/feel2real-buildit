package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "conversation_sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(ConversationSourceId.class)
public class ConversationSource {

  @Id
  @Column(name = "conversation_id")
  private Long conversationId;

  @Id
  @Column(name = "source_id")
  private Long sourceId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "conversation_id", insertable = false, updatable = false)
  private Conversation conversation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id", insertable = false, updatable = false)
  private Source source;

  @Builder
  public ConversationSource(Long conversationId, Long sourceId) {
    this.conversationId = conversationId;
    this.sourceId = sourceId;
  }
}
