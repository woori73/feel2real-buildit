package f2r.tutor_solution.private_tutor_solution_back.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnProperty(name = "app.statistics.alb.enabled", havingValue = "true")
public class AlbAccessStatisticsConfig {

  /** ALB S3 조회·gzip 파싱이 영상 upload recovery/optimization scheduler를 점유하지 않게 격리한다. */
  @Bean(name = "albAccessStatisticsScheduler")
  public ThreadPoolTaskScheduler albAccessStatisticsScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("alb-access-statistics-");
    scheduler.initialize();
    return scheduler;
  }
}
