package f2r.tutor_solution.private_tutor_solution_back.domain.user.social;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Deploy #331 회귀 테스트: NaverOAuthClient는 production 생성자(props)와 테스트 생성자
 * (props, http)가 공존한다. @Autowired 지정이 빠져 Spring이 생성자를 결정하지 못하면
 * no-arg를 찾아 기동 실패했는데, WebMvc/@MockitoBean 슬라이스는 bean을 mock으로 대체해
 * 이 실패를 잡지 못했다. 여기서는 실제 bean 인스턴스화까지 검증한다.
 */
class NaverOAuthClientContextTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(SocialClientTestConfig.class);

  @Configuration
  @EnableConfigurationProperties(NaverOAuthProperties.class)
  @Import(NaverOAuthClient.class)
  static class SocialClientTestConfig {}

  @Test
  @DisplayName("given: NAVER_* 환경변수 값 하나 없는 최소 컨텍스트, when: 기동, then: NaverOAuthClient 실bean 생성 성공")
  void contextStartsWithoutNaverPropertiesAndCreatesRealClientBean() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(NaverOAuthClient.class);
          assertThat(context.getBean(NaverOAuthClient.class))
              .isInstanceOf(NaverOAuthClient.class);
        });
  }

  @Test
  @DisplayName("given: 빈 properties, when: bean 생성, then: 외부 호출 없이 빈으로만 존재 (props 미설정 상태 부팅 = 운영 env unset等价)")
  void beanExistsWithoutAnyExternalCallOrProperty() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          NaverOAuthProperties props = context.getBean(NaverOAuthProperties.class);
          assertThat(props.configured()).isFalse();
        });
  }
}
