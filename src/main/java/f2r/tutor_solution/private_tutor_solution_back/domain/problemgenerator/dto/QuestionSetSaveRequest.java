package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto;

public record QuestionSetSaveRequest(
    String title,
    Long materialId,
    String grammar,
    String promptPassage,
    String model,
    String rawResponseJson) {
}
