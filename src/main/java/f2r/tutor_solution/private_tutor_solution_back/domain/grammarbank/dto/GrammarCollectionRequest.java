package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarCollection;
import java.util.List;

/** 선택함/문제집 생성·수정 요청. */
public record GrammarCollectionRequest(
    GrammarCollection.CollectionKind kind,
    String title,
    String description,
    List<Long> questionIds
) {
}
