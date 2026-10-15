package f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplication;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplicationStatus;

import java.time.LocalDateTime;

public record LessonApplicationResponse(
    Long applicationId,
    Long lessonId,
    String lessonName,
    Long academyId,
    String academyName,
    String academySlug,
    Long studentId,
    String studentName,
    LessonApplicationStatus status,
    LocalDateTime appliedAt,
    LocalDateTime canceledAt,
    Long conversationId,
    String conversationTitle
) {
  public static LessonApplicationResponse from(LessonApplication application) {
    var conv = application.getConversation();
    return new LessonApplicationResponse(
        application.getApplicationId(),
        application.getLesson().getLessonId(),
        application.getLesson().getName(),
        application.getLesson().getAcademy().getAcademyId(),
        application.getLesson().getAcademy().getName(),
        application.getLesson().getAcademy().getSlug(),
        application.getStudent().getStudentId(),
        application.getStudent().getUser().getName(),
        application.getStatus(),
        application.getAppliedAt(),
        application.getCanceledAt(),
        conv != null ? conv.getConversationId() : null,
        conv != null ? conv.getTitle() : null);
  }
}
