package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.academy-blog")
public record AcademyBlogProperties(
    int likeAllowedDays,
    int recommendationWindowDays
) {
  public AcademyBlogProperties {
    if (likeAllowedDays <= 0) likeAllowedDays = 30;
    if (recommendationWindowDays <= 0) recommendationWindowDays = 7;
  }
}
