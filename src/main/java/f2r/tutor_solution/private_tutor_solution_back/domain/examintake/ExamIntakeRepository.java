package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamIntakeRepository {
  final JdbcTemplate db;
  private final ObjectMapper json = new ObjectMapper();

  public ExamIntakeRepository(JdbcTemplate db) {
    this.db = db;
  }

  public JdbcTemplate jdbc() {
    return db;
  }

  @jakarta.annotation.PostConstruct
  void requireInstalledSchema() {
    db.queryForObject("SELECT COUNT(*) FROM exam_intake_request WHERE 1=0", Integer.class);
  }

  String json(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("접수 직렬화 실패", e);
    }
  }

  <T> T parse(String value, TypeReference<T> type) {
    try {
      return json.readValue(value, type);
    } catch (Exception e) {
      throw new IllegalStateException("접수 데이터 형식 오류", e);
    }
  }

  private final RowMapper<Request> requestMapper =
      (rs, n) ->
          new Request(
              rs.getString("id"),
              rs.getLong("owner_id"),
              Kind.valueOf(rs.getString("kind")),
              State.valueOf(rs.getString("state")),
              parse(rs.getString("payload"), new TypeReference<>() {}),
              parse(rs.getString("offer_snapshot"), new TypeReference<>() {}),
              rs.getLong("version"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  Request get(String id, boolean lock) {
    return db
        .query(
            "SELECT * FROM exam_intake_request WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            requestMapper,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> error(404, "접수 내역을 찾을 수 없습니다."));
  }

  List<Request> list(Long owner, int offset) {
    return owner == null
        ? db.query(
            "SELECT * FROM exam_intake_request ORDER BY created_at DESC LIMIT 50 OFFSET ?",
            requestMapper,
            offset)
        : db.query(
            "SELECT * FROM exam_intake_request WHERE owner_id=? ORDER BY created_at DESC LIMIT 50"
                + " OFFSET ?",
            requestMapper,
            owner,
            offset);
  }

  Optional<Request> duplicate(long owner, String key, String hash) {
    var ids =
        db.queryForList(
            "SELECT id,input_hash FROM exam_intake_request WHERE owner_id=? AND intake_key=?",
            owner,
            key);
    if (ids.isEmpty()) return Optional.empty();
    if (!hash.equals(ids.getFirst().get("input_hash")))
      throw error(409, "같은 접수 키의 내용이 다릅니다. 접수 내역을 먼저 확인해 주세요.");
    return Optional.of(get((String) ids.getFirst().get("id"), false));
  }

  Request insert(
      long owner, Create in, Map<String, String> details, Map<String, Object> offer, String hash) {
    String id = UUID.randomUUID().toString();
    var now = Timestamp.from(Instant.now());
    db.update(
        "INSERT INTO"
            + " exam_intake_request(id,owner_id,kind,state,payload,offer_snapshot,consent_version,consent_at,intake_key,input_hash,version,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,0,?,?)",
        id,
        owner,
        in.kind().name(),
        State.RECEIVED.name(),
        json(details),
        json(offer),
        CONSENT,
        now,
        in.idempotencyKey(),
        hash,
        now,
        now);
    return get(id, false);
  }

  void state(Request r, State next) {
    db.update(
        "UPDATE exam_intake_request SET state=?,version=version+1,updated_at=? WHERE id=?",
        next.name(),
        Timestamp.from(Instant.now()),
        r.id());
  }

  void touch(Request r) {
    state(r, r.state());
  }

  String action(Request r, long actor, String kind, String body) {
    String id = UUID.randomUUID().toString();
    var now = Timestamp.from(Instant.now());
    db.update(
        "INSERT INTO"
            + " exam_intake_action(id,request_id,actor_id,kind,body,state,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        id,
        r.id(),
        actor,
        kind,
        body,
        Set.of(
                    "STATE_CHANGED",
                    "DELIVERED",
                    "ACTION_REVIEW",
                    "DOCUMENT_RESCAN",
                    "DETAILS_AMENDED",
                    "NOTIFICATION_RECEIVED")
                .contains(kind)
            ? "RECORDED"
            : "REQUESTED",
        now,
        now);
    return id;
  }

  List<Action> actions(String id) {
    return db.query(
        "SELECT * FROM exam_intake_action WHERE request_id=? ORDER BY created_at",
        (rs, n) ->
            new Action(
                rs.getString("id"),
                rs.getString("request_id"),
                rs.getLong("actor_id"),
                rs.getString("kind"),
                rs.getString("body"),
                rs.getString("state"),
                (Integer) rs.getObject("amount_won"),
                rs.getTimestamp("created_at").toInstant()),
        id);
  }

  void event(Request r, String type, String key) {
    var now = Timestamp.from(Instant.now());
    db.update(
        "INSERT INTO"
            + " exam_intake_event(id,request_id,event_type,event_key,state,attempts,created_at,next_attempt_at)"
            + " VALUES(?,?,?,?,'PENDING',0,?,?)",
        UUID.randomUUID().toString(),
        r.id(),
        type,
        key,
        now,
        now);
  }

  List<Document> documents(String request) {
    return db.query(
        "SELECT * FROM exam_intake_document WHERE request_id=? ORDER BY created_at",
        (rs, n) ->
            new Document(
                rs.getString("id"),
                rs.getString("request_id"),
                rs.getString("original_name"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getString("sha256"),
                rs.getString("storage_name"),
                rs.getString("scan_state"),
                rs.getBoolean("hidden")),
        request);
  }

  Optional<Bank> bank() {
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
}
