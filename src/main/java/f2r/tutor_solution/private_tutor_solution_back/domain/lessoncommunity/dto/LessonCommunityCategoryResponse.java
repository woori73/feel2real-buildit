package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityCategory;

public record LessonCommunityCategoryResponse(Long categoryId, String name, int sortOrder) {
  public static LessonCommunityCategoryResponse from(LessonCommunityCategory c) {
    return new LessonCommunityCategoryResponse(c.getCategoryId(), c.getName(), c.getSortOrder());
  }
}
