package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto;

public record GeminiGenerateRequest(
    String school,
    String grade,
    String exam,
    String grammar,
    String passage,
    Integer count,
    String difficulty) {
}
