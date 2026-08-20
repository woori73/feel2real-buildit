package f2r.tutor_solution.private_tutor_solution_back.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;

@Configuration
public class AcademyVideoOptimizationConfig {

  @Bean(name = "academyVideoConversionExecutor")
  public Executor academyVideoConversionExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("academy-video-convert-");
    executor.initialize();
    return executor;
  }

  @Bean(name = "academyVideoCleanupExecutor")
  public Executor academyVideoCleanupExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("academy-video-cleanup-");
    executor.initialize();
    return executor;
  }

  @Bean(name = "academyVideoCleanupScheduler")
  public ThreadPoolTaskScheduler academyVideoCleanupScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("academy-video-cleanup-scheduler-");
    scheduler.initialize();
    return scheduler;
  }

  /**
   * Optimization scheduling is deliberately separate from conversion and cleanup so blocked
   * external deletion cannot starve G003 heartbeat or recovery polling.
   */
  @Bean(name = "taskScheduler")
  public ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(3);
    scheduler.setThreadNamePrefix("academy-video-scheduler-");
    scheduler.initialize();
    return scheduler;
  }
}
