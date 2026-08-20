package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.dto;

import java.util.List;

public record LessonCommunityPostListResponse(
    LessonCommunityPostSummaryResponse pinnedNotice,
    List<LessonCommunityPostSummaryResponse> posts
) {}
