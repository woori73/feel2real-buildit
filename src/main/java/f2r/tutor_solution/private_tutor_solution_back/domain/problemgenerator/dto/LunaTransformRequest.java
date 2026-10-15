package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto;

public record LunaTransformRequest(
    String sourceQuestion,
    String instructions,
    String difficulty,
    String digitalTwinContext
) {}
