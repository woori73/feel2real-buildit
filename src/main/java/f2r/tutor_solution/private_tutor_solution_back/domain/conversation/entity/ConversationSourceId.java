package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity;

import java.io.Serializable;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ConversationSourceId implements Serializable {
  private Long conversationId;
  private Long sourceId;
}
