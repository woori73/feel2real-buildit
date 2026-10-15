package f2r.tutor_solution.private_tutor_solution_back.domain.academy.exception;

public record LessonCollectionErrorResponse(
    String code,
    String message,
    boolean retryable
) {
}
