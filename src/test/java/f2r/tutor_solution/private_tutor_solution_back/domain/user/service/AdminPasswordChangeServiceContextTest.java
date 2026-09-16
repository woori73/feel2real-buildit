package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * AdminPasswordChangeService가 실제 Spring IoC 컨테이너에서 bean으로 만들어지는지 검증한다.
 *
 * <p>{@link AdminPasswordChangeServiceTest}는 package-private 테스트 생성자를 직접 호출해
 * Spring의 생성자 선택 로직을 건너뛰므로, 두 생성자(운영용 4-arg / 테스트용 5-arg) 중 무엇을
 * autowire할지 애매한 상태(어느 쪽에도 {@code @Autowired}가 없는 상태)를 잡아내지 못한다
 * (Deploy #284: bootJar는 성공했지만 실제 기동 시 "No default constructor found"로 실패).
 * 이 테스트는 {@link AnnotationConfigApplicationContext}로 실제 컨테이너를 띄워 그 간극을 메운다.
 */
@DisplayName("AdminPasswordChangeService Spring bean 생성 검증")
class AdminPasswordChangeServiceContextTest {

  private AnnotationConfigApplicationContext context;

  @AfterEach
  void tearDown() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  @DisplayName("생성자가 2개(운영용/테스트용)여도 @Autowired 생성자로 bean이 정상 생성된다")
  void beanIsCreatedThroughTheAutowiredConstructor() {
    context = new AnnotationConfigApplicationContext();
    context.registerBean(PropertySourcesPlaceholderConfigurer.class,
        PropertySourcesPlaceholderConfigurer::new);
    context.registerBean(UserRepository.class, () -> mock(UserRepository.class));
    context.registerBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class));
    context.register(AdminPasswordChangeService.class);

    context.refresh();

    assertThat(context.getBean(AdminPasswordChangeService.class)).isNotNull();
  }
}
