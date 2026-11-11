package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamIntakeNotificationDelivery {
  static final String ADMIN_URL = "https://feel2real.co.kr/admin/hub/exam-requests";
  private final ObjectProvider<JavaMailSender> mail;
  private final boolean legacyEnabled;
  private final String legacyRecipient;
  private final String legacyFrom;
  private final HttpClient http;
  private final ObjectMapper json = new ObjectMapper();

  @Autowired
  public ExamIntakeNotificationDelivery(
      ObjectProvider<JavaMailSender> mail,
      @Value("${app.exam-intake.email-notifications-enabled:false}") boolean enabled,
      @Value("${app.exam-intake.notification-recipient:}") String recipient,
      @Value("${app.exam-intake.notification-from:${spring.mail.username:}}") String from) {
    this(mail, enabled, recipient, from,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build());
  }

  ExamIntakeNotificationDelivery(ObjectProvider<JavaMailSender> mail, boolean enabled,
      String recipient, String from, HttpClient http) {
    this.mail = mail;
    this.legacyEnabled = enabled;
    this.legacyRecipient = recipient;
    this.legacyFrom = from;
    this.http = http;
  }

  boolean emailReady(ExamIntakeNotificationSettings.Config c) {
    return c.version() == 0
        ? legacyEnabled && !legacyRecipient.isBlank() && mail.getIfAvailable() != null
        : c.emailReady();
  }

  static String adminLink(String request) {
    return request == null || request.isBlank() ? ADMIN_URL
        : ADMIN_URL + "?request=" + URLEncoder.encode(request, StandardCharsets.UTF_8);
  }

  static String body(String request, String eventType) {
    String title = eventType.endsWith(".created") ? "새 접수가 등록되었습니다."
        : eventType.equals("TEST") ? "접수 알림 연결 테스트입니다." : "접수 내역에 확인할 사항이 있습니다.";
    return "[Feel2Real] " + title + "\n접수 관리에서 확인해 주세요.\n" + adminLink(request)
        + "\n관리자 로그인이 필요합니다.";
  }

  void sendEmail(ExamIntakeNotificationSettings.Config c, String request, String eventType, String eventId) {
    SimpleMailMessage message = new SimpleMailMessage();
    String from = c.version() == 0 ? legacyFrom : c.naverAccount();
    if (!from.isBlank()) message.setFrom(from);
    message.setTo(c.version() == 0 ? legacyRecipient : c.email());
    message.setSubject(eventType.equals("TEST") ? "Feel2Real 접수 알림 연결 테스트"
        : "Feel2Real 접수 알림 [" + eventId + "]");
    message.setText(body(request, eventType) + (eventId == null ? "" : "\n알림 번호: " + eventId));
    JavaMailSender sender = c.version() == 0 ? mail.getObject() : naverMailer(c);
    sender.send(message);
  }

  JavaMailSender naverMailer(ExamIntakeNotificationSettings.Config c) {
    var sender = new JavaMailSenderImpl();
    sender.setHost("smtp.naver.com");
    sender.setPort(587);
    sender.setUsername(c.naverAccount());
    sender.setPassword(c.naverPassword());
    sender.setDefaultEncoding("UTF-8");
    var props = sender.getJavaMailProperties();
    props.setProperty("mail.smtp.auth", "true");
    props.setProperty("mail.smtp.starttls.enable", "true");
    props.setProperty("mail.smtp.starttls.required", "true");
    props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
    for (String timeout : List.of("connectiontimeout", "timeout", "writetimeout"))
      props.setProperty("mail.smtp." + timeout, "5000");
    return sender;
  }

  static class SmsFailure extends RuntimeException {
    final String code;
    SmsFailure(String code) { super(code); this.code = code; }
  }

  void sendSms(ExamIntakeNotificationSettings.Config c, String requestId, String eventId) {
    try {
      String date = Instant.now().toString();
      String salt = UUID.randomUUID().toString().replace("-", "");
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(c.solapiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      String signature = HexFormat.of().formatHex(mac.doFinal((date + salt).getBytes(StandardCharsets.UTF_8)));
      var message = Map.of("to", c.phone(), "from", c.sender(), "type", "LMS",
          "text", body(requestId, requestId == null ? "TEST" : "application.created"),
          "subject", "Feel2Real 접수 알림", "customFields", Map.of("eventId", eventId));
      HttpRequest request = HttpRequest.newBuilder(
              URI.create("https://api.solapi.com/messages/v4/send-many/detail"))
          .timeout(Duration.ofSeconds(10))
          .header("Content-Type", "application/json")
          .header("Authorization", "HMAC-SHA256 apiKey=" + c.solapiKey() + ", date=" + date
              + ", salt=" + salt + ", signature=" + signature)
          .POST(HttpRequest.BodyPublishers.ofString(
              json.writeValueAsString(Map.of("messages", List.of(message), "showMessageList", true))))
          .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new SmsFailure(response.statusCode() >= 500 ? "SMS_RESULT_UNKNOWN" : "SMS_REJECTED");
      var result = json.readTree(response.body());
      if (!result.path("failedMessageList").isArray()
          || !result.path("failedMessageList").isEmpty()
          || result.path("groupInfo").path("count").path("registeredSuccess").asInt() != 1
          || result.path("groupInfo").path("groupId").asText().isBlank()
          || Set.of("FAILED", "DELETED").contains(result.path("groupInfo").path("status").asText()))
        throw new SmsFailure("SMS_REJECTED");
    } catch (SmsFailure e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SmsFailure("SMS_RESULT_UNKNOWN");
    } catch (Exception e) {
      // A timeout can happen after provider acceptance. Never auto-send another paid message.
      throw new SmsFailure("SMS_RESULT_UNKNOWN");
    }
  }
}
