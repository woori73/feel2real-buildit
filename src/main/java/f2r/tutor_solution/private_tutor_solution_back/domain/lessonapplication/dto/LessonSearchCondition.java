package f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonWeekday;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;

public record LessonSearchCondition(
    Integer subjectId,
    TargetGroup targetGroup,
    String city,
    String district,
    LessonType lessonType,
    LessonWeekday weekday,
    Integer maxPrice,
    Integer weeklyCount
) {
}
