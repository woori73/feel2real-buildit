package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BuildIt AI 채팅 세션 (P1).
 *
 * <p>고객의 AI 채팅 세션을 기록하고 토큰 사용량을 추적한다.
 */
@Entity
@Table(
    name = "academy_ai_sessions",
    indexes = {@Index(name = "idx_academy_session", columnList = "academyId, startedAt")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyAiSession {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long academyId;

  @Column(nullable = false, length = 100)
  private String tutorId;

  @Column(nullable = false)
  private LocalDateTime startedAt;

  private LocalDateTime endedAt;

  @Column(nullable = false)
  private Integer messageCount = 0;

  @Column(nullable = false)
  private Integer totalTokens = 0;

  public AcademyAiSession(Long academyId, String tutorId) {
    this.academyId = academyId;
    this.tutorId = tutorId;
    this.startedAt = LocalDateTime.now();
  }

  public void incrementMessage(int tokens) {
    this.messageCount++;
    this.totalTokens += tokens;
  }

  public void end() {
    this.endedAt = LocalDateTime.now();
  }
}
