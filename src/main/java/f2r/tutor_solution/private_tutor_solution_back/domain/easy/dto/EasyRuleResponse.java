package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClassificationRule;

public record EasyRuleResponse(
    Long ruleId,
    String patternType,
    String pattern,
    Long accountId,
    String accountName,
    int priority,
    Long amountMin,
    Long amountMax,
    String direction,
    boolean active
) {
  public static EasyRuleResponse from(EasyClassificationRule rule) {
    return new EasyRuleResponse(
        rule.getRuleId(),
        rule.getPatternType().name(),
        rule.getPattern(),
        rule.getAccount().getAccountId(),
        rule.getAccount().getName(),
        rule.getPriority(),
        rule.getAmountMin(),
        rule.getAmountMax(),
        rule.getDirection() == null ? null : rule.getDirection().name(),
        rule.isActive());
  }
}
