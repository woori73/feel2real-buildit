package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.AnalyticsVisitorIdentity;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.SiteEventRateLimiter;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.SiteEventRecordingService;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.SiteEventRecordingService.Event;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 이벤트 수집 API(익명 허용). 클라이언트가 보낸 URL·사용자 식별값은 쓰지 않는다 —
 * 방문자는 서버가 쿠키/JWT로 판별하고, 페이지·섹션은 서버 카탈로그가 정한다. 응답은 항상 본문 없는 204(수집 결과를 알려주지 않는다).
 */
@RestController
@RequestMapping("/api/analytics/events")
public class SiteEventController {

  static final int MAX_CONTENT_LENGTH = 4096;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record EventBatchRequest(String page, String viewId, List<Event> events) {}

  private final SiteEventRecordingService service;
  private final SiteEventRateLimiter rateLimiter;

  public SiteEventController(SiteEventRecordingService service, SiteEventRateLimiter rateLimiter) {
    this.service = service;
    this.rateLimiter = rateLimiter;
  }

  @PostMapping
  public ResponseEntity<?> collect(@RequestBody EventBatchRequest body, HttpServletRequest request) {
    if (request.getContentLengthLong() > MAX_CONTENT_LENGTH) {
      return ResponseEntity.status(413).build();
    }
    if (body.events() != null && body.events().size() > SiteEventRecordingService.MAX_EVENTS_PER_REQUEST) {
      return ResponseEntity.badRequest().body(Map.of("error", "too_many_events"));
    }
    if (!rateLimiter.tryAcquire(rateLimiter.clientIp(request))) {
      return ResponseEntity.status(429).build();
    }

    boolean bot = AnalyticsVisitorIdentity.isBot(request.getHeader("User-Agent"));
    boolean internal = false;
    String visitorKey = null;
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof JwtTokenProvider.AuthenticatedPrincipal p) {
      visitorKey = "U:" + p.userId();
      internal = "ADMIN".equals(p.role()) || "ROLE_ADMIN".equals(p.role());
    } else if (bot) {
      visitorKey = "B:BOT";
    } else {
      String visitorId = AnalyticsVisitorIdentity.validVisitorId(request);
      if (visitorId != null) visitorKey = "V:" + visitorId;
    }
    // 방문 쿠키가 없으면(= 페이지를 정상적으로 열지 않은 요청) 저장하지 않는다.
    if (visitorKey != null) {
      service.record(body.page(), body.viewId(), body.events(), visitorKey, bot, internal);
    }
    return ResponseEntity.noContent().build();
  }
}
