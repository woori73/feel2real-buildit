package f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 문구 생성에 쓰는 브랜드 설정. 고정 문구는 생성된 본문 위/아래에 그대로 붙는다. */
@Entity
@Table(name = "sns_brand_presets")
@Getter
@Setter
@NoArgsConstructor
public class SnsBrandPreset {

  public enum FixedTextPosition { TOP, BOTTOM }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "industry", columnDefinition = "TEXT")
  private String industry;

  @Column(name = "audience", columnDefinition = "TEXT")
  private String audience;

  @Column(name = "purpose", columnDefinition = "TEXT")
  private String purpose;

  @Column(name = "instructions", columnDefinition = "TEXT")
  private String instructions;

  @Column(name = "fixed_text", columnDefinition = "TEXT")
  private String fixedText;

  @Enumerated(EnumType.STRING)
  @Column(name = "fixed_text_position", nullable = false, length = 10)
  private FixedTextPosition fixedTextPosition = FixedTextPosition.BOTTOM;

  /** 줄마다 하나. */
  @Column(name = "banned_phrases", columnDefinition = "TEXT")
  private String bannedPhrases;

  @Column(name = "language", nullable = false, length = 10)
  private String language = "ko";

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public List<String> bannedPhraseList() {
    if (bannedPhrases == null || bannedPhrases.isBlank()) return List.of();
    return Arrays.stream(bannedPhrases.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  @PrePersist
  void onCreate() {
    createdAt = SnsTime.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = SnsTime.now();
  }
}
