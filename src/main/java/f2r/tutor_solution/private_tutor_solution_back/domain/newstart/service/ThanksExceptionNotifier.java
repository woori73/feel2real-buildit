package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.config.NewStartThanksProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksVerification;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksVerificationRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * 감사회원 셀프 인증 "자동처리 불가능한 예외"만 운영자에게 이메일로 알린다(정상 인증은 알리지 않는다).
 *
 * <p>메일에는 닉네임·사유·발생시각·관리자 확인 링크만 싣는다 — 이메일 주소·실명·네이버 ID·접두부·OAuth 식별값은 넣지 않는다.
 * SMTP 비밀값은 {@link NewStartThanksProperties}(환경변수)로만 받고, 설정이 비어 있으면 발송을 건너뛰고 상태를
 * {@code NOT_CONFIGURED}로 남긴다(예외함 적재는 이미 끝나 있다). 발송은 비동기이며 실패해도 인증 흐름에 영향을 주지 않는다.
 */
@Component
public class ThanksExceptionNotifier {

  private static final Logger log = LoggerFactory.getLogger(ThanksExceptionNotifier.class);
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final int MAX_NICK = 50;

  private final NewStartThanksProperties props;
  private final NewStartThanksVerificationRepository verificationRepository;
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "thanks-exception-mailer");
    t.setDaemon(true);
    return t;
  });

  public ThanksExceptionNotifier(NewStartThanksProperties props, NewStartThanksVerificationRepository verificationRepository) {
    this.props = props;
    this.verificationRepository = verificationRepository;
  }

  /** 커밋 이후(예외 row가 보이는 시점)에 호출한다. 호출자 스레드를 막지 않는다. */
  public void notifyAsync(Long verificationId) {
    executor.submit(() -> notifyNow(verificationId));
  }

  /** 동기 발송(테스트·내부용). 결과 상태를 verification.email_status에 기록하고 그 값을 돌려준다. */
  String notifyNow(Long verificationId) {
    NewStartThanksVerification v = verificationRepository.findById(verificationId).orElse(null);
    if (v == null) return null;
    String status;
    if (!props.mailConfigured()) {
      status = NewStartThanksVerification.EMAIL_NOT_CONFIGURED;
      log.warn("THANKS_EXCEPTION_ALERT mail not configured; exception stays in admin inbox id={} reason={}",
          v.getVerificationId(), v.getExceptionReason());
    } else {
      try {
        send(buildMessage(v));
        status = NewStartThanksVerification.EMAIL_SENT;
      } catch (RuntimeException e) {
        status = NewStartThanksVerification.EMAIL_FAILED;
        // 수신자·본문·비밀값은 로그에 남기지 않는다.
        log.warn("THANKS_EXCEPTION_ALERT mail failed id={} type={}", v.getVerificationId(), e.getClass().getSimpleName());
      }
    }
    try {
      v.markEmail(status);
      verificationRepository.save(v);
    } catch (RuntimeException e) {
      log.warn("THANKS_EXCEPTION_ALERT status update failed id={}", v.getVerificationId());
    }
    return status;
  }

  SimpleMailMessage buildMessage(NewStartThanksVerification v) {
    String reason = ThanksExceptionReason.label(v.getExceptionReason());
    LocalDateTime at = v.getCreatedAt() != null ? v.getCreatedAt() : LocalDateTime.now(KST);
    SimpleMailMessage m = new SimpleMailMessage();
    m.setTo(props.getAlertTo().strip());
    m.setFrom(props.getAlertFrom() == null || props.getAlertFrom().isBlank() ? props.getSmtpUsername() : props.getAlertFrom());
    m.setSubject("[NewStart] 감사회원 인증 예외 — " + reason);
    m.setText("감사회원 셀프 인증이 자동으로 완료되지 않아 확인이 필요합니다.\n\n"
        + "- 카페 닉네임(입력값): " + safeNickname(v.getNicknameInput()) + "\n"
        + "- 사유: " + reason + "\n"
        + "- 발생시각: " + FMT.format(at) + " (KST)\n"
        + "- 확인: " + props.getAdminUrl() + "\n");
    return m;
  }

  /** 회원이 입력한 값이 메일 헤더/본문 구조를 깨지 않도록 제어문자를 제거하고 길이를 제한한다. */
  static String safeNickname(String nickname) {
    if (nickname == null) return "";
    String cleaned = nickname.replaceAll("[\\p{Cntrl}]", " ").strip();
    return cleaned.length() > MAX_NICK ? cleaned.substring(0, MAX_NICK) : cleaned;
  }

  private void send(SimpleMailMessage message) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(props.getSmtpHost());
    sender.setPort(props.getSmtpPort());
    sender.setUsername(props.getSmtpUsername());
    sender.setPassword(props.getSmtpPassword());
    sender.setDefaultEncoding("UTF-8");
    Properties p = sender.getJavaMailProperties();
    p.put("mail.smtp.auth", "true");
    p.put("mail.smtp.starttls.enable", "true");
    p.put("mail.smtp.connectiontimeout", "5000");
    p.put("mail.smtp.timeout", "8000");
    p.put("mail.smtp.writetimeout", "8000");
    sender.send(message);
  }
}
