package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.dto;

public record LessonCommunityContextResponse(
    Long lessonId,
    String lessonName,
    Long academyId,
    String academyName,
    String role,
    boolean tutorOwner,
    boolean admin
) {}
