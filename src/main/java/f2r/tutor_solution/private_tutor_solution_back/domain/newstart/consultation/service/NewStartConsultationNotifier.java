package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.service;

import f2r.tutor_solution.private_tutor_solution_back.config.NewStartThanksProperties;
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
 * 새 1:1 비공개 상담이 DB에 커밋된 뒤 운영자에게 이메일로 알린다.
 *
 * <p>SMTP·수신자 설정은 감사회원 예외 알림({@code ThanksExceptionNotifier})과 같은
 * {@link NewStartThanksProperties}(환경변수)를 공유한다. 비밀값은 코드에 두지 않는다.
 *
 * <p>메일에는 상담번호·닉네임·접수시각·짧은 미리보기·관리 링크만 싣는다. 상담 본문 전체·채무정보·
 * 이메일 주소·로그인 ID·인증값은 싣지 않으며, 미리보기의 긴 숫자열(계좌·주민번호 등)은 가린다.
 * 발송은 비동기이고, 미설정·실패는 로그만 남기고 호출자(상담 저장)에 영향을 주지 않는다.
 */
@Component
public class NewStartConsultationNotifier {

  private static final Logger log = LoggerFactory.getLogger(NewStartConsultationNotifier.class);
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  static final int MAX_NICKNAME = 30;
  static final int MAX_PREVIEW = 30;

  private final NewStartThanksProperties props;
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "consultation-mailer");
    t.setDaemon(true);
    return t;
  });

  public NewStartConsultationNotifier(NewStartThanksProperties props) {
    this.props = props;
  }

  /** 호출자 스레드를 막지 않는다. */
  public void notifyAsync(Long consultationId, String nickname, String content, LocalDateTime createdAt) {
    SimpleMailMessage message;
    try {
      message = buildMessage(consultationId, nickname, content, createdAt);
    } catch (RuntimeException e) {
      log.warn("CONSULTATION_ALERT build failed id={} type={}", consultationId, e.getClass().getSimpleName());
      return;
    }
    executor.submit(() -> deliver(consultationId, message));
  }

  /** 동기 발송(테스트·내부용). 발송했으면 true. */
  boolean deliver(Long consultationId, SimpleMailMessage message) {
    if (!props.mailConfigured()) {
      log.warn("CONSULTATION_ALERT mail not configured; consultation stays in admin inbox id={}", consultationId);
      return false;
    }
    try {
      send(message);
      return true;
    } catch (RuntimeException e) {
      // 수신자·본문·비밀값은 로그에 남기지 않는다.
      log.warn("CONSULTATION_ALERT mail failed id={} type={}", consultationId, e.getClass().getSimpleName());
      return false;
    }
  }

  SimpleMailMessage buildMessage(Long consultationId, String nickname, String content, LocalDateTime createdAt) {
    LocalDateTime at = createdAt != null ? createdAt : LocalDateTime.now(KST);
    SimpleMailMessage m = new SimpleMailMessage();
    m.setTo(props.getAlertTo().strip());
    m.setFrom(props.getAlertFrom() == null || props.getAlertFrom().isBlank() ? props.getSmtpUsername() : props.getAlertFrom());
    m.setSubject("[NewStart] 새로운 1:1 비공개 상담 #" + consultationId);
    m.setText("새로운 1:1 비공개 상담이 접수되었습니다.\n\n"
        + "- 상담번호: " + consultationId + "\n"
        + "- 회원: " + clean(nickname, MAX_NICKNAME, "회원") + "\n"
        + "- 접수시각: " + FMT.format(at) + " (KST)\n"
        + "- 미리보기: " + preview(content) + "\n\n"
        + "NewStart 관리자 상담함에서 확인하십시오.\n"
        + props.getConsultationAdminUrl() + "\n");
    return m;
  }

  /** 제어문자 제거 + 6자리 이상 숫자열 마스킹 + 길이 제한. */
  static String preview(String content) {
    if (content == null) return "";
    String full = clean(content.replaceAll("\\d{6,}", "***"), Integer.MAX_VALUE, "");
    return full.length() > MAX_PREVIEW ? full.substring(0, MAX_PREVIEW) + "…" : full;
  }

  static String clean(String value, int max, String fallback) {
    if (value == null) return fallback;
    String cleaned = value.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").strip();
    if (cleaned.isEmpty()) return fallback;
    return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
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
