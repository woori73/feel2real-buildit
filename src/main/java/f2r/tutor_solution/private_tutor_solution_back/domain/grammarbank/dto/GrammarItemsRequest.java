package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import java.util.List;

public record GrammarItemsRequest(
    List<Long> questionIds
) {
}
