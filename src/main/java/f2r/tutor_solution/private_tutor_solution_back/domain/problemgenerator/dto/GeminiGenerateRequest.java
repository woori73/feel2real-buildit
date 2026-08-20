package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto;

public record GeminiGenerateRequest(
    String apiKey,
    String grammar,
    String passage,
    Integer count,
    String difficulty,
    String materialTitle) {
}
