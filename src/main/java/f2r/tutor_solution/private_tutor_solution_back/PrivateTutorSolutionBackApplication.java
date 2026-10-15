package f2r.tutor_solution.private_tutor_solution_back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * UserDetailsServiceAutoConfiguration을 제외한다 — 우리는 AuthController + UserService.login()으로
 * 직접 인증하므로 Spring의 default UserDetailsService("user" 계정 + random password) 자동 등록은 불필요하다.
 * 제외하지 않으면 시작 로그에 random password가 출력되고 사용되지 않는 in-memory user가 빈으로 남는다.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class PrivateTutorSolutionBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(PrivateTutorSolutionBackApplication.class, args);
	}

}
