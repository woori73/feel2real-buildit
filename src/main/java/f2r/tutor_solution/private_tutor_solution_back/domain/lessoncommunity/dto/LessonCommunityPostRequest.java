package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityPostType;

public record LessonCommunityPostRequest(
    LessonCommunityPostType postType,
    String title,
    String content,
    Long categoryId,
    boolean qnaPublic
) {}
