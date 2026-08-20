package f2r.tutor_solution.private_tutor_solution_back.domain.community.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공개 커뮤니티 좋아요/추천 설정값. 운영자가 배포 환경 설정에서 덮어쓸 수 있다.
 * {@code @ConfigurationPropertiesScan}(App 클래스)으로 자동 등록되므로 별도 @EnableConfigurationProperties 불필요.
 */
@ConfigurationProperties(prefix = "app.community")
public record CommunityProperties(
    int likeAllowedDays,
    int recommendationWindowDays
) {
  public CommunityProperties {
    if (likeAllowedDays <= 0) likeAllowedDays = 30;
    if (recommendationWindowDays <= 0) recommendationWindowDays = 7;
  }
}
