package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "lesson_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonProduct extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "lesson_id")
  private Long lessonId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "academy_id", nullable = false)
  private Academy academy;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Lob
  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "school_name", length = 100)
  private String schoolName;

  @Column(name = "grade_label", length = 50)
  private String gradeLabel;

  @Enumerated(EnumType.STRING)
  @Column(name = "lesson_type", length = 20)
  private LessonType lessonType;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_group", length = 20)
  private TargetGroup targetGroup;

  @Column(name = "price_amount")
  private Integer priceAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "price_unit", length = 20)
  private PriceUnit priceUnit;

  @Column(name = "price_summary", length = 100)
  private String priceSummary;

  @Column(name = "max_capacity")
  private Integer maxCapacity;

  @Column(name = "current_count", nullable = false)
  private Integer currentCount = 0;

  @Enumerated(EnumType.STRING)
  @Column(name = "schedule_type", length = 20)
  private ScheduleType scheduleType;

  @Column(name = "weekly_count")
  private Integer weeklyCount;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "duration_weeks")
  private Integer durationWeeks;

  @Column(name = "duration_months")
  private Integer durationMonths;

  @Column(name = "schedule_summary", length = 200)
  private String scheduleSummary;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private LessonStatus status = LessonStatus.PUBLISHED;

  @Column(name = "is_visible", nullable = false)
  private boolean visible = true;

  @Column(name = "tab_guidance_label", length = 120)
  private String tabGuidanceLabel;

  @Column(name = "tab_short_name", length = 80)
  private String tabShortName;

  @Column(name = "kicker", length = 200)
  private String kicker;

  @Column(name = "highlight", length = 300)
  private String highlight;

  @Column(name = "learner_need", length = 300)
  private String learnerNeed;

  @Column(name = "core_detail_title", length = 200)
  private String coreDetailTitle;

  @Column(name = "explanation_1", length = 500)
  private String explanation1;

  @Column(name = "explanation_2", length = 500)
  private String explanation2;

  @Column(name = "explanation_3", length = 500)
  private String explanation3;

  @Enumerated(EnumType.STRING)
  @Column(name = "theme_key", length = 20)
  private LessonTheme theme;

  /**
   * 대표 미디어(히어로) — 이미지 key. {@link #mediaVideoId}와 배타(XOR).
   *
   * <p>둘 다 null이면 "미디어 없음"이다. media_type 컬럼은 두지 않는다 — 이 두 필드에서 파생된다.
   */
  @Column(name = "media_image_key", length = 500)
  private String mediaImageKey;

  /**
   * 대표 미디어 — {@code academy_videos.video_id} 참조. {@link #mediaImageKey}와 배타(XOR).
   *
   * <p>연관관계 매핑({@code @ManyToOne})이 아니라 raw Long이다. 매핑하면 {@code LessonResponse.from()}
   * 경로에서 LAZY 프록시가 트랜잭션 밖으로 새어나간다. 참조 무결성은 FK가 잡는다.
   *
   * <p>FK가 {@code ON DELETE SET NULL}이라 영상 row가 사라지면 이 값도 조용히 null이 된다.
   * 사용자 대면 삭제 차단은 {@code AcademyVideoService.delete()}의 참조 검사가 담당한다.
   */
  @Column(name = "media_video_id")
  private Long mediaVideoId;

  /** 수업 OT 영상. 대표 미디어와 독립적으로 같은 학원 영상 풀을 참조한다. */
  @Column(name = "ot_video_id")
  private Long otVideoId;

  @Column(name = "creation_key", length = 36)
  private String creationKey;

  @Column(name = "authoring_revision")
  private Long authoringRevision;

  @ElementCollection(targetClass = LessonWeekday.class)
  @CollectionTable(name = "lesson_weekdays", joinColumns = @JoinColumn(name = "lesson_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "weekday", nullable = false, length = 20)
  private Set<LessonWeekday> weekdays = new HashSet<>();

  @Builder
  public LessonProduct(Academy academy, String name, String description,
      String schoolName, String gradeLabel,
      LessonType lessonType, TargetGroup targetGroup, Integer priceAmount, PriceUnit priceUnit,
      String priceSummary, Integer maxCapacity, Integer currentCount,
      ScheduleType scheduleType, Integer weeklyCount, LocalDate startDate,
      LocalDate endDate, Integer durationWeeks, Integer durationMonths,
      String scheduleSummary, LessonStatus status, Boolean visible,
      String tabGuidanceLabel, String tabShortName, String kicker, String highlight,
      String learnerNeed, String coreDetailTitle, String explanation1, String explanation2,
      String explanation3, LessonTheme theme, String mediaImageKey, Long mediaVideoId,
      Long otVideoId,
      String creationKey, Long authoringRevision,
      Set<LessonWeekday> weekdays) {
    this.academy = academy;
    this.name = name;
    this.description = description;
    this.schoolName = schoolName;
    this.gradeLabel = gradeLabel;
    this.lessonType = lessonType;
    // 편집기가 수강 대상을 받지 않으므로 null을 그대로 보존한다.
    // HIGH로 정규화하면 강사가 지정한 적 없는 대상이 공개 카드와 검색 필터에 노출된다.
    this.targetGroup = targetGroup;
    this.priceAmount = priceAmount;
    this.priceUnit = priceUnit;
    this.priceSummary = priceSummary;
    this.maxCapacity = maxCapacity;
    this.currentCount = currentCount != null ? currentCount : 0;
    this.scheduleType = scheduleType;
    this.weeklyCount = weeklyCount;
    this.startDate = startDate;
    this.endDate = endDate;
    this.durationWeeks = durationWeeks;
    this.durationMonths = durationMonths;
    this.scheduleSummary = scheduleSummary;
    this.status = status != null ? status : LessonStatus.PUBLISHED;
    this.visible = visible == null || visible;
    this.tabGuidanceLabel = tabGuidanceLabel;
    this.tabShortName = tabShortName;
    this.kicker = kicker;
    this.highlight = highlight;
    this.learnerNeed = learnerNeed;
    this.coreDetailTitle = coreDetailTitle;
    this.explanation1 = explanation1;
    this.explanation2 = explanation2;
    this.explanation3 = explanation3;
    this.theme = theme;
    this.mediaImageKey = mediaImageKey;
    this.mediaVideoId = mediaVideoId;
    this.otVideoId = otVideoId;
    this.creationKey = creationKey;
    this.authoringRevision = authoringRevision;
    if (weekdays != null) this.weekdays = weekdays;
  }

  public void updateDetails(String name, String description,
      LessonType lessonType, TargetGroup targetGroup, Integer priceAmount, PriceUnit priceUnit,
      String priceSummary, Integer maxCapacity,
      ScheduleType scheduleType, Integer weeklyCount, LocalDate startDate,
      LocalDate endDate, Integer durationWeeks, Integer durationMonths,
      String scheduleSummary, LessonStatus status, Set<LessonWeekday> weekdays) {
    // 레거시 PUT 경로다. 저작 전용 필드(tabGuidanceLabel 이하)와 대표 미디어는 이 엔드포인트가
    // 소유하지 않으므로 현재 값을 그대로 넘겨 보존한다. 여기에 null을 넘기면 레거시 PUT 한 번에
    // 편집기에서 등록한 히어로 미디어가 지워진다.
    updateAuthoringDetails(
        name, description, schoolName, gradeLabel,
        lessonType, targetGroup, priceAmount, priceUnit, priceSummary,
        maxCapacity, scheduleType, weeklyCount, startDate, endDate, durationWeeks,
        durationMonths, scheduleSummary, status, weekdays, tabGuidanceLabel, tabShortName,
        kicker, highlight, learnerNeed, coreDetailTitle, explanation1, explanation2,
        explanation3, theme, mediaImageKey, mediaVideoId, otVideoId);
  }

  public boolean updateAuthoringDetails(
      String name, String description, String schoolName, String gradeLabel,
      LessonType lessonType, TargetGroup targetGroup,
      Integer priceAmount, PriceUnit priceUnit, String priceSummary, Integer maxCapacity,
      ScheduleType scheduleType, Integer weeklyCount, LocalDate startDate,
      LocalDate endDate, Integer durationWeeks, Integer durationMonths,
      String scheduleSummary, LessonStatus status, Set<LessonWeekday> weekdays,
      String tabGuidanceLabel, String tabShortName, String kicker, String highlight,
      String learnerNeed, String coreDetailTitle, String explanation1, String explanation2,
      String explanation3, LessonTheme theme, String mediaImageKey, Long mediaVideoId,
      Long otVideoId) {
    if (mediaImageKey != null && mediaVideoId != null) {
      throw new IllegalArgumentException("대표 미디어는 이미지와 영상 중 하나만 지정할 수 있습니다.");
    }
    TargetGroup normalizedTargetGroup = targetGroup;
    LessonStatus normalizedStatus = status != null ? status : LessonStatus.PUBLISHED;
    Set<LessonWeekday> normalizedWeekdays = weekdays == null ? Set.of() : new HashSet<>(weekdays);
    boolean changed = !Objects.equals(this.name, name)
        || !Objects.equals(this.description, description)
        || !Objects.equals(this.schoolName, schoolName)
        || !Objects.equals(this.gradeLabel, gradeLabel)
        || this.lessonType != lessonType
        || this.targetGroup != normalizedTargetGroup
        || !Objects.equals(this.priceAmount, priceAmount)
        || this.priceUnit != priceUnit
        || !Objects.equals(this.priceSummary, priceSummary)
        || !Objects.equals(this.maxCapacity, maxCapacity)
        || this.scheduleType != scheduleType
        || !Objects.equals(this.weeklyCount, weeklyCount)
        || !Objects.equals(this.startDate, startDate)
        || !Objects.equals(this.endDate, endDate)
        || !Objects.equals(this.durationWeeks, durationWeeks)
        || !Objects.equals(this.durationMonths, durationMonths)
        || !Objects.equals(this.scheduleSummary, scheduleSummary)
        || this.status != normalizedStatus
        || !Objects.equals(this.weekdays, normalizedWeekdays)
        || !Objects.equals(this.tabGuidanceLabel, tabGuidanceLabel)
        || !Objects.equals(this.tabShortName, tabShortName)
        || !Objects.equals(this.kicker, kicker)
        || !Objects.equals(this.highlight, highlight)
        || !Objects.equals(this.learnerNeed, learnerNeed)
        || !Objects.equals(this.coreDetailTitle, coreDetailTitle)
        || !Objects.equals(this.explanation1, explanation1)
        || !Objects.equals(this.explanation2, explanation2)
        || !Objects.equals(this.explanation3, explanation3)
        || this.theme != theme
        || !Objects.equals(this.mediaImageKey, mediaImageKey)
        || !Objects.equals(this.mediaVideoId, mediaVideoId)
        || !Objects.equals(this.otVideoId, otVideoId);

    if (!changed) {
      return false;
    }

    this.name = name;
    this.description = description;
    this.schoolName = schoolName;
    this.gradeLabel = gradeLabel;
    this.lessonType = lessonType;
    this.targetGroup = normalizedTargetGroup;
    this.priceAmount = priceAmount;
    this.priceUnit = priceUnit;
    this.priceSummary = priceSummary;
    this.maxCapacity = maxCapacity;
    this.scheduleType = scheduleType;
    this.weeklyCount = weeklyCount;
    this.startDate = startDate;
    this.endDate = endDate;
    this.durationWeeks = durationWeeks;
    this.durationMonths = durationMonths;
    this.scheduleSummary = scheduleSummary;
    this.status = normalizedStatus;
    this.weekdays.clear();
    this.weekdays.addAll(normalizedWeekdays);
    this.tabGuidanceLabel = tabGuidanceLabel;
    this.tabShortName = tabShortName;
    this.kicker = kicker;
    this.highlight = highlight;
    this.learnerNeed = learnerNeed;
    this.coreDetailTitle = coreDetailTitle;
    this.explanation1 = explanation1;
    this.explanation2 = explanation2;
    this.explanation3 = explanation3;
    this.theme = theme;
    this.mediaImageKey = mediaImageKey;
    this.mediaVideoId = mediaVideoId;
    this.otVideoId = otVideoId;
    incrementAuthoringRevision();
    return true;
  }

  public LessonTheme getEffectiveTheme() {
    return theme != null ? theme : LessonTheme.NAVY;
  }

  public long getEffectiveAuthoringRevision() {
    return authoringRevision != null ? authoringRevision : 0L;
  }

  public void incrementAuthoringRevision() {
    authoringRevision = getEffectiveAuthoringRevision() + 1L;
  }
  public void approveOneSeat() {
    if (isFull()) {
      throw new IllegalStateException("수업 정원이 가득 찼습니다.");
    }
    currentCount += 1;
  }

  public void cancelOneSeat() {
    if (currentCount <= 0) {
      throw new IllegalStateException("취소할 신청 인원이 없습니다.");
    }
    currentCount -= 1;
  }

  /** 정원 미정(null)은 무제한 접수로 본다. 편집기가 정원을 받지 않으므로 신규 수업은 대부분 null이다. */
  public boolean isFull() {
    return maxCapacity != null && currentCount >= maxCapacity;
  }

  /** 정원 미정이면 남은 자리를 계산할 수 없으므로 null을 반환한다(무제한). */
  public Integer remainingCapacity() {
    return maxCapacity == null ? null : Math.max(0, maxCapacity - currentCount);
  }

  public void updateVisibility(boolean visible) {
    this.visible = visible;
  }

  public void hideFromTutorManagement() {
    boolean authoredStateChanged = status != LessonStatus.HIDDEN;
    this.status = LessonStatus.HIDDEN;
    this.visible = false;
    if (authoredStateChanged) {
      incrementAuthoringRevision();
    }
  }

}
