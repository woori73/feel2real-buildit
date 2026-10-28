package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** No startup queries or file-store dependency: bank preparation never enables customer intake. */
@Service
public class ExamBankService {
  private final JdbcTemplate db;
  private final TransactionTemplate tx;
  private final ObjectMapper mapper = new ObjectMapper();

  public ExamBankService(JdbcTemplate db, TransactionTemplate tx) {
    this.db = db;
    this.tx = tx;
  }

  private String json(Bank bank) {
    try {
      return mapper.writeValueAsString(bank);
    } catch (Exception e) {
      throw new IllegalStateException("계좌 기록 처리 실패");
    }
  }

  Optional<Bank> read() {
    return db
        .query(
            "SELECT * FROM exam_intake_bank WHERE id=1",
            (rs, n) ->
                new Bank(
                    rs.getString("bank_name"),
                    rs.getString("account_number"),
                    rs.getString("account_holder"),
                    rs.getBoolean("approved"),
                    rs.getLong("version")))
        .stream()
        .findFirst();
  }

  public Optional<Bank> adminBank(AuthenticatedPrincipal p) {
    admin(p);
    return read();
  }

  public Bank saveBank(AuthenticatedPrincipal p, Bank bank) {
    admin(p);
    String name = text(bank.bankName(), "은행명", 1, 80),
        account = text(bank.accountNumber(), "계좌번호", 1, 80),
        holder = text(bank.accountHolder(), "예금주", 1, 80);
    return tx.execute(
        status -> {
          var rows = db.queryForList("SELECT version FROM exam_intake_bank WHERE id=1 FOR UPDATE");
          if (rows.isEmpty()) {
            if (bank.version() != 0) throw error(409, "계좌 설정을 다시 불러와 주세요.");
            db.update(
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
            db.update(
                "UPDATE exam_intake_bank SET"
                    + " bank_name=?,account_number=?,account_holder=?,approved=FALSE,approved_by=NULL,version=version+1,changed_by=?,updated_at=?"
                    + " WHERE id=1",
                name,
                account,
                holder,
                p.userId(),
                Timestamp.from(Instant.now()));
          }
          Bank saved = read().orElseThrow();
          bankAudit(p, "SAVE", saved);
          return saved;
        });
  }

  public Bank approveBank(AuthenticatedPrincipal p, long version) {
    admin(p);
    return tx.execute(
        status -> {
          int changed =
              db.update(
                  "UPDATE exam_intake_bank SET"
                      + " approved=TRUE,approved_by=?,version=version+1,updated_at=? WHERE id=1"
                      + " AND version=? AND approved=FALSE",
                  p.userId(),
                  Timestamp.from(Instant.now()),
                  version);
          if (changed != 1) throw error(409, "계좌 설정을 다시 확인해 주세요.");
          Bank saved = read().orElseThrow();
          bankAudit(p, "APPROVE", saved);
          return saved;
        });
  }

  private void bankAudit(AuthenticatedPrincipal p, String action, Bank bank) {
    db.update(
        "INSERT INTO exam_intake_bank_audit(id,actor_id,action,bank_snapshot,created_at)"
            + " VALUES(?,?,?,?,?)",
        UUID.randomUUID().toString(),
        p.userId(),
        action,
        json(bank),
        Timestamp.from(Instant.now()));
  }
}
