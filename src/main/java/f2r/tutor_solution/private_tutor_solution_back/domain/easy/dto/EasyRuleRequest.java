package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

public record EasyRuleRequest(
    String patternType,
    String pattern,
    Long accountId,
    Integer priority,
    Long amountMin,
    Long amountMax,
    String direction,
    Boolean active
) {
}
