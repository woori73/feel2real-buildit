package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAutomationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsAutomationRuleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 매분: 실행 시각이 지난 오늘 규칙을 한 번씩 실행하고, 예약 시각이 된 게시물을 올린다. 매일 03:17: 토큰 갱신.
 * app.sns.scheduler-enabled=false면 아무것도 하지 않는다. 같은 규칙은 하루 한 번만(last_run_on 원자적 갱신).
 */
@Component
public class SnsScheduler {

  private static final Logger log = LoggerFactory.getLogger(SnsScheduler.class);

  private final SnsProperties properties;
  private final SnsAutomationRuleRepository rules;
  private final SnsGenerationService generation;
  private final SnsPublishService publish;
  private final TransactionTemplate tx;

  public SnsScheduler(SnsProperties properties, SnsAutomationRuleRepository rules, SnsGenerationService generation,
      SnsPublishService publish, TransactionTemplate tx) {
    this.properties = properties;
    this.rules = rules;
    this.generation = generation;
    this.publish = publish;
    this.tx = tx;
  }

  @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
  public void everyMinute() {
    if (!properties.isSchedulerEnabled()) return;
    runDueRules(SnsTime.now());
    try {
      publish.publishDueScheduled();
    } catch (RuntimeException e) {
      log.warn("[sns] 예약 발행 처리 오류: {}", e.getClass().getSimpleName());
    }
  }

  @Scheduled(cron = "0 17 3 * * *", zone = "Asia/Seoul")
  public void daily() {
    if (!properties.isSchedulerEnabled()) return;
    publish.refreshExpiring();
  }

  /** 반환: 이번에 실행한 규칙 수. */
  public int runDueRules(LocalDateTime now) {
    LocalDate today = now.toLocalDate();
    int ran = 0;
    for (SnsAutomationRule rule : rules.findByEnabledTrue()) {
      if (!isDue(rule, now)) continue;
      Integer claimed = tx.execute(s -> rules.claimRun(rule.getId(), today));
      if (claimed == null || claimed != 1) continue;
      try {
        generation.startRun(rule, false);
        ran++;
      } catch (SnsGenerationService.GenerationBlocked e) {
        log.info("[sns] 규칙 {} 실행 안 함: {}", rule.getId(), e.getMessage());
      } catch (RuntimeException e) {
        log.warn("[sns] 규칙 {} 실행 오류: {}", rule.getId(), e.getClass().getSimpleName());
      }
    }
    return ran;
  }

  static boolean isDue(SnsAutomationRule rule, LocalDateTime now) {
    if (!rule.isEnabled() || rule.getSaveMode() == SnsAutomationRule.SaveMode.PAUSED) return false;
    LocalDate today = now.toLocalDate();
    if (rule.getLastRunOn() != null && !rule.getLastRunOn().isBefore(today)) return false;
    if (!rule.isScheduledOn(today)) return false;
    // 실행 시각부터 3시간 안에만 실행한다(서버가 잠깐 멈췄다 돌아와도 따라잡되, 밤늦게 켠 규칙이 바로 돌지 않게).
    java.time.LocalTime t = now.toLocalTime();
    java.time.LocalTime start = rule.getRunTime();
    java.time.LocalTime end = start.plusHours(CATCH_UP_HOURS);
    if (end.isBefore(start)) end = java.time.LocalTime.MAX;
    return !t.isBefore(start) && t.isBefore(end);
  }

  static final int CATCH_UP_HOURS = 3;
}
