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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 자동화 규칙: 언제·어떤 브랜드·어떤 참고 이미지로 피드를 만들지. */
@Entity
@Table(name = "sns_automation_rules")
@Getter
@Setter
@NoArgsConstructor
public class SnsAutomationRule {

  public enum ScheduleType { DAILY, EVERY_OTHER_DAY, WEEKLY }

  /** PUBLISH(자동 발행)는 1차에서 켜지 않는다 — 저장돼 있어도 LIST처럼 동작한다(HANDOFF §3-3). */
  public enum SaveMode { PUBLISH, LIST, PAUSED }

  public enum ImageStyle { PHOTO, ILLUSTRATION, THREE_D }

  public static final List<String> ASPECT_RATIOS = List.of("1:1", "4:5", "16:9");

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "title", nullable = false, length = 150)
  private String title;

  @Column(name = "account_id")
  private Long accountId;

  @Column(name = "brand_id", nullable = false)
  private Long brandId;

  @Column(name = "reference_image_id")
  private Long referenceImageId;

  @Column(name = "image_prompt", nullable = false, columnDefinition = "TEXT")
  private String imagePrompt;

  @Enumerated(EnumType.STRING)
  @Column(name = "image_style", nullable = false, length = 30)
  private ImageStyle imageStyle = ImageStyle.PHOTO;

  @Column(name = "aspect_ratio", nullable = false, length = 10)
  private String aspectRatio = "1:1";

  @Column(name = "image_count", nullable = false)
  private int imageCount = 1;

  /** 공백으로 구분한 고정 해시태그(최대 5개). */
  @Column(name = "fixed_hashtags", length = 300)
  private String fixedHashtags;

  @Column(name = "ai_hashtags", nullable = false)
  private boolean aiHashtags = true;

  @Enumerated(EnumType.STRING)
  @Column(name = "schedule_type", nullable = false, length = 20)
  private ScheduleType scheduleType = ScheduleType.DAILY;

  /** WEEKLY일 때 요일 목록(MON,WED 등). */
  @Column(name = "schedule_days", length = 40)
  private String scheduleDays;

  @Column(name = "run_time", nullable = false)
  private LocalTime runTime = LocalTime.of(19, 0);

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "save_mode", nullable = false, length = 10)
  private SaveMode saveMode = SaveMode.LIST;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  /** 규칙별 월 상한(원). 비어 있으면 전체 상한만 적용. */
  @Column(name = "monthly_budget_krw")
  private Integer monthlyBudgetKrw;

  @Column(name = "last_run_on")
  private LocalDate lastRunOn;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public List<String> fixedHashtagList() {
    if (fixedHashtags == null || fixedHashtags.isBlank()) return List.of();
    return Arrays.stream(fixedHashtags.trim().split("\\s+"))
        .map(t -> t.startsWith("#") ? t : "#" + t)
        .distinct().limit(5).toList();
  }

  /** 오늘(서울 날짜) 이 규칙을 돌려야 하는 날인지. 시간은 따로 본다. */
  public boolean isScheduledOn(LocalDate date) {
    if (startDate != null && date.isBefore(startDate)) return false;
    return switch (scheduleType) {
      case DAILY -> true;
      case EVERY_OTHER_DAY -> startDate == null || ChronoUnit.DAYS.between(startDate, date) % 2 == 0;
      case WEEKLY -> weeklyDays().contains(date.getDayOfWeek());
    };
  }

  public List<DayOfWeek> weeklyDays() {
    if (scheduleDays == null || scheduleDays.isBlank()) return List.of();
    return Arrays.stream(scheduleDays.split(","))
        .map(s -> s.trim().toUpperCase(Locale.ROOT))
        .filter(s -> !s.isEmpty())
        .map(SnsAutomationRule::dayOf)
        .toList();
  }

  private static DayOfWeek dayOf(String s) {
    for (DayOfWeek d : DayOfWeek.values()) {
      if (d.name().startsWith(s)) return d;
    }
    throw new IllegalArgumentException("알 수 없는 요일: " + s);
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
