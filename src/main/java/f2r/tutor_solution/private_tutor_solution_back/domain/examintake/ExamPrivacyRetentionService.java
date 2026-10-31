package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Manual, reviewed erasure. File deletion failures remain retryable; financial evidence is
 * retained.
 */
@Service
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamPrivacyRetentionService {
  private final ExamIntakeRepository repo;
  private final ExamPrivateFiles files;
  private final TransactionTemplate tx;
  private final String reviewed;

  public ExamPrivacyRetentionService(
      ExamIntakeRepository repo,
      ExamPrivateFiles files,
      TransactionTemplate tx,
      @Value("${app.exam-intake.retention-reviewed-policy-version:}") String reviewed) {
    this.repo = repo;
    this.files = files;
    this.tx = tx;
    this.reviewed = reviewed;
  }

  public List<Map<String, Object>> plan(AuthenticatedPrincipal p) {
    admin(p);
    return repo.jdbc()
        .queryForList(
            "SELECT r.id,r.kind,r.state,r.updated_at FROM exam_intake_request r WHERE r.state IN"
                + " ('COMPLETE','CANCELLED','RECEIVED','AWAITING_DEPOSIT') AND (r.updated_at<? OR"
                + " EXISTS(SELECT 1 FROM exam_intake_action a WHERE a.request_id=r.id AND"
                + " a.kind='PRIVACY_ERASURE' AND a.state='PROCESSING')) AND NOT EXISTS(SELECT 1"
                + " FROM exam_intake_action a WHERE a.request_id=r.id AND a.kind='PRIVACY_ERASURE'"
                + " AND a.state='RESOLVED') ORDER BY r.updated_at LIMIT 100",
            Timestamp.from(Instant.now().minusSeconds(30L * 86400)));
  }

  public void erase(
      AuthenticatedPrincipal p,
      String id,
      long version,
      boolean purposeComplete,
      boolean noLegalHold) {
    admin(p);
    if (!PRIVACY.equals(reviewed)) throw error(409, "검토된 보존·삭제 정책 등록 전에는 삭제할 수 없습니다.");
    if (!purposeComplete || !noLegalHold) throw error(400, "목적 달성과 법적 보존·분쟁 유보 여부를 확인해 주세요.");
    String marker =
        tx.execute(
            status -> {
              Request r = repo.get(id, true);
              if (r.version() != version) throw error(409, "접수를 다시 불러와 주세요.");
              var prior =
                  repo.actions(id).stream()
                      .filter(a -> a.kind().equals("PRIVACY_ERASURE"))
                      .findFirst();
              if (prior.isPresent() && prior.get().state().equals("RESOLVED")) return null;
              boolean inactiveUnpaid =
                  Set.of(State.RECEIVED, State.AWAITING_DEPOSIT).contains(r.state())
                      && r.updatedAt().isBefore(Instant.now().minusSeconds(30L * 86400))
                      && repo.actions(id).stream()
                          .noneMatch(a -> a.kind().equals("PAYMENT_CONFIRMATION"));
              if (!Set.of(State.COMPLETE, State.CANCELLED).contains(r.state()) && !inactiveUnpaid)
                throw error(409, "종료·취소 또는 30일이 지난 미계약·미입금 건의 목적 달성을 확인해 주세요.");
              if (repo.actions(id).stream()
                  .anyMatch(
                      a ->
                          Set.of(
                                      "REFUND",
                                      "CANCEL",
                                      "REVISION",
                                      "PRIVACY_REQUEST",
                                      "UPDATE_DETAILS",
                                      "DELETE_DOCUMENT")
                                  .contains(a.kind())
                              && !Set.of("RESOLVED", "REMITTED", "REJECTED").contains(a.state())))
                throw error(409, "진행 중인 요청과 분쟁을 먼저 검토해 주세요.");
              if (inactiveUnpaid) {
                repo.action(r, p.userId(), "STATE_CHANGED", "30일 경과 미계약·미입금 종결: CANCELLED");
                repo.state(r, State.CANCELLED);
              }
              String action =
                  prior
                      .map(Action::id)
                      .orElseGet(() -> repo.action(r, p.userId(), "PRIVACY_ERASURE", PRIVACY));
              repo.jdbc()
                  .update("UPDATE exam_intake_action SET state='PROCESSING' WHERE id=?", action);
              repo.jdbc()
                  .update("UPDATE exam_intake_document SET hidden=TRUE WHERE request_id=?", id);
              return action;
            });
    if (marker == null) return;
    try {
      for (Document d : repo.documents(id)) files.discard(d.storageName());
    } catch (IOException | RuntimeException e) {
      throw error(503, "파일 삭제가 완료되지 않았습니다. 비공개 상태를 유지하며 다시 시도해 주세요.");
    }
    tx.executeWithoutResult(
        status -> {
          Request r = repo.get(id, true);
          // During the retry window no other request mutation is allowed by
          // ExamIntakeService.locked.
          var redacted = new LinkedHashMap<String, String>();
          for (String key : r.details().keySet())
            redacted.put(key, AGREEMENTS.contains(key) ? r.details().get(key) : "");
          redacted.put("name", "개인정보 정리 완료");
          repo.jdbc()
              .update(
                  "UPDATE exam_intake_request SET payload=? WHERE id=?", repo.json(redacted), id);
          for (Document d : repo.documents(id))
            repo.jdbc()
                .update(
                    "UPDATE exam_intake_document SET original_name='삭제"
                        + " 완료',size_bytes=0,sha256=?,scan_state='DELETED',hidden=TRUE WHERE id=?",
                    hash(d.id()),
                    d.id());
          // Minimal payment/contract/delivery evidence is not treated as expendable learning
          // material.
          var actions = repo.actions(id);
          var financialIds = new HashSet<String>();
          actions.stream()
              .filter(a -> Set.of("REFUND", "CANCEL").contains(a.kind()))
              .forEach(a -> financialIds.add(a.id()));
          for (Action action : actions) {
            boolean legalEvidence =
                Set.of(
                        "PAYMENT_CONFIRMATION",
                        "PRIVACY_ERASURE",
                        "STATE_CHANGED",
                        "DELIVERED",
                        "REFUND",
                        "CANCEL")
                    .contains(action.kind());
            if (action.kind().equals("ACTION_REVIEW")) {
              Map<String, Object> review =
                  repo.parse(
                      action.body(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
              legalEvidence = financialIds.contains(Objects.toString(review.get("actionId"), ""));
            }
            if (!legalEvidence)
              repo.jdbc()
                  .update(
                      "UPDATE exam_intake_action SET body='개인정보 정리 완료' WHERE id=?", action.id());
          }
          repo.jdbc()
              .update(
                  "UPDATE exam_intake_delivery SET note='전달 기록 보존 · 상세 개인정보 정리' WHERE request_id=?",
                  id);
          repo.jdbc()
              .update(
                  "UPDATE exam_intake_guardian_consent SET guardian_name='개인정보 정리"
                      + " 완료',phone='',state='REVOKED',revoked_at=? WHERE request_id=?",
                  Timestamp.from(Instant.now()),
                  id);
          repo.jdbc()
              .update(
                  "UPDATE exam_intake_action SET state='RESOLVED',updated_at=? WHERE id=?",
                  Timestamp.from(Instant.now()),
                  marker);
          repo.touch(r);
        });
  }

  public int eraseUnusedGuardians(AuthenticatedPrincipal p) {
    admin(p);
    if (!PRIVACY.equals(reviewed)) throw error(409, "검토된 보존·삭제 정책이 필요합니다.");
    return repo.jdbc()
        .update(
            "UPDATE exam_intake_guardian_consent SET guardian_name='개인정보 정리"
                + " 완료',phone='',state='REVOKED' WHERE request_id IS NULL AND (expires_at<? OR"
                + " state IN ('REVOKED','REJECTED')) AND phone<>''",
            Timestamp.from(Instant.now()));
  }
}
