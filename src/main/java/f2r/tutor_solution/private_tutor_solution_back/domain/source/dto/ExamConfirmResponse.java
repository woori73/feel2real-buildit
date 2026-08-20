package f2r.tutor_solution.private_tutor_solution_back.domain.source.dto;

public record ExamConfirmResponse(
    Long sourceId,
    Long examId,
    Long scoreId,
    Integer totalQuestions,
    Integer correctCount,
    boolean dedupMatched
) {}
