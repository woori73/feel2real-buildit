package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;

import java.time.LocalDate;
import java.util.Set;

public record LessonCollectionItemRequest(
    Long lessonId,
    String creationKey,
    Long expectedAuthoringRevision,
    String name,
    String description,
    String schoolName,
    String gradeLabel,
    LessonType lessonType,
    TargetGroup targetGroup,
    Integer priceAmount,
    PriceUnit priceUnit,
    String priceSummary,
    Integer maxCapacity,
    ScheduleType scheduleType,
    Integer weeklyCount,
    Set<LessonWeekday> weekdays,
    LocalDate startDate,
    LocalDate endDate,
    Integer durationWeeks,
    Integer durationMonths,
    String scheduleSummary,
    LessonStatus status,
    String tabGuidanceLabel,
    String tabShortName,
    String kicker,
    String highlight,
    String learnerNeed,
    String coreDetailTitle,
    String explanation1,
    String explanation2,
    String explanation3,
    LessonTheme theme,
    /** 대표 미디어 이미지 key. {@link #mediaVideoId}와 배타(XOR). 편집기 컬렉션 저장 전용. */
    String mediaImageKey,
    /** 대표 미디어 영상 id. {@link #mediaImageKey}와 배타(XOR). 내 학원 소유 영상만 허용. */
    Long mediaVideoId,
    /** 수업 OT 영상 id. 대표 미디어와 독립이며 내 학원 소유 영상만 허용. */
    Long otVideoId
) {
}
