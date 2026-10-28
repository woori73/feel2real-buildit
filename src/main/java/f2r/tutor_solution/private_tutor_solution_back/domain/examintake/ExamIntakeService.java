package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.HomeExamOffer;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamIntakeService {
  final ExamIntakeRepository repo;
  final ExamPrivateFiles files;
  final TransactionTemplate tx;
  private final String refundPolicyVersion;

  public ExamIntakeService(
      ExamIntakeRepository repo,
      ExamPrivateFiles files,
      TransactionTemplate tx,
      @Value("${app.exam-intake.refund-reviewed-policy-version:}") String refundPolicyVersion) {
    this.repo = repo;
    this.files = files;
    this.tx = tx;
    this.refundPolicyVersion = refundPolicyVersion;
  }

  public Request create(AuthenticatedPrincipal p, Create input) {
    long owner = user(p);
    var details = validate(input);
    String hash = hash(repo.json(new TreeMap<>(Map.of("kind", input.kind(), "details", details))));
    try {
      return tx.execute(
          status -> {
            var old = repo.duplicate(owner, input.idempotencyKey(), hash);
            if (old.isPresent()) return old.get();
            Integer count =
                repo.jdbc()
                    .queryForObject(
                        "SELECT COUNT(*) FROM exam_intake_request WHERE owner_id=? AND"
                            + " created_at>?",
                        Integer.class,
                        owner,
                        Timestamp.from(Instant.now().minusSeconds(86400)));
            if (count != null && count >= 20) throw error(429, "하루 접수 한도를 초과했습니다. 기존 접수를 확인해 주세요.");
            var offer = HomeExamOffer.CURRENT;
            var snapshot =
                Map.<String, Object>of(
                    "version",
                    offer.version(),
                    "durationDays",
                    offer.durationDays(),
                    "deliveriesPerWeek",
                    offer.deliveriesPerWeek(),
                    "totalDeliveries",
                    offer.totalDeliveries(),
                    "questionsPerDelivery",
                    offer.questionsPerDelivery(),
                    "totalQuestions",
                    offer.getTotalQuestions(),
                    "priceWon",
                    offer.priceWon());
            Request r = repo.insert(owner, input, details, snapshot, hash);
            repo.event(
                r,
                input.kind() == Kind.INQUIRY ? "inquiry.created" : "application.created",
                r.id() + ":created");
            return r;
          });
    } catch (DuplicateKeyException duplicate) {
      return repo.duplicate(owner, input.idempotencyKey(), hash)
          .orElseThrow(() -> error(409, "접수 중입니다. 같은 키로 다시 확인해 주세요."));
    }
  }

  public List<Request> list(AuthenticatedPrincipal p, int page, boolean adminView) {
    user(p);
    if (adminView) admin(p);
    if (page < 0 || page > 10000) throw error(400, "페이지 범위를 확인해 주세요.");
    return repo.list(adminView ? null : p.userId(), page * 50);
  }

  public Map<String, Object> detail(AuthenticatedPrincipal p, String id) {
    user(p);
    Request r = repo.get(id, false);
    owner(p, r);
    return Map.of(
        "request",
        r,
        "documents",
        repo.documents(id).stream()
            .map(
                d ->
                    Map.of(
                        "id",
                        d.id(),
                        "name",
                        d.originalName(),
                        "size",
                        d.sizeBytes(),
                        "scanState",
                        d.scanState(),
                        "hidden",
                        d.hidden()))
            .toList(),
        "actions",
        repo.actions(id),
        "deliveries",
        repo.jdbc()
            .queryForList(
                "SELECT ordinal_number,note,delivered_at FROM exam_intake_delivery WHERE"
                    + " request_id=? ORDER BY ordinal_number",
                id));
  }

  private Request locked(AuthenticatedPrincipal p, String id, long version) {
    Request r = repo.get(id, true);
    owner(p, r);
    if (r.version() != version) throw error(409, "다른 변경이 있습니다. 접수를 새로 불러와 주세요.");
    return r;
  }

  public Request requestAction(
      AuthenticatedPrincipal p, String id, long version, String kind, String body) {
    user(p);
    String clean = text(body, "요청 내용", 1, 2000);
    if (!Set.of("UPDATE_DETAILS", "DELETE_DOCUMENT", "REVISION", "CANCEL", "REFUND").contains(kind))
      throw error(400, "요청 유형을 확인해 주세요.");
    return tx.execute(
        status -> {
          Request r = locked(p, id, version);
          if (r.state() == State.CANCELLED) throw error(409, "취소 완료된 접수입니다.");
          if (Set.of("UPDATE_DETAILS", "DELETE_DOCUMENT").contains(kind) && !beforeProduction(r))
            throw error(409, "제작 시작 후에는 수정·취소 요청으로 접수해 주세요.");
          if (kind.equals("DELETE_DOCUMENT")
              && repo.documents(id).stream().noneMatch(d -> d.id().equals(clean) && !d.hidden()))
            throw error(404, "자료를 찾을 수 없습니다.");
          String actionId = repo.action(r, p.userId(), kind, clean);
          repo.touch(r);
          if (kind.equals("REFUND") || kind.equals("CANCEL"))
            repo.event(r, "refund.requested", actionId);
          else repo.event(r, "revision.requested", actionId);
          return repo.get(id, false);
        });
  }

  public Request paymentRequested(AuthenticatedPrincipal p, String id, long version, String payer) {
    user(p);
    String name = text(payer, "입금자명", 1, 80);
    return tx.execute(
        status -> {
          Request r = locked(p, id, version);
          if (r.kind() != Kind.APPLICATION
              || !Set.of(State.RECEIVED, State.AWAITING_DEPOSIT).contains(r.state()))
            throw error(409, "현재 입금 확인을 요청할 수 없습니다.");
          Bank bank =
              repo.bank()
                  .filter(Bank::approved)
                  .orElseThrow(() -> error(409, "승인된 입금 계좌가 없습니다. 관리자 안내를 기다려 주세요."));
          repo.action(
              r,
              p.userId(),
              "PAYMENT_CONFIRMATION",
              repo.json(
                  Map.of("payer", name, "bank", bank, "priceWon", r.offer().get("priceWon"))));
          repo.state(r, State.CONFIRMATION_REQUESTED);
          repo.event(r, "payment.confirmation_requested", r.id() + ":payment-requested");
          return repo.get(id, false);
        });
  }

  public Request adminState(AuthenticatedPrincipal p, String id, long version, State next) {
    admin(p);
    return tx.execute(
        status -> {
          Request r = locked(p, id, version);
          boolean allowed =
              switch (r.state()) {
                case RECEIVED ->
                    next == State.AWAITING_DEPOSIT && r.kind() == Kind.APPLICATION
                        || next == State.COMPLETE && r.kind() == Kind.INQUIRY;
                case CONFIRMATION_REQUESTED -> next == State.CONFIRMED;
                case CONFIRMED -> next == State.PRODUCTION;
                case DELIVERED -> next == State.COMPLETE;
                default -> false;
              };
          if (!allowed) throw error(409, "허용되지 않은 상태 전환입니다.");
          if (next == State.AWAITING_DEPOSIT)
            repo.bank()
                .filter(Bank::approved)
                .orElseThrow(() -> error(409, "먼저 계좌를 등록하고 승인해 주세요."));
          repo.state(r, next);
          repo.action(r, p.userId(), "STATE_CHANGED", r.state() + " -> " + next);
          if (next == State.CONFIRMED) repo.event(r, "payment.confirmed", r.id() + ":confirmed");
          return repo.get(id, false);
        });
  }

  public Request delivered(
      AuthenticatedPrincipal p, String id, long version, int ordinal, String note) {
    admin(p);
    String clean = text(note, "전달 채널·내역", 1, 500);
    return tx.execute(
        status -> {
          Request r = locked(p, id, version);
          int total = ((Number) r.offer().get("totalDeliveries")).intValue();
          if (r.state() != State.PRODUCTION || ordinal < 1 || ordinal > total)
            throw error(409, "제작 중인 회차만 전달 완료로 기록할 수 있습니다.");
          Integer existing =
              repo.jdbc()
                  .queryForObject(
                      "SELECT COUNT(*) FROM exam_intake_delivery WHERE request_id=?",
                      Integer.class,
                      id);
          if (ordinal != existing + 1) throw error(409, "다음 회차부터 순서대로 기록해 주세요.");
          repo.jdbc()
              .update(
                  "INSERT INTO"
                      + " exam_intake_delivery(request_id,ordinal_number,actor_id,note,delivered_at)"
                      + " VALUES(?,?,?,?,?)",
                  id,
                  ordinal,
                  p.userId(),
                  clean,
                  Timestamp.from(Instant.now()));
          repo.state(r, ordinal == total ? State.DELIVERED : State.PRODUCTION);
          repo.action(r, p.userId(), "DELIVERED", "회차 " + ordinal);
          return repo.get(id, false);
        });
  }

  public Request resolveAction(
      AuthenticatedPrincipal p,
      String id,
      long version,
      String actionId,
      String state,
      Integer amount,
      String note) {
    admin(p);
    String clean = text(note, "처리 근거", 1, 1000);
    return tx.execute(
        txStatus -> {
          Request r = locked(p, id, version);
          Action action =
              repo.actions(id).stream()
                  .filter(a -> a.id().equals(actionId))
                  .findFirst()
                  .orElseThrow(() -> error(404, "요청을 찾을 수 없습니다."));
          boolean refund = action.kind().equals("REFUND") || action.kind().equals("CANCEL");
          boolean allowed =
              action.state().equals("REQUESTED") && Set.of("REVIEWING", "REJECTED").contains(state)
                  || action.state().equals("REVIEWING")
                      && Set.of("APPROVED", "REJECTED").contains(state)
                  || action.state().equals("APPROVED")
                      && (refund ? "REMITTED" : "RESOLVED").equals(state);
          if (!allowed) throw error(409, "요청 처리 순서를 확인해 주세요.");
          if (action.kind().equals("UPDATE_DETAILS") && state.equals("RESOLVED"))
            throw error(409, "승인된 변경 요청은 신청 내용 반영 화면에서 처리해 주세요.");
          if (refund && state.equals("APPROVED")) {
            if (refundPolicyVersion.isBlank()) throw error(409, "법적 검토가 완료된 환불 정책을 먼저 등록해야 합니다.");
            if (amount == null
                || amount < 0
                || amount > ((Number) r.offer().get("priceWon")).intValue())
              throw error(400, "검토한 환불 금액을 입력해 주세요.");
          } else if (amount != null) throw error(400, "환불 승인 단계에서만 금액을 기록합니다.");
          if (action.kind().equals("DELETE_DOCUMENT") && state.equals("RESOLVED"))
            repo.jdbc()
                .update(
                    "UPDATE exam_intake_document SET hidden=TRUE WHERE request_id=? AND id=?",
                    id,
                    action.body());
          repo.jdbc()
              .update(
                  "UPDATE exam_intake_action SET"
                      + " state=?,amount_won=COALESCE(?,amount_won),updated_at=? WHERE id=?",
                  state,
                  amount,
                  Timestamp.from(Instant.now()),
                  actionId);
          repo.action(
              r,
              p.userId(),
              "ACTION_REVIEW",
              repo.json(
                  Map.of(
                      "actionId",
                      actionId,
                      "state",
                      state,
                      "reason",
                      clean,
                      "policyVersion",
                      refund ? refundPolicyVersion : "")));
          repo.state(r, refund && state.equals("REMITTED") ? State.CANCELLED : r.state());
          return repo.get(id, false);
        });
  }

  public void upload(AuthenticatedPrincipal p, String id, MultipartFile upload) throws IOException {
    user(p);
    Request before = repo.get(id, false);
    owner(p, before);
    if (!beforeProduction(before)) throw error(409, "제작 시작 후에는 수정 요청으로 접수해 주세요.");
    var prepared = files.prepare(upload);
    boolean[] retained = {false};
    try {
      tx.executeWithoutResult(
          status -> {
            Request r = repo.get(id, true);
            owner(p, r);
            if (!beforeProduction(r)) throw error(409, "제작이 시작되어 제출할 수 없습니다.");
            var docs = repo.documents(id);
            if (docs.stream().anyMatch(d -> d.sha256().equals(prepared.hash()))) return;
            if (docs.size() >= 10
                || docs.stream().mapToLong(Document::sizeBytes).sum() + prepared.size()
                    > 100L * 1024 * 1024) throw error(400, "접수당 10개·100MiB 제한을 초과했습니다.");
            String fileId = UUID.randomUUID().toString();
            repo.jdbc()
                .update(
                    "INSERT INTO"
                        + " exam_intake_document(id,request_id,original_name,content_type,size_bytes,sha256,storage_name,scan_state,hidden,created_at)"
                        + " VALUES(?,?,?,?,?,?,?,?,FALSE,?)",
                    fileId,
                    id,
                    prepared.name(),
                    prepared.type(),
                    prepared.size(),
                    prepared.hash(),
                    prepared.storage(),
                    prepared.scanState(),
                    Timestamp.from(Instant.now()));
            repo.touch(r);
            repo.event(r, "document.uploaded", fileId);
            retained[0] = true;
          });
    } catch (RuntimeException e) {
      retained[0] = false;
      throw e;
    } finally {
      if (!retained[0]) files.discard(prepared.storage());
    }
  }

  public Document download(AuthenticatedPrincipal p, String id, String document) {
    admin(p);
    Request r = repo.get(id, false);
    Document d =
        repo.documents(id).stream()
            .filter(x -> x.id().equals(document))
            .findFirst()
            .orElseThrow(() -> error(404, "자료를 찾을 수 없습니다."));
    if (d.hidden() || !d.scanState().equals("CLEAN"))
      throw error(409, "검사가 완료된 공개 제한 자료만 열람할 수 있습니다.");
    return d;
  }

  public Optional<Bank> publicBank(AuthenticatedPrincipal p) {
    user(p);
    return repo.bank().filter(Bank::approved);
  }

  public Optional<Bank> adminBank(AuthenticatedPrincipal p) {
    admin(p);
    return repo.bank();
  }

  public Bank saveBank(AuthenticatedPrincipal p, Bank bank) {
    admin(p);
    String name = text(bank.bankName(), "은행명", 1, 80),
        account = text(bank.accountNumber(), "계좌번호", 1, 80),
        holder = text(bank.accountHolder(), "예금주", 1, 80);
    return tx.execute(
        status -> {
          var rows =
              repo.jdbc()
                  .queryForList("SELECT version FROM exam_intake_bank WHERE id=1 FOR UPDATE");
          if (rows.isEmpty()) {
            if (bank.version() != 0) throw error(409, "계좌 설정을 다시 불러와 주세요.");
            repo.jdbc()
                .update(
                    "INSERT INTO"
                        + " exam_intake_bank(id,bank_name,account_number,account_holder,approved,version,changed_by,updated_at)"
                        + " VALUES(1,?,?,?,FALSE,1,?,?)",
                    name,
                    account,
                    holder,
                    p.userId(),
                    Timestamp.from(Instant.now()));
          } else {
            if (((Number) rows.getFirst().get("version")).longValue() != bank.version())
              throw error(409, "계좌 설정이 변경되었습니다.");
            repo.jdbc()
                .update(
                    "UPDATE exam_intake_bank SET"
                        + " bank_name=?,account_number=?,account_holder=?,approved=FALSE,approved_by=NULL,version=version+1,changed_by=?,updated_at=?"
                        + " WHERE id=1",
                    name,
                    account,
                    holder,
                    p.userId(),
                    Timestamp.from(Instant.now()));
          }
          Bank saved = repo.bank().orElseThrow();
          bankAudit(p, "SAVE", saved);
          return saved;
        });
  }

  public Bank approveBank(AuthenticatedPrincipal p, long version) {
    admin(p);
    return tx.execute(
        status -> {
          int changed =
              repo.jdbc()
                  .update(
                      "UPDATE exam_intake_bank SET"
                          + " approved=TRUE,approved_by=?,version=version+1,updated_at=? WHERE id=1"
                          + " AND version=? AND approved=FALSE",
                      p.userId(),
                      Timestamp.from(Instant.now()),
                      version);
          if (changed != 1) throw error(409, "계좌 설정을 다시 확인해 주세요.");
          Bank saved = repo.bank().orElseThrow();
          bankAudit(p, "APPROVE", saved);
          return saved;
        });
  }

  private void bankAudit(AuthenticatedPrincipal p, String action, Bank bank) {
    repo.jdbc()
        .update(
            "INSERT INTO exam_intake_bank_audit(id,actor_id,action,bank_snapshot,created_at)"
                + " VALUES(?,?,?,?,?)",
            UUID.randomUUID().toString(),
            p.userId(),
            action,
            repo.json(bank),
            Timestamp.from(Instant.now()));
  }

  public Request amend(
      AuthenticatedPrincipal p,
      String id,
      long version,
      String actionId,
      Map<String, String> details) {
    admin(p);
    return tx.execute(
        status -> {
          Request r = locked(p, id, version);
          if (!beforeProduction(r)) throw error(409, "제작 시작 후에는 수정 요청으로 처리해 주세요.");
          Action action =
              repo.actions(id).stream()
                  .filter(
                      a ->
                          a.id().equals(actionId)
                              && a.kind().equals("UPDATE_DETAILS")
                              && a.state().equals("APPROVED"))
                  .findFirst()
                  .orElseThrow(() -> error(409, "승인된 신청 내용 변경 요청을 선택해 주세요."));
          var clean =
              validate(new Create(r.kind(), details, true, CONSENT, "admin-amend-validation"));
          repo.action(
              r,
              p.userId(),
              "DETAILS_AMENDED",
              repo.json(Map.of("actionId", action.id(), "before", r.details(), "after", clean)));
          repo.jdbc()
              .update("UPDATE exam_intake_request SET payload=? WHERE id=?", repo.json(clean), id);
          repo.jdbc()
              .update(
                  "UPDATE exam_intake_action SET state='RESOLVED',updated_at=? WHERE id=?",
                  Timestamp.from(Instant.now()),
                  action.id());
          repo.touch(r);
          return repo.get(id, false);
        });
  }

  public String rescan(AuthenticatedPrincipal p, String id, String document) throws IOException {
    admin(p);
    repo.get(id, false);
    Document d =
        repo.documents(id).stream()
            .filter(x -> x.id().equals(document))
            .findFirst()
            .orElseThrow(() -> error(404, "자료를 찾을 수 없습니다."));
    if (d.hidden()) throw error(409, "비공개 처리된 자료입니다.");
    var path = files.path(d.storageName());
    if (!java.nio.file.Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
        || !hash(java.nio.file.Files.readAllBytes(path)).equals(d.sha256()))
      throw error(409, "자료 무결성 확인이 필요합니다.");
    String result = files.scan(path);
    tx.executeWithoutResult(
        status -> {
          repo.jdbc()
              .update(
                  "UPDATE exam_intake_document SET scan_state=?,hidden=? WHERE id=? AND"
                      + " hidden=FALSE",
                  result,
                  "INFECTED".equals(result),
                  document);
          repo.action(repo.get(id, false), p.userId(), "DOCUMENT_RESCAN", document + ":" + result);
        });
    return result;
  }
}
