package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GrammarCollectionResponse(
    Long collectionId,
    String kind,
    String title,
    String description,
    Long designcraftJobId,
    long itemCount,
    LocalDateTime createdAt,
    List<GrammarCollectionItemResponse> items
) {
}
