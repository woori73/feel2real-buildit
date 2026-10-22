package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CollaborationService {
  static final String PROJECT = "ai-agent-part1";
  static final List<String> ORDER = List.of("GPT", "MUSE", "CODEX", "CLAUDE");
  private final JdbcTemplate db;
  private final StudioService studio;
  private final CollaborationProvider provider;
  private final TransactionTemplate tx;

  @org.springframework.beans.factory.annotation.Value(
      "${app.collaboration.daily-budget-microusd:0}")
  private long dailyBudget;

  public CollaborationService(
      JdbcTemplate db,
      StudioService studio,
      CollaborationProvider provider,
      PlatformTransactionManager manager) {
    this.db = db;
    this.studio = studio;
    this.provider = provider;
    this.tx = new TransactionTemplate(manager);
  }

  public record Create(
      String title,
      String instruction,
      List<String> participants,
      int maxCalls,
      long budgetMicrousd) {}

  public record Change(
      long version, String action, boolean externalConsent, boolean acknowledgeUnknown) {}

  public record Artifact(long version, String fileId, String revisionId, Integer prNumber) {}

  public record Call(String taskId, String runId, String agent, String prompt, long ownerId) {}

  static org.springframework.web.server.ResponseStatusException error(int code, String message) {
    return new org.springframework.web.server.ResponseStatusException(
        org.springframework.http.HttpStatus.valueOf(code), message);
  }

  static String text(String value, int max) {
    if (value == null || value.isBlank() || value.length() > max || value.indexOf('\0') >= 0)
      throw error(400, "입력 길이와 내용을 확인하세요.");
    return value.strip();
  }

  static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  static List<String> participants(Map<String, Object> task) {
    return List.of(task.get("participants").toString().split(","));
  }

  void event(String task, String actor, Long user, String type, String body, String run) {
    db.update(
        "INSERT INTO ai_collaboration_events(task_id,actor,actor_user_id,event_type,body,run_id)"
            + " VALUES(?,?,?,?,?,?)",
        task,
        actor,
        user,
        type,
        body,
        run);
  }

  Map<String, Object> task(String id, boolean lock) {
    return db
        .queryForList(
            "SELECT * FROM ai_collaboration_tasks WHERE id=? AND project_id=?"
                + (lock ? " FOR UPDATE" : ""),
            id,
            PROJECT)
        .stream()
        .findFirst()
        .orElseThrow(() -> error(404, "작업이 없습니다."));
  }

  void owner(AuthenticatedPrincipal p, Map<String, Object> task) {
    studio.require(p, true, false);
    if (!"ADMIN".equals(p.role()) && p.userId() != number(task, "owner_id"))
      throw error(403, "작업 등록자만 제어할 수 있습니다.");
  }

  void expected(Map<String, Object> task, long version) {
    if (number(task, "version") != version) throw error(409, "작업이 변경되었습니다. 최신 상태를 확인하세요.");
  }

  public Map<String, Object> agents(AuthenticatedPrincipal p) {
    studio.require(p, false, false);
    var out = new LinkedHashMap<String, Object>();
    for (String agent : ORDER) out.put(agent, provider.readiness(agent));
    return out;
  }

  public List<Map<String, Object>> list(AuthenticatedPrincipal p) {
    studio.require(p, false, false);
    return db.queryForList(
        "SELECT"
            + " id,title,owner_id,participants,status,calls_used,max_calls,budget_microusd,reserved_microusd,version,created_at"
            + " FROM ai_collaboration_tasks WHERE project_id=? ORDER BY created_at DESC LIMIT 100",
        PROJECT);
  }

  public Map<String, Object> detail(AuthenticatedPrincipal p, String id) {
    studio.require(p, false, false);
    var result = new LinkedHashMap<String, Object>(task(id, false));
    result.put(
        "events",
        db.queryForList(
            "SELECT id,actor,actor_user_id,event_type,body,run_id,created_at FROM"
                + " ai_collaboration_events WHERE task_id=? ORDER BY id DESC LIMIT 200",
            id));
    result.put(
        "artifacts",
        db.queryForList(
            "SELECT a.id,a.file_id,a.revision_id,a.pr_number,f.filename,f.sha256,f.size_bytes FROM"
                + " ai_collaboration_artifacts a LEFT JOIN studio_files f ON f.id=a.file_id WHERE"
                + " a.task_id=? ORDER BY a.created_at",
            id));
    result.put("canControl", "ADMIN".equals(p.role()) || p.userId() == number(result, "owner_id"));
    return result;
  }

  public List<Map<String, Object>> events(AuthenticatedPrincipal p, String id, long beforeId) {
    studio.require(p, false, false);
    task(id, false);
    if (beforeId < 0) throw error(400, "이력 위치 오류");
    return beforeId == 0
        ? db.queryForList(
            "SELECT id,actor,actor_user_id,event_type,body,run_id,created_at FROM"
                + " ai_collaboration_events WHERE task_id=? ORDER BY id DESC LIMIT 200",
            id)
        : db.queryForList(
            "SELECT id,actor,actor_user_id,event_type,body,run_id,created_at FROM"
                + " ai_collaboration_events WHERE task_id=? AND id<? ORDER BY id DESC LIMIT 200",
            id,
            beforeId);
  }

  public Map<String, Object> create(AuthenticatedPrincipal p, Create request) {
    studio.require(p, true, false);
    String title = text(request.title(), 150), instruction = text(request.instruction(), 8000);
    if (request.participants() == null
        || request.participants().isEmpty()
        || request.participants().size() > 4
        || new HashSet<>(request.participants()).size() != request.participants().size()
        || !ORDER.containsAll(request.participants())) throw error(400, "참여 AI를 직접 선택하세요.");
    if (request.maxCalls() < 1
        || request.maxCalls() > 20
        || request.budgetMicrousd() < 0
        || request.budgetMicrousd() > 100_000_000)
      throw error(400, "호출은 1~20회, 비용 상한은 0~100 USD로 지정하세요.");
    String selected =
        String.join(",", ORDER.stream().filter(request.participants()::contains).toList());
    String id = UUID.randomUUID().toString();
    tx.executeWithoutResult(
        status -> {
          db.update(
              "INSERT INTO"
                  + " ai_collaboration_tasks(id,project_id,owner_id,title,instruction,participants,status,max_calls,budget_microusd)"
                  + " VALUES(?,?,?,?,?,?,'DRAFT',?,?)",
              id,
              PROJECT,
              p.userId(),
              title,
              instruction,
              selected,
              request.maxCalls(),
              request.budgetMicrousd());
          event(
              id,
              "USER",
              p.userId(),
              "CREATED",
              "참여: "
                  + selected
                  + " / 호출 상한: "
                  + request.maxCalls()
                  + " / 비용 상한 μUSD: "
                  + request.budgetMicrousd(),
              null);
        });
    return detail(p, id);
  }

  public Map<String, Object> change(AuthenticatedPrincipal p, String id, Change change) {
    tx.executeWithoutResult(
        transaction -> {
          var task = task(id, true);
          owner(p, task);
          expected(task, change.version());
          String current = task.get("status").toString(), next;
          switch (change.action()) {
            case "start", "resume" -> {
              if (!Set.of("DRAFT", "PAUSED", "UNKNOWN", "BLOCKED").contains(current)
                  || task.get("active_run") != null) throw error(409, "진행 중 호출이 끝난 뒤 재개하세요.");
              if (current.equals("UNKNOWN") && !change.acknowledgeUnknown())
                throw error(409, "이전 호출의 결과·과금이 불명확함을 확인해야 합니다.");
              if (!change.externalConsent())
                throw error(400, "선택한 AI에 작업 지시·연결 자료·공동 타임라인을 제공하는 데 동의하세요.");
              if (number(task, "phase_index") >= participants(task).size())
                next = "WAITING_APPROVAL";
              else {
                for (String agent : participants(task))
                  if (!provider.readiness(agent).enabled())
                    throw error(409, agent + " 연결과 비용 승인이 준비되지 않았습니다.");
                next = "RUNNING";
              }
            }
            case "pause" -> {
              if (!Set.of("RUNNING", "DRAFT", "WAITING_APPROVAL").contains(current))
                throw error(409, "현재 중단할 수 없습니다.");
              next = "PAUSED";
            }
            case "approve" -> {
              if (!current.equals("WAITING_APPROVAL") || task.get("active_run") != null)
                throw error(409, "완료된 결과를 확인한 뒤 승인하세요.");
              next = "APPROVED";
            }
            case "cancel" -> {
              if (current.equals("APPROVED") || current.equals("CANCELLED"))
                throw error(409, "이미 종료된 작업입니다.");
              next = "CANCELLED";
            }
            case "recover" -> {
              if (task.get("active_run") == null || !change.acknowledgeUnknown())
                throw error(409, "미완료 호출과 과금 불명 상태를 확인하세요.");
              var since = (java.sql.Timestamp) task.get("updated_at");
              if (since.toInstant().isAfter(java.time.Instant.now().minusSeconds(600)))
                throw error(409, "호출 시작 후 10분이 지나야 수동 복구할 수 있습니다.");
              db.update("UPDATE ai_collaboration_tasks SET active_run=NULL WHERE id=?", id);
              next = "UNKNOWN";
            }
            default -> throw error(400, "지원하지 않는 작업입니다.");
          }
          db.update(
              "UPDATE ai_collaboration_tasks SET"
                  + " status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
              next,
              id);
          event(
              id,
              "USER",
              p.userId(),
              change.action().toUpperCase(Locale.ROOT),
              "상태 " + current + " → " + next + " / 확인한 버전 " + change.version(),
              null);
        });
    return detail(p, id);
  }

  public Map<String, Object> message(
      AuthenticatedPrincipal p, String id, long version, String body) {
    String clean = text(body, 4000);
    tx.executeWithoutResult(
        transaction -> {
          var task = task(id, true);
          owner(p, task);
          expected(task, version);
          if (Set.of("APPROVED", "CANCELLED").contains(task.get("status")))
            throw error(409, "종료된 작업입니다.");
          event(id, "USER", p.userId(), "MESSAGE", clean, null);
          db.update(
              "UPDATE ai_collaboration_tasks SET version=version+1,updated_at=CURRENT_TIMESTAMP"
                  + " WHERE id=?",
              id);
        });
    return detail(p, id);
  }

  public Map<String, Object> attach(AuthenticatedPrincipal p, String id, Artifact artifact) {
    tx.executeWithoutResult(
        transaction -> {
          var task = task(id, true);
          owner(p, task);
          expected(task, artifact.version());
          if (!Set.of("DRAFT", "PAUSED").contains(task.get("status"))
              || task.get("active_run") != null) throw error(409, "자료 연결은 시작 전 또는 중단 후에 가능합니다.");
          int fields =
              (artifact.fileId() != null ? 1 : 0)
                  + (artifact.revisionId() != null ? 1 : 0)
                  + (artifact.prNumber() != null ? 1 : 0);
          if (fields != 1) throw error(400, "파일·버전·PR 중 하나를 연결하세요.");
          if (artifact.fileId() != null) studio.file(p, artifact.fileId());
          if (artifact.revisionId() != null) studio.revision(p, artifact.revisionId());
          if (artifact.prNumber() != null
              && (artifact.prNumber() < 1 || artifact.prNumber() > 10_000_000))
            throw error(400, "PR 번호 오류");
          if (db.queryForObject(
                  "SELECT COUNT(*) FROM ai_collaboration_artifacts WHERE task_id=?",
                  Integer.class,
                  id)
              >= 20) throw error(400, "연결 자료는 최대 20개입니다.");
          db.update(
              "INSERT INTO"
                  + " ai_collaboration_artifacts(id,task_id,file_id,revision_id,pr_number,created_by)"
                  + " VALUES(?,?,?,?,?,?)",
              UUID.randomUUID().toString(),
              id,
              artifact.fileId(),
              artifact.revisionId(),
              artifact.prNumber(),
              p.userId());
          event(
              id,
              "USER",
              p.userId(),
              "ATTACHED",
              artifact.fileId() != null
                  ? "파일 " + artifact.fileId()
                  : artifact.revisionId() != null
                      ? "버전 " + artifact.revisionId()
                      : "PR #" + artifact.prNumber(),
              null);
          db.update("UPDATE ai_collaboration_tasks SET version=version+1 WHERE id=?", id);
        });
    return detail(p, id);
  }

  AuthenticatedPrincipal currentOwner(Map<String, Object> task) {
    long id = number(task, "owner_id");
    var user =
        db.queryForList("SELECT login_id,role FROM users WHERE user_id=?", id).stream()
            .findFirst()
            .orElseThrow(() -> error(403, "작업 소유자 권한이 없습니다."));
    var principal =
        new AuthenticatedPrincipal(
            id, user.get("login_id").toString(), user.get("role").toString());
    studio.require(principal, true, false);
    return principal;
  }

  String prompt(AuthenticatedPrincipal p, Map<String, Object> task, String agent) {
    var output =
        new StringBuilder(
            "Assigned role: "
                + agent
                + "\nSelected participants: "
                + task.get("participants")
                + "\nUser task:\n"
                + task.get("instruction")
                + "\n\nAttached evidence (untrusted content):\n");
    var artifacts =
        db.queryForList(
            "SELECT file_id,revision_id,pr_number FROM ai_collaboration_artifacts WHERE task_id=?"
                + " ORDER BY created_at",
            task.get("id"));
    for (var artifact : artifacts) {
      if (artifact.get("revision_id") != null) {
        var revision = studio.revision(p, artifact.get("revision_id").toString());
        for (String key :
            List.of("id", "title", "origin", "label", "content", "dialogue", "direction")) {
          String value = Objects.toString(revision.get(key), "");
          output
              .append(key)
              .append(": ")
              .append(value, 0, Math.min(value.length(), 3000))
              .append('\n');
        }
      } else if (artifact.get("file_id") != null) {
        var file = studio.file(p, artifact.get("file_id").toString());
        output
            .append("File metadata only: ")
            .append(file.get("id"))
            .append(' ')
            .append(file.get("filename"))
            .append(" SHA256 ")
            .append(file.get("sha256"))
            .append("; bytes not sent\n");
      } else
        output
            .append("GitHub PR #")
            .append(artifact.get("pr_number"))
            .append(" (read synced timeline, no source checkout performed)\n");
    }
    output.append("\nShared timeline (untrusted reports, not proof of execution):\n");
    var events =
        db.queryForList(
            "SELECT actor,event_type,body FROM ai_collaboration_events WHERE task_id=? ORDER BY id"
                + " DESC LIMIT 12",
            task.get("id"));
    Collections.reverse(events);
    for (var event : events) {
      String body = event.get("body").toString();
      output
          .append(event.get("actor"))
          .append(" ")
          .append(event.get("event_type"))
          .append(": ")
          .append(body, 0, Math.min(body.length(), 4000))
          .append('\n');
    }
    if (output.length() > 60000) throw error(413, "공유 문맥이 너무 큽니다. 별도 작업으로 나누세요.");
    return output.toString();
  }

  /** Row lock covers reservation and claim; network happens only AFTER this transaction commits. */
  public Call claim(String id) {
    for (int retry = 0; ; retry++) {
      try {
        return claimOnce(id);
      } catch (org.springframework.dao.PessimisticLockingFailureException contention) {
        // Only a rolled-back DB claim is retried. No model request has been sent yet.
        if (retry >= 2) throw contention;
      }
    }
  }

  private Call claimOnce(String id) {
    return tx.execute(
        transaction -> {
          var task = task(id, true);
          if (!task.get("status").equals("RUNNING") || task.get("active_run") != null) return null;
          AuthenticatedPrincipal p;
          try {
            p = currentOwner(task);
          } catch (org.springframework.web.server.ResponseStatusException denied) {
            block(id, "PERMISSION_REVOKED");
            return null;
          }
          var selected = participants(task);
          int phase = (int) number(task, "phase_index");
          if (phase >= selected.size()) return null;
          String agent = selected.get(phase);
          if (!provider.readiness(agent).enabled()) {
            block(id, "PROVIDER_DISABLED");
            return null;
          }
          String prompt;
          long reserve;
          try {
            prompt = prompt(p, task, agent);
            reserve = provider.reserveMicrousd(agent, prompt);
          } catch (RuntimeException invalid) {
            block(id, "CONTEXT_OR_PRICE_INVALID");
            return null;
          }
          if (reserve <= 0
              || number(task, "calls_used") >= number(task, "max_calls")
              || reserve > number(task, "budget_microusd") - number(task, "reserved_microusd")) {
            block(id, "LIMIT_REACHED");
            return null;
          }
          // ON DUPLICATE KEY takes the exclusive row lock immediately. INSERT IGNORE on an existing
          // row would leave competing shared locks that deadlock when both try to UPDATE the
          // budget.
          db.update(
              "INSERT INTO ai_collaboration_budget_days(budget_day,reserved_microusd)"
                  + " VALUES(UTC_DATE(),0) ON DUPLICATE KEY UPDATE"
                  + " reserved_microusd=reserved_microusd");
          if (dailyBudget < reserve
              || db.update(
                      "UPDATE ai_collaboration_budget_days SET"
                          + " reserved_microusd=reserved_microusd+? WHERE budget_day=UTC_DATE() AND"
                          + " reserved_microusd<=?",
                      reserve,
                      dailyBudget - reserve)
                  != 1) {
            block(id, "DAILY_LIMIT_REACHED");
            return null;
          }
          String run = UUID.randomUUID().toString();
          db.update(
              "UPDATE ai_collaboration_tasks SET"
                  + " active_run=?,calls_used=calls_used+1,reserved_microusd=reserved_microusd+?,version=version+1,updated_at=CURRENT_TIMESTAMP"
                  + " WHERE id=?",
              run,
              reserve,
              id);
          event(
              id, agent, null, "CALL_STARTED", "예약 μUSD: " + reserve + " / 외부 제공에 동의한 작업만 호출", run);
          return new Call(id, run, agent, prompt, p.userId());
        });
  }

  void block(String id, String reason) {
    db.update(
        "UPDATE ai_collaboration_tasks SET"
            + " status='BLOCKED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
        id);
    event(id, "SYSTEM", null, reason, "자동 호출을 중단했습니다.", null);
  }

  public void finish(Call call, CollaborationProvider.Reply reply) {
    tx.executeWithoutResult(
        transaction -> {
          var task = task(call.taskId(), true);
          if (!Objects.equals(task.get("active_run"), call.runId())) return;
          var selected = participants(task);
          int next = (int) number(task, "phase_index") + 1;
          if (call.agent().equals("CLAUDE")
              && reply.decision().equals("CHANGES_REQUESTED")
              && selected.contains("CODEX")) next = selected.indexOf("CODEX");
          String current = task.get("status").toString();
          String status =
              current.equals("RUNNING")
                  ? (next >= selected.size() ? "WAITING_APPROVAL" : "RUNNING")
                  : current;
          if (current.equals("RUNNING")
              && call.agent().equals("CLAUDE")
              && (reply.decision().equals("INFO")
                  || (reply.decision().equals("CHANGES_REQUESTED")
                      && !selected.contains("CODEX")))) {
            status = "BLOCKED";
            next = selected.indexOf("CLAUDE");
          }
          event(
              call.taskId(),
              call.agent(),
              null,
              "MODEL_PROPOSAL",
              text(reply.text(), 16000),
              call.runId());
          event(
              call.taskId(),
              "SYSTEM",
              null,
              "ROUTED",
              call.agent() + " decision=" + reply.decision() + " / 다음 단계 " + next,
              call.runId());
          db.update(
              "UPDATE ai_collaboration_tasks SET"
                  + " active_run=NULL,phase_index=?,status=?,version=version+1,updated_at=CURRENT_TIMESTAMP"
                  + " WHERE id=?",
              next,
              status,
              call.taskId());
        });
  }

  public void unknown(Call call) {
    tx.executeWithoutResult(
        transaction -> {
          var task = task(call.taskId(), true);
          if (!Objects.equals(task.get("active_run"), call.runId())) return;
          String state = task.get("status").equals("CANCELLED") ? "CANCELLED" : "UNKNOWN";
          db.update(
              "UPDATE ai_collaboration_tasks SET"
                  + " active_run=NULL,status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE"
                  + " id=?",
              state,
              call.taskId());
          event(
              call.taskId(),
              "SYSTEM",
              null,
              "CALL_UNKNOWN",
              "호출 결과·과금 불명. 예약 비용을 유지하며 자동 재시도하지 않습니다.",
              call.runId());
        });
  }

  public void githubSnapshot(AuthenticatedPrincipal p, String id, long version, String snapshot) {
    tx.executeWithoutResult(
        transaction -> {
          var task = task(id, true);
          owner(p, task);
          expected(task, version);
          event(id, "GITHUB", p.userId(), "PR_CI_SNAPSHOT", text(snapshot, 12000), null);
          db.update("UPDATE ai_collaboration_tasks SET version=version+1 WHERE id=?", id);
        });
  }
}
