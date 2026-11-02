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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 자동화가 만든 게시물 한 건. 문구(caption)와 이미지(image)가 각각 만들어지고, 둘 다 끝나면 REVIEW로 넘어간다.
 * trial=true는 규칙 편집 화면의 시험 생성 결과(최근 주제 목록·예약 발행에서 제외).
 */
@Entity
@Table(name = "sns_posts")
@Getter
@Setter
@NoArgsConstructor
public class SnsPost {

  /** GENERATING: 문구·이미지 생성 중. REVIEW: 검토 대기. */
  public enum Status { GENERATING, REVIEW, SCHEDULED, PUBLISHED, FAILED, DISCARDED }

  public enum PartStatus { PENDING, RUNNING, DONE, FAILED }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "rule_id", nullable = false)
  private Long ruleId;

  @Column(name = "trial", nullable = false)
  private boolean trial;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private Status status = Status.GENERATING;

  @Column(name = "topic", length = 200)
  private String topic;

  /** 본문 + 고정 문구(해시태그 제외). */
  @Column(name = "caption", columnDefinition = "TEXT")
  private String caption;

  /** 공백으로 구분한 해시태그. */
  @Column(name = "hashtags", length = 1000)
  private String hashtags;

  @Enumerated(EnumType.STRING)
  @Column(name = "caption_status", nullable = false, length = 20)
  private PartStatus captionStatus = PartStatus.PENDING;

  @Column(name = "caption_attempts", nullable = false)
  private int captionAttempts;

  /** 저장 키(JSON 배열 문자열 아님, 줄바꿈 구분). 1차는 1장. */
  @Column(name = "image_paths", columnDefinition = "TEXT")
  private String imagePaths;

  @Column(name = "image_prompt", columnDefinition = "TEXT")
  private String imagePrompt;

  @Enumerated(EnumType.STRING)
  @Column(name = "image_status", nullable = false, length = 20)
  private PartStatus imageStatus = PartStatus.PENDING;

  @Column(name = "image_attempts", nullable = false)
  private int imageAttempts;

  @Column(name = "image_claimed_at")
  private LocalDateTime imageClaimedAt;

  /** 인스타그램이 이미지를 가져갈 공개 주소용 임의 토큰(/api/sns/media/{token}.jpg). */
  @Column(name = "media_token", length = 64, unique = true)
  private String mediaToken;

  @Column(name = "scheduled_at")
  private LocalDateTime scheduledAt;

  @Column(name = "publish_attempts", nullable = false)
  private int publishAttempts;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Column(name = "ig_media_id", length = 64)
  private String igMediaId;

  @Column(name = "fail_reason", length = 500)
  private String failReason;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public String firstImagePath() {
    if (imagePaths == null || imagePaths.isBlank()) return null;
    return imagePaths.split("\\R")[0].trim();
  }

  /** 인스타그램에 올릴 최종 글: 문구 + 빈 줄 + 해시태그. */
  public String publishText() {
    String c = caption == null ? "" : caption.strip();
    String h = hashtags == null ? "" : hashtags.strip();
    return h.isEmpty() ? c : c + "\n\n" + h;
  }

  public void fail(String reason) {
    this.status = Status.FAILED;
    this.failReason = reason == null ? null : reason.length() > 500 ? reason.substring(0, 500) : reason;
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
