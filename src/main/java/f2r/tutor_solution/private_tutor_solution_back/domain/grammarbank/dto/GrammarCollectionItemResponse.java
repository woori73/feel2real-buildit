package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import java.util.List;

public record GrammarCollectionItemResponse(
    Long questionId,
    Integer orderNum,
    String stemExcerpt,
    List<String> tags
) {
}
