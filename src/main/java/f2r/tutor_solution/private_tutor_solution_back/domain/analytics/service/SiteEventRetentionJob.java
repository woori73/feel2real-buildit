package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 새벽 보관 기간(app.analytics.events.retention-days, 기본 395일)이 지난 이벤트를 삭제한다. */
@Slf4j
@Component
public class SiteEventRetentionJob {

  private final SiteEventRecordingService service;

  public SiteEventRetentionJob(SiteEventRecordingService service) {
    this.service = service;
  }

  @Scheduled(cron = "0 20 4 * * *", zone = "Asia/Seoul")
  public void purge() {
    try {
      int n = service.purgeExpired();
      if (n > 0) log.info("site_events 보관 기간({}일) 초과 {}건 삭제", service.retentionDays(), n);
    } catch (Exception e) {
      log.warn("site_events 정리 실패: {}", e.getMessage());
    }
  }
}
