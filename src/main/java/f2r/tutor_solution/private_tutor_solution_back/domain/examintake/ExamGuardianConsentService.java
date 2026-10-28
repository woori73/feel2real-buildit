package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamGuardianConsentService {
  private final JdbcTemplate db;
  private final TransactionTemplate tx;

  public ExamGuardianConsentService(JdbcTemplate db, TransactionTemplate tx) {
    this.db = db;
    this.tx = tx;
  }

  public record Submission(
      String guardianName,
      String phone,
      String relationship,
      boolean adultGuardian,
      boolean consent,
      String privacyVersion,
      String termsVersion) {}

  public record Verification(
      boolean identityChecked, boolean authorityChecked, boolean explainedAndAgreed) {}

  public Map<String, Object> submit(AuthenticatedPrincipal p, Submission in) {
    long owner = user(p);
    if (!in.adultGuardian()
        || !in.consent()
        || !PRIVACY.equals(in.privacyVersion())
        || !TERMS.equals(in.termsVersion()))
      throw error(400, "성인 법정대리인이 동의 내용을 확인해 주세요. 아동 정보는 입력하지 마세요.");
    String name = text(in.guardianName(), "법정대리인 이름", 1, 80),
        phone = text(in.phone(), "전화번호", 7, 25);
    if (!phone.matches("[0-9+() -]{7,25}")
        || !Set.of("PARENT", "LEGAL_GUARDIAN").contains(Objects.toString(in.relationship(), "")))
      throw error(400, "법정대리인 연락처와 관계를 확인해 주세요.");
    return tx.execute(
        status -> {
          // Serialize per authenticated account; only existing user ID is read/locked.
          db.queryForList("SELECT user_id FROM users WHERE user_id=? FOR UPDATE", owner);
          Integer count =
              db.queryForObject(
                  "SELECT COUNT(*) FROM exam_intake_guardian_consent WHERE owner_id=? AND"
                      + " created_at>?",
                  Integer.class,
                  owner,
                  Timestamp.from(Instant.now().minusSeconds(86400)));
          if (count != null && count >= 5) throw error(429, "동의 확인 요청 한도를 초과했습니다.");
          String id = UUID.randomUUID().toString();
          Instant now = Instant.now();
          db.update(
              "INSERT INTO"
                  + " exam_intake_guardian_consent(id,owner_id,guardian_name,phone,relationship,state,privacy_version,terms_version,created_at,expires_at,consent_items)"
                  + " VALUES(?,?,?,?,?,'PENDING',?,?,?,?,?)",
              id,
              owner,
              name,
              phone,
              in.relationship(),
              PRIVACY,
              TERMS,
              Timestamp.from(now),
              Timestamp.from(now.plusSeconds(30L * 86400)),
              "adult_guardian_attested=true;electronic_consent=true;guardian:name,phone,relationship;child:name,school,grade,examDate,scope,pastPapers,questionTypes,difficulty,format,optionalRequirements,optionalFiles;purpose:exam-service;privacy:"
                  + PRIVACY
                  + ";terms:"
                  + TERMS);
          return get(p, id, false);
        });
  }

  public List<Map<String, Object>> list(AuthenticatedPrincipal p, boolean adminView) {
    user(p);
    if (adminView) admin(p);
    String fields =
        "id,owner_id,guardian_name,phone,relationship,state,privacy_version,terms_version,created_at,verified_at,expires_at,revoked_at,request_id,consent_items,reviewed_at,verification_result";
    return adminView
        ? db.queryForList(
            "SELECT "
                + fields
                + " FROM exam_intake_guardian_consent ORDER BY created_at DESC LIMIT 100")
        : db.queryForList(
            "SELECT "
                + fields
                + " FROM exam_intake_guardian_consent WHERE owner_id=? ORDER BY created_at DESC"
                + " LIMIT 20",
            p.userId());
  }

  private Map<String, Object> get(AuthenticatedPrincipal p, String id, boolean lock) {
    user(p);
    var rows =
        db.queryForList(
            "SELECT * FROM exam_intake_guardian_consent WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            id);
    if (rows.isEmpty()) throw error(404, "동의 확인 요청을 찾을 수 없습니다.");
    var r = rows.getFirst();
    if (!isAdmin(p) && ((Number) r.get("owner_id")).longValue() != p.userId())
      throw error(404, "동의 확인 요청을 찾을 수 없습니다.");
    return r;
  }

  public Map<String, Object> verify(AuthenticatedPrincipal p, String id, Verification in) {
    admin(p);
    if (!in.identityChecked() || !in.authorityChecked() || !in.explainedAndAgreed())
      throw error(400, "전화로 신원·법정대리 권한·동의 내용 설명과 의사표시를 모두 확인해야 합니다.");
    return tx.execute(
        status -> {
          var r = get(p, id, true);
          if (((Number) r.get("owner_id")).longValue() == p.userId())
            throw error(403, "자신의 동의 요청은 승인할 수 없습니다.");
          if (!"PENDING".equals(r.get("state")) || !current(r))
            throw error(409, "대기 중인 유효한 동의 요청만 확인할 수 있습니다.");
          db.update(
              "UPDATE exam_intake_guardian_consent SET"
                  + " state='VERIFIED',verified_by=?,verified_at=?,verification_method='PHONE_EXPLAIN_AND_CONFIRM',verification_result='AGREED',reviewed_at=?"
                  + " WHERE id=?",
              p.userId(),
              Timestamp.from(Instant.now()),
              Timestamp.from(Instant.now()),
              id);
          return get(p, id, false);
        });
  }

  public Map<String, Object> reject(AuthenticatedPrincipal p, String id, String result) {
    admin(p);
    if (!Set.of("DECLINED", "UNVERIFIED").contains(Objects.toString(result, "")))
      throw error(400, "거절 또는 확인 불가를 선택해 주세요.");
    return tx.execute(
        status -> {
          var r = get(p, id, true);
          if (!"PENDING".equals(r.get("state"))) throw error(409, "대기 중인 요청만 처리할 수 있습니다.");
          db.update(
              "UPDATE exam_intake_guardian_consent SET"
                  + " state='REJECTED',verified_by=?,reviewed_at=?,verification_result=?,verification_method='PHONE_REVIEW'"
                  + " WHERE id=?",
              p.userId(),
              Timestamp.from(Instant.now()),
              result,
              id);
          return get(p, id, false);
        });
  }

  private boolean current(Map<String, Object> r) {
    return PRIVACY.equals(r.get("privacy_version"))
        && TERMS.equals(r.get("terms_version"))
        && ((Timestamp) r.get("expires_at")).toInstant().isAfter(Instant.now());
  }

  void checkAvailable(long owner, String id) {
    var rows =
        db.queryForList(
            "SELECT * FROM exam_intake_guardian_consent WHERE id=? AND owner_id=? FOR UPDATE",
            Objects.toString(id, ""),
            owner);
    if (rows.isEmpty()
        || !"VERIFIED".equals(rows.getFirst().get("state"))
        || !current(rows.getFirst()))
      throw error(409, "관리자가 확인한 유효한 법정대리인 동의가 필요합니다. 아동 정보 제출을 보류해 주세요.");
  }

  void consume(long owner, String id, String request) {
    int n =
        db.update(
            "UPDATE exam_intake_guardian_consent SET state='USED',request_id=? WHERE id=? AND"
                + " owner_id=? AND state='VERIFIED'",
            request,
            id,
            owner);
    if (n != 1) throw error(409, "동의가 이미 사용되었거나 철회되었습니다.");
  }

  void checkActive(Request request) {
    if (!"UNDER14".equals(request.details().get("ageBand"))) return;
    // Hold the grant through an enclosing upload/amend transaction, so revocation and
    // child-data writes cannot pass one another after the final authorization check.
    var rows =
        db.queryForList(
            "SELECT state FROM exam_intake_guardian_consent WHERE id=? AND owner_id=? AND"
                + " request_id=? FOR UPDATE",
            request.details().get("guardianConsentId"),
            request.ownerId(),
            request.id());
    if (rows.size() != 1 || !"USED".equals(rows.getFirst().get("state")))
      throw error(409, "동의가 철회되어 아동 정보를 추가 처리할 수 없습니다. 개인정보 요청으로 문의해 주세요.");
  }

  public Map<String, Object> revoke(AuthenticatedPrincipal p, String id) {
    return tx.execute(
        status -> {
          var r = get(p, id, true);
          if (!"REVOKED".equals(r.get("state")))
            db.update(
                "UPDATE exam_intake_guardian_consent SET state='REVOKED',revoked_at=?,revoked_by=?"
                    + " WHERE id=?",
                Timestamp.from(Instant.now()),
                p.userId(),
                id);
          // No erasure or financial change: the administrator reviews retention/cancellation
          // separately.
          return get(p, id, false);
        });
  }
}
