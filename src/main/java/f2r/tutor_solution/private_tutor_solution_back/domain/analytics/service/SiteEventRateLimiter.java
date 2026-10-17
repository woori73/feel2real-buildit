package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller.AdmissionsRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 이벤트 수집 API 속도 제한(IP 기준 슬라이딩 윈도우 + 전역 일일 상한). 다른 제한기와 별도 인스턴스라 서로 한도를 소모하지 않는다. */
@Component
public class SiteEventRateLimiter {

  private final AdmissionsRateLimiter delegate;

  public SiteEventRateLimiter(
      @Value("${app.analytics.events.rate-limit.per-minute:60}") int perMinute,
      @Value("${app.analytics.events.rate-limit.per-hour:600}") int perHour,
      @Value("${app.admissions.rate-limit.trusted-proxies:}") String trustedProxies,
      @Value("${app.analytics.events.rate-limit.daily-limit:200000}") int dailyLimit) {
    this.delegate = new AdmissionsRateLimiter(perMinute, perHour, 10_000, trustedProxies, dailyLimit);
  }

  public String clientIp(HttpServletRequest request) {
    return delegate.resolveClientIp(request);
  }

  public boolean tryAcquire(String ip) {
    return delegate.tryAcquire(ip);
  }
}
