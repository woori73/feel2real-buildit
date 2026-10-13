package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

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

/** BuildIt 통합 화면의 대화 한 줄. 학원당 시간 순으로 쌓인다. */
@Entity
@Table(name = "academy_assistant_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyAssistantMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "message_id")
  private Long messageId;

  @Column(name = "academy_id", nullable = false)
  private Long academyId;

  /** USER 또는 ASSISTANT. */
  @Column(name = "role", nullable = false, length = 10)
  private String role;

  /** TEXT, RESULT(결과 카드), QUESTION(추가 정보 질문). */
  @Column(name = "kind", nullable = false, length = 20)
  private String kind;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "payload_json", columnDefinition = "TEXT")
  private String payloadJson;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public AcademyAssistantMessage(
      Long academyId, String role, String kind, String content, String payloadJson) {
    this.academyId = academyId;
    this.role = role;
    this.kind = kind;
    this.content = content;
    this.payloadJson = payloadJson;
  }
}
