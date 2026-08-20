package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity.AcademyBlogPostStatus;

import java.util.List;

public record AcademyBlogPostRequest(
    String title,
    String slug,
    String summary,
    String contentJson,
    String contentHtml,
    String metaTitle,
    String metaDescription,
    AcademyBlogPostStatus status,
    Boolean visible,
    List<Long> sourceIds
) {}
