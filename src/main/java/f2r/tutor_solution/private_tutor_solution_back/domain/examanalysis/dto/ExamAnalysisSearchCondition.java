package f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.dto;

public record ExamAnalysisSearchCondition(
    String keyword,
    String schoolName,
    Integer grade,
    String semester,
    String examType,
    Integer subjectId
) {
}
