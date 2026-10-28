package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable, at-least-once ID-only fallback. Muse/Dots are not assumed to have notification APIs. */
@Component
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamIntakeNotifications {
  private final ExamIntakeRepository repo;
  private final TransactionTemplate tx;
  private final ObjectProvider<JavaMailSender> mail;
  private final boolean enabled;
  private final String recipient;

  public ExamIntakeNotifications(
      ExamIntakeRepository repo,
      TransactionTemplate tx,
      ObjectProvider<JavaMailSender> mail,
      @Value("${app.exam-intake.email-notifications-enabled:false}") boolean enabled,
      @Value("${app.exam-intake.notification-recipient:}") String recipient) {
    this.repo = repo;
    this.tx = tx;
    this.mail = mail;
    this.enabled = enabled;
    this.recipient = recipient;
  }

  @Scheduled(fixedDelayString = "${app.exam-intake.notification-poll-ms:60000}")
  public void tick() {
    // 48h without processing is a review alert, not a promised delivery deadline.
    var now = Instant.now();
    for (String id :
        repo.jdbc()
            .queryForList(
                "SELECT id FROM exam_intake_request WHERE state NOT IN ('COMPLETE','CANCELLED') AND"
                    + " updated_at<? ORDER BY updated_at LIMIT 50",
                String.class,
                Timestamp.from(now.minusSeconds(172800)))) {
      try {
        repo.event(
            repo.get(id, false),
            "application.overdue",
            id + ":overdue:" + LocalDate.ofInstant(now, ZoneOffset.UTC));
      } catch (DuplicateKeyException ignored) {
      }
    }
    if (!enabled || recipient.isBlank() || mail.getIfAvailable() == null) return;
    Map<String, Object> event =
        tx.execute(
            status -> {
              var rows =
                  repo.jdbc()
                      .queryForList(
                          "SELECT id,request_id,event_type,attempts FROM exam_intake_event WHERE"
                              + " state IN ('PENDING','SENDING') AND next_attempt_at<=? ORDER BY"
                              + " created_at LIMIT 1 FOR UPDATE",
                          Timestamp.from(now));
              if (rows.isEmpty()) return null;
              var row = rows.getFirst();
              int attempts = ((Number) row.get("attempts")).intValue();
              if (attempts >= 8) {
                repo.jdbc()
                    .update(
                        "UPDATE exam_intake_event SET state='FAILED',last_error='RETRY_EXHAUSTED'"
                            + " WHERE id=?",
                        row.get("id"));
                return null;
              }
              repo.jdbc()
                  .update(
                      "UPDATE exam_intake_event SET"
                          + " state='SENDING',attempts=attempts+1,next_attempt_at=? WHERE id=?",
                      Timestamp.from(now.plusSeconds(120)),
                      row.get("id"));
              return row;
            });
    if (event == null) return;
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setTo(recipient);
      message.setSubject("Feel2Real 접수 알림 [" + event.get("id") + "]");
      message.setText(
          "이벤트: "
              + event.get("event_type")
              + "\n접수 ID: "
              + event.get("request_id")
              + "\n"
              + "관리자 확인: https://feel2real.co.kr/admin/hub/exam-requests\n"
              + "이벤트 ID를 기준으로 중복 알림을 확인하세요.");
      mail.getObject().send(message);
      repo.jdbc()
          .update(
              "UPDATE exam_intake_event SET state='SENT',last_error=NULL WHERE id=? AND"
                  + " state='SENDING'",
              event.get("id"));
    } catch (Exception e) {
      int attempts = ((Number) event.get("attempts")).intValue() + 1;
      repo.jdbc()
          .update(
              "UPDATE exam_intake_event SET state=?,last_error='SMTP_FAILURE',next_attempt_at=?"
                  + " WHERE id=? AND state='SENDING'",
              attempts >= 8 ? "FAILED" : "PENDING",
              Timestamp.from(
                  Instant.now().plusSeconds(Math.min(3600, 60L << Math.min(attempts, 6)))),
              event.get("id"));
    }
  }
}
