package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable per-channel events; provider acceptance is distinct from confirmed receipt. */
@Component
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamIntakeNotifications {
  private final ExamIntakeRepository repo;
  private final TransactionTemplate tx;
  private final ExamIntakeNotificationSettings settings;
  private final ExamIntakeNotificationDelivery delivery;

  public ExamIntakeNotifications(ExamIntakeRepository repo, TransactionTemplate tx,
      ExamIntakeNotificationSettings settings, ExamIntakeNotificationDelivery delivery) {
    this.repo = repo;
    this.tx = tx;
    this.settings = settings;
    this.delivery = delivery;
  }

  @Scheduled(fixedDelayString = "${app.exam-intake.notification-poll-ms:60000}")
  public void tick() {
    var now = Instant.now();
    // 48h without processing is a review alert, not a promised delivery deadline.
    for (String id : repo.jdbc().queryForList(
        "SELECT id FROM exam_intake_request WHERE state NOT IN ('COMPLETE','CANCELLED') AND"
            + " updated_at<? ORDER BY updated_at LIMIT 50",
        String.class, Timestamp.from(now.minusSeconds(172800)))) {
      try {
        repo.event(repo.get(id, false), "application.overdue",
            id + ":overdue:" + LocalDate.ofInstant(now, ZoneOffset.UTC));
      } catch (DuplicateKeyException ignored) { }
    }
    ExamIntakeNotificationSettings.Config c;
    try { c = settings.current(); }
    catch (org.springframework.web.server.ResponseStatusException unavailable) { return; }
    if (delivery.emailReady(c)) sendEmail(c, now);
    if (c.smsReady()) sendSms(c, now);
  }

  private void sendEmail(ExamIntakeNotificationSettings.Config c, Instant now) {
    Map<String, Object> event = claim(false, now,
        c.version() == 0 ? Instant.EPOCH : Instant.parse(c.emailSince()));
    if (event == null) return;
    try {
      delivery.sendEmail(c, event.get("request_id").toString(), event.get("event_type").toString(), event.get("id").toString());
      sent(event);
    } catch (Exception e) {
      int attempts = ((Number) event.get("attempts")).intValue() + 1;
      repo.jdbc().update(
          "UPDATE exam_intake_event SET state=?,last_error='SMTP_FAILURE',next_attempt_at=?"
              + " WHERE id=? AND state='SENDING'",
          attempts >= 8 ? "FAILED" : "PENDING",
          Timestamp.from(Instant.now().plusSeconds(Math.min(3600, 60L << Math.min(attempts, 6)))),
          event.get("id"));
    }
  }

  private void sendSms(ExamIntakeNotificationSettings.Config c, Instant now) {
    // Materialize a separate, unique SMS event for each new application/inquiry.
    // Connecting a provider does not replay historical requests.
    var since = Timestamp.from(Instant.parse(c.smsSince()));
    for (var row : repo.jdbc().queryForList(
        "SELECT e.id,e.request_id,e.event_type FROM exam_intake_event e WHERE"
            + " e.event_type IN ('application.created','inquiry.created') AND e.created_at>=?"
            + " AND NOT EXISTS (SELECT 1 FROM exam_intake_event s WHERE s.event_key=CONCAT('sms:',e.id))"
            + " ORDER BY e.created_at LIMIT 50", since)) {
      try {
        repo.event(repo.get(row.get("request_id").toString(), false),
            "sms." + row.get("event_type"), "sms:" + row.get("id"));
      } catch (DuplicateKeyException ignored) { }
    }
    // If a process stopped after sending, the carrier may already have accepted it.
    // Require operator review rather than auto-sending another paid message.
    repo.jdbc().update(
        "UPDATE exam_intake_event SET state='FAILED',last_error='SMS_RESULT_UNKNOWN' WHERE"
            + " event_type LIKE 'sms.%' AND state='SENDING' AND next_attempt_at<=?",
        Timestamp.from(now));
    Map<String, Object> event = claim(true, Instant.now(), Instant.parse(c.smsSince()));
    if (event == null) return;
    try {
      delivery.sendSms(c, event.get("request_id").toString(), event.get("id").toString());
      sent(event);
    } catch (ExamIntakeNotificationDelivery.SmsFailure e) {
      repo.jdbc().update(
          "UPDATE exam_intake_event SET state='FAILED',last_error=? WHERE id=? AND state='SENDING'",
          e.code, event.get("id"));
    }
  }

  private Map<String, Object> claim(boolean sms, Instant now, Instant since) {
    return tx.execute(status -> {
      var rows = repo.jdbc().queryForList(
          "SELECT id,request_id,event_type,attempts FROM exam_intake_event WHERE "
              + (sms ? "event_type LIKE 'sms.%' AND state='PENDING'"
                  : "event_type NOT LIKE 'sms.%' AND state IN ('PENDING','SENDING')")
              + " AND next_attempt_at<=? AND created_at>=? ORDER BY created_at LIMIT 1 FOR UPDATE",
          Timestamp.from(now), Timestamp.from(since));
      if (rows.isEmpty()) return null;
      var row = rows.getFirst();
      if (((Number) row.get("attempts")).intValue() >= 8) {
        repo.jdbc().update(
            "UPDATE exam_intake_event SET state='FAILED',last_error='RETRY_EXHAUSTED' WHERE id=?",
            row.get("id"));
        return null;
      }
      repo.jdbc().update(
          "UPDATE exam_intake_event SET state='SENDING',attempts=attempts+1,next_attempt_at=? WHERE id=?",
          Timestamp.from(now.plusSeconds(120)), row.get("id"));
      return row;
    });
  }

  private void sent(Map<String, Object> event) {
    repo.jdbc().update(
        "UPDATE exam_intake_event SET state='SENT',last_error=NULL WHERE id=? AND state='SENDING'",
        event.get("id"));
  }
}
