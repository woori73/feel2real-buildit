package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import java.util.List;

public record LessonCollectionSaveResponse(
    List<LessonResponse> savedItems,
    int requestedCount,
    int createdCount,
    int updatedCount,
    int replayedCount
) {
}
