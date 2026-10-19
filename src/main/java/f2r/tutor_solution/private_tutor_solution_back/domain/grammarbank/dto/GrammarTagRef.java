package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import java.math.BigDecimal;

public record GrammarTagRef(
    Long tagId,
    String nameKo,
    boolean primary,
    boolean auto,
    BigDecimal confidence
) {
}
