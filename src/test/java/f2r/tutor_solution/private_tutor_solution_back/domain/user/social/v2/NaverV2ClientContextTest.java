package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * V1 Deploy #331과 같은 클래스의 실수(생성자 2개 공존 + @Autowired 누락 → no-arg를 찾다 기동
 * 실패, WebMvc/@MockitoBean 슬라이스는 bean을 mock으로 대체해 못 잡음)를 V2에서 처음부터
 * 방지하는 회귀 테스트 — 실제 bean 인스턴스화까지 검증한다.
 */
class NaverV2ClientContextTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(SocialClientTestConfig.class);

  @Configuration
  @EnableConfigurationProperties(NaverOAuthProperties.class)
  @Import(NaverV2Client.class)
  static class SocialClientTestConfig {}

  @Test
  @DisplayName("given: NAVER_* 환경변수 값 하나 없는 최소 컨텍스트, when: 기동, then: NaverV2Client 실bean 생성 성공")
  void contextStartsWithoutNaverPropertiesAndCreatesRealClientBean() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(NaverV2Client.class);
          assertThat(context.getBean(NaverV2Client.class)).isInstanceOf(NaverV2Client.class);
        });
  }
}
