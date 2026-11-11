package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/exam-intake/notifications")
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamIntakeNotificationController {
  private final ExamIntakeNotificationSettings settings;
  private final ExamIntakeNotificationDelivery delivery;
  private final AtomicLong lastTest = new AtomicLong();

  public ExamIntakeNotificationController(ExamIntakeNotificationSettings settings,
      ExamIntakeNotificationDelivery delivery) {
    this.settings = settings;
    this.delivery = delivery;
  }

  @GetMapping
  ResponseEntity<Map<String, Object>> read(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings.view(p));
  }

  @PutMapping
  ResponseEntity<Map<String, Object>> save(@AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestBody ExamIntakeNotificationSettings.Update input, HttpServletRequest request) {
    ExamIntakeController.mutation(request);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings.save(p, input));
  }

  @PostMapping("/test")
  ResponseEntity<Map<String, String>> test(@AuthenticationPrincipal AuthenticatedPrincipal p,
      HttpServletRequest request) {
    ExamIntakeController.mutation(request);
    admin(p);
    long now = Instant.now().getEpochSecond(), previous = lastTest.get();
    if (now - previous < 60 || !lastTest.compareAndSet(previous, now))
      throw error(429, "연결 테스트는 1분 후 다시 시도해 주세요.");
    var c = settings.current();
    var result = new LinkedHashMap<String, String>();
    result.put("email", !c.emailEnabled() ? "DISABLED" : "CONNECTION_REQUIRED");
    result.put("sms", !c.smsEnabled() ? "DISABLED" : "CONNECTION_REQUIRED");
    if (delivery.emailReady(c)) {
      try { delivery.sendEmail(c, null, "TEST", null); result.put("email", "ACCEPTED"); }
      catch (Exception e) { result.put("email", "SMTP_FAILURE"); }
    }
    if (c.smsReady()) {
      try { delivery.sendSms(c, null, UUID.randomUUID().toString()); result.put("sms", "ACCEPTED"); }
      catch (ExamIntakeNotificationDelivery.SmsFailure e) { result.put("sms", e.code); }
    }
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> rejected(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).cacheControl(CacheControl.noStore())
        .body(Map.of("message", Objects.toString(e.getReason(), "알림 설정을 처리할 수 없습니다.")));
  }
}
