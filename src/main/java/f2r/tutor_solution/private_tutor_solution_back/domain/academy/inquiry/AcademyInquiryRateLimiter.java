package f2r.tutor_solution.private_tutor_solution_back.domain.academy.inquiry;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller.AdmissionsRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 상담신청 공개 API 속도 제한. 입시상담 제한기({@link AdmissionsRateLimiter})의 IP 판별(신뢰 프록시 XFF 규칙)과
 * 슬라이딩 윈도우를 재사용하되, 한도와 기록은 별도 인스턴스로 분리한다(서로의 한도를 소모하지 않는다).
 * 신뢰 프록시 설정은 같은 속성({@code app.admissions.rate-limit.trusted-proxies})을 쓴다.
 */
@Component
public class AcademyInquiryRateLimiter {

  private final AdmissionsRateLimiter delegate;

  public AcademyInquiryRateLimiter(
      @Value("${app.academy-inquiry.rate-limit.per-minute:3}") int perMinute,
      @Value("${app.academy-inquiry.rate-limit.per-hour:10}") int perHour,
      @Value("${app.admissions.rate-limit.trusted-proxies:}") String trustedProxies,
      @Value("${app.academy-inquiry.rate-limit.daily-limit:300}") int dailyLimit) {
    this.delegate = new AdmissionsRateLimiter(perMinute, perHour, 10_000, trustedProxies, dailyLimit);
  }

  public String clientIp(HttpServletRequest request) {
    return delegate.resolveClientIp(request);
  }

  /** 허용되면 true. 한도를 넘으면 false(차단된 요청은 기록하지 않는다). */
  public boolean tryAcquire(String ip) {
    return delegate.tryAcquire(ip);
  }
}
