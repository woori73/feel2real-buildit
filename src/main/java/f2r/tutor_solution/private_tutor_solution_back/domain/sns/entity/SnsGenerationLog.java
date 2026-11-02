package f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 생성·발행 호출 기록. 월 상한은 이 표의 cost_krw 합계로 계산한다. */
@Entity
@Table(name = "sns_generation_logs")
@Getter
@Setter
@NoArgsConstructor
public class SnsGenerationLog {

  public enum Kind { IMAGE, TEXT, PUBLISH }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "rule_id")
  private Long ruleId;

  @Column(name = "post_id")
  private Long postId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 20)
  private Kind kind;

  @Column(name = "model", length = 100)
  private String model;

  @Column(name = "prompt", columnDefinition = "TEXT")
  private String prompt;

  @Column(name = "cost_krw", nullable = false)
  private int costKrw;

  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  @Column(name = "success", nullable = false)
  private boolean success;

  @Column(name = "error_message", length = 500)
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  public static SnsGenerationLog of(Kind kind, Long ruleId, Long postId, String model, String prompt,
      int costKrw, long durationMs, boolean success, String error) {
    SnsGenerationLog l = new SnsGenerationLog();
    l.kind = kind;
    l.ruleId = ruleId;
    l.postId = postId;
    l.model = model;
    l.prompt = prompt;
    l.costKrw = costKrw;
    l.durationMs = durationMs;
    l.success = success;
    l.errorMessage = error == null ? null : error.length() > 500 ? error.substring(0, 500) : error;
    return l;
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = SnsTime.now();
  }
}
