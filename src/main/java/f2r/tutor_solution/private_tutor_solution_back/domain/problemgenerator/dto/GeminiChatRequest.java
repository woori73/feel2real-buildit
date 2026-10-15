package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto;

public record GeminiChatRequest(
    String apiKey,
    String message,
    String context) {
}
