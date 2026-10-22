package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared memory and handoff only. No inference client or paid model API dependency. */
@Service
public class CollaborationService {
  static final String PROJECT = "ai-agent-part1";
  static final List<String> ORDER = List.of("GPT", "MUSE", "CODEX", "CLAUDE");
  private final JdbcTemplate db;
  private final StudioService studio;
  private final CollaborationWorkspaces spaces;
  private final TransactionTemplate tx;

  public CollaborationService(
      JdbcTemplate db,
      StudioService studio,
      CollaborationWorkspaces spaces,
      PlatformTransactionManager manager) {
    this.db = db;
    this.studio = studio;
    this.spaces = spaces;
    this.tx = new TransactionTemplate(manager);
  }

  public record Create(
      String title,
      String instruction,
      List<String> participants,
      int maxCalls,
      long budgetMicrousd,
      String workspaceId) {}

  public record Change(
      long version, String action, boolean externalConsent, boolean acknowledgeUnknown) {}

  public record Artifact(long version, String fileId, String revisionId, Integer prNumber) {}

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

  static String workspace(Map<String, Object> task) {
    return Objects.toString(task.get("workspace_id"), PROJECT);
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
    spaces.require(p, workspace(task), true, false);
    if (!"ADMIN".equals(p.role()) && p.userId() != number(task, "owner_id"))
      throw error(403, "작업 등록자만 제어할 수 있습니다.");
  }

  void expected(Map<String, Object> task, long version) {
    if (number(task, "version") != version) throw error(409, "작업이 변경되었습니다. 최신 상태를 확인하세요.");
  }

  public Map<String, Object> agents(AuthenticatedPrincipal p) {
    studio.require(p, false, false);
    var out = new LinkedHashMap<String, Object>();
    for (String agent : ORDER)
      out.put(
          agent,
          Map.of(
              "mode",
              "SUBSCRIPTION_CONNECTOR",
              "serverAutoRun",
              false,
              "paidModelApi",
              false,
              "actualProductConnectionVerified",
              false));
    return out;
  }

  public List<Map<String, Object>> list(AuthenticatedPrincipal p, String workspaceId) {
    String id = workspaceId == null ? PROJECT : workspaceId;
    spaces.require(p, id, false, false);
    return db.queryForList(
        "SELECT"
            + " id,title,owner_id,participants,status,phase_index,calls_used,max_calls,version,created_at,workspace_id"
            + " FROM ai_collaboration_tasks WHERE project_id=? AND COALESCE(workspace_id,?)=? ORDER"
            + " BY created_at DESC,id DESC LIMIT 100",
        PROJECT,
        PROJECT,
        id);
  }

  public Map<String, Object> detail(AuthenticatedPrincipal p, String id) {
    return detail(p, id, 200, 0);
  }

  public Map<String, Object> detail(
      AuthenticatedPrincipal p, String id, int eventLimit, long beforeId) {
    var task = task(id, false);
    String role = spaces.require(p, workspace(task), false, false);
    var result = new LinkedHashMap<String, Object>(task);
    var eventRows = events(p, id, beforeId, eventLimit);
    result.put("events", eventRows);
    result.put("eventsNextBeforeId", eventRows.isEmpty() ? 0 : eventRows.getLast().get("id"));
    result.put(
        "artifacts",
        db.queryForList(
            "SELECT a.id,a.file_id,a.revision_id,a.pr_number,f.filename,f.sha256,f.size_bytes FROM"
                + " ai_collaboration_artifacts a LEFT JOIN studio_files f ON f.id=a.file_id WHERE"
                + " a.task_id=? ORDER BY a.created_at",
            id));
    result.put(
        "canControl",
        !role.equals("VIEWER")
            && ("ADMIN".equals(p.role()) || p.userId() == number(task, "owner_id")));
    result.put("canComment", !role.equals("VIEWER"));
    int phase = (int) number(task, "phase_index");
    result.put("nextAgent", phase < participants(task).size() ? participants(task).get(phase) : "");
    return result;
  }

  public List<Map<String, Object>> events(AuthenticatedPrincipal p, String id, long beforeId) {
    return events(p, id, beforeId, 200);
  }

  List<Map<String, Object>> events(AuthenticatedPrincipal p, String id, long beforeId, int limit) {
    var task = task(id, false);
    spaces.require(p, workspace(task), false, false);
    if (beforeId < 0 || limit < 1 || limit > 200) throw error(400, "이력 위치 오류");
    return beforeId == 0
        ? db.queryForList(
            "SELECT id,actor,actor_user_id,event_type,body,run_id,created_at FROM"
                + " ai_collaboration_events WHERE task_id=? ORDER BY id DESC LIMIT ?",
            id,
            limit)
        : db.queryForList(
            "SELECT id,actor,actor_user_id,event_type,body,run_id,created_at FROM"
                + " ai_collaboration_events WHERE task_id=? AND id<? ORDER BY id DESC LIMIT ?",
            id,
            beforeId,
            limit);
  }

  public Map<String, Object> create(AuthenticatedPrincipal p, Create r) {
    String work = r.workspaceId() == null ? PROJECT : r.workspaceId();
    spaces.require(p, work, true, false);
    String title = text(r.title(), 150), instruction = text(r.instruction(), 8000);
    if (r.participants() == null
        || r.participants().isEmpty()
        || r.participants().size() > 4
        || new HashSet<>(r.participants()).size() != r.participants().size()
        || !ORDER.containsAll(r.participants())) throw error(400, "참여 AI를 직접 선택하세요.");
    if (r.maxCalls() < 1 || r.maxCalls() > 20 || r.budgetMicrousd() != 0)
      throw error(400, "인계 상한은 1~20회이며 별도 유료 API 예산은 0으로 고정됩니다.");
    String selected = String.join(",", ORDER.stream().filter(r.participants()::contains).toList()),
        id = UUID.randomUUID().toString();
    tx.executeWithoutResult(
        status -> {
          db.update(
              "INSERT INTO"
                  + " ai_collaboration_tasks(id,project_id,workspace_id,owner_id,title,instruction,participants,status,max_calls,budget_microusd)"
                  + " VALUES(?,?,?,?,?,?,?,'DRAFT',?,0)",
              id,
              PROJECT,
              work,
              p.userId(),
              title,
              instruction,
              selected,
              r.maxCalls());
          event(
              id,
              "USER",
              p.userId(),
              "CREATED",
              "참여: " + selected + " / 인계 상한: " + r.maxCalls() + " / 별도 모델 API 비용 0",
              null);
        });
    return detail(p, id);
  }

  public Map<String, Object> change(AuthenticatedPrincipal p, String id, Change change) {
    tx.executeWithoutResult(
        t -> {
          var task = task(id, true);
          owner(p, task);
          expected(task, change.version());
          String current = task.get("status").toString(), next;
          switch (change.action()) {
            case "start", "resume" -> {
              if (!Set.of("DRAFT", "PAUSED", "BLOCKED", "UNKNOWN").contains(current))
                throw error(409, "현재 시작/재개할 수 없습니다.");
              if (number(task, "calls_used") >= number(task, "max_calls"))
                throw error(409, "인계 횟수 상한에 도달했습니다.");
              if (!change.externalConsent()) throw error(400, "선택한 구독 AI 연결에 공유할 작업임을 확인하세요.");
              next =
                  number(task, "phase_index") >= participants(task).size()
                      ? "WAITING_APPROVAL"
                      : "WAITING_AGENT";
            }
            case "pause" -> {
              if (!Set.of("DRAFT", "WAITING_AGENT", "WAITING_APPROVAL", "RUNNING")
                  .contains(current)) throw error(409, "현재 중단할 수 없습니다.");
              next = "PAUSED";
            }
            case "approve" -> {
              if (!current.equals("WAITING_APPROVAL")) throw error(409, "완료된 제안과 검수 이력을 확인하세요.");
              next = "APPROVED";
            }
            case "cancel" -> {
              if (Set.of("APPROVED", "CANCELLED").contains(current))
                throw error(409, "이미 종료된 작업입니다.");
              next = "CANCELLED";
            }
            default -> throw error(400, "지원하지 않는 작업입니다.");
          }
          db.update(
              "UPDATE ai_collaboration_tasks SET"
                  + " status=?,active_run=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE"
                  + " id=?",
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
        t -> {
          var task = task(id, true);
          spaces.require(p, workspace(task), true, false);
          expected(task, version);
          if (Set.of("APPROVED", "CANCELLED").contains(task.get("status")))
            throw error(409, "종료된 작업입니다.");
          event(id, "USER", p.userId(), "HUMAN_REVIEW", clean, null);
          db.update(
              "UPDATE ai_collaboration_tasks SET version=version+1,updated_at=CURRENT_TIMESTAMP"
                  + " WHERE id=?",
              id);
        });
    return detail(p, id);
  }

  public Map<String, Object> attach(AuthenticatedPrincipal p, String id, Artifact a) {
    tx.executeWithoutResult(
        t -> {
          var task = task(id, true);
          owner(p, task);
          expected(task, a.version());
          if (!Set.of("DRAFT", "PAUSED").contains(task.get("status")))
            throw error(409, "자료 연결은 시작 전 또는 중단 후 가능합니다.");
          int fields =
              (a.fileId() != null ? 1 : 0)
                  + (a.revisionId() != null ? 1 : 0)
                  + (a.prNumber() != null ? 1 : 0);
          if (fields != 1) throw error(400, "파일·버전·PR 중 하나를 연결하세요.");
          if (a.fileId() != null) studio.file(p, a.fileId());
          if (a.revisionId() != null) studio.revision(p, a.revisionId());
          if (a.prNumber() != null && (a.prNumber() < 1 || a.prNumber() > 10_000_000))
            throw error(400, "PR 번호 오류");
          if (db.queryForObject(
                  "SELECT COUNT(*) FROM ai_collaboration_artifacts WHERE task_id=?",
                  Integer.class,
                  id)
              >= 20) throw error(400, "연결 자료는 최대20개입니다.");
          db.update(
              "INSERT INTO"
                  + " ai_collaboration_artifacts(id,task_id,file_id,revision_id,pr_number,created_by)"
                  + " VALUES(?,?,?,?,?,?)",
              UUID.randomUUID().toString(),
              id,
              a.fileId(),
              a.revisionId(),
              a.prNumber(),
              p.userId());
          event(
              id,
              "USER",
              p.userId(),
              "ATTACHED",
              Objects.toString(a.fileId(), Objects.toString(a.revisionId(), "PR #" + a.prNumber())),
              null);
          db.update("UPDATE ai_collaboration_tasks SET version=version+1 WHERE id=?", id);
        });
    return detail(p, id);
  }

  public List<Map<String, Object>> inbox(AuthenticatedPrincipal p, String workspace, String agent) {
    spaces.require(p, workspace, false, false);
    if (!ORDER.contains(agent)) throw error(400, "AI 역할 오류");
    return list(p, workspace).stream()
        .filter(
            row ->
                row.get("status").equals("WAITING_AGENT")
                    && number(row, "phase_index") < participants(row).size()
                    && participants(row).get((int) number(row, "phase_index")).equals(agent))
        .toList();
  }

  public Map<String, Object> submit(
      AuthenticatedPrincipal p,
      String workspace,
      String agent,
      String grant,
      String requestId,
      String id,
      long version,
      String body,
      String decision,
      List<String> fileIds) {
    if (!requestId.matches("[A-Za-z0-9_-]{1,64}")) throw error(400, "requestId 오류");
    String clean = text(body, 16000);
    if (!Set.of("INFO", "PASS", "CHANGES_REQUESTED").contains(decision)
        || fileIds == null
        || fileIds.size() > 20) throw error(400, "제안 입력 오류");
    String hash =
        CollaborationFiles.sha(
            (id
                    + "\n"
                    + version
                    + "\n"
                    + clean
                    + "\n"
                    + decision
                    + "\n"
                    + String.join(",", fileIds))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return tx.execute(
        t -> {
          var task = task(id, true);
          spaces.require(p, workspace, true, false);
          if (!workspace(task).equals(workspace) || !participants(task).contains(agent))
            throw error(403, "지정된 AI·프로젝트가 아닙니다.");
          var prior =
              db.queryForList(
                  "SELECT body_sha,result_json FROM ai_collaboration_submissions WHERE grant_id=?"
                      + " AND request_id=?",
                  grant,
                  requestId);
          if (!prior.isEmpty()) {
            if (!prior.getFirst().get("body_sha").equals(hash))
              throw error(409, "동일 요청 ID의 내용이 다릅니다.");
            try {
              return new com.fasterxml.jackson.databind.ObjectMapper()
                  .readValue(prior.getFirst().get("result_json").toString(), Map.class);
            } catch (Exception e) {
              throw error(500, "이력 확인 실패");
            }
          }
          expected(task, version);
          int phase = (int) number(task, "phase_index");
          if (!task.get("status").equals("WAITING_AGENT")
              || phase >= participants(task).size()
              || !participants(task).get(phase).equals(agent))
            throw error(409, "현재 담당 AI의 인계 대기 상태가 아닙니다.");
          if (number(task, "calls_used") >= number(task, "max_calls"))
            throw error(409, "인계 횟수 상한에 도달했습니다.");
          for (String file : fileIds)
            if (db.queryForObject(
                    "SELECT COUNT(*) FROM ai_collaboration_files WHERE id=? AND workspace_id=?",
                    Integer.class,
                    file,
                    workspace)
                != 1) throw error(403, "이 프로젝트의 첨부만 연결하세요.");
          int next = phase + 1;
          String status;
          if (agent.equals("CLAUDE") && decision.equals("CHANGES_REQUESTED")) {
            next =
                participants(task).contains("CODEX") ? participants(task).indexOf("CODEX") : phase;
            status = participants(task).contains("CODEX") ? "WAITING_AGENT" : "BLOCKED";
          } else if (agent.equals("CLAUDE") && !decision.equals("PASS")) {
            next = phase;
            status = "BLOCKED";
          } else status = next >= participants(task).size() ? "WAITING_APPROVAL" : "WAITING_AGENT";
          if (status.equals("WAITING_AGENT")
              && number(task, "calls_used") + 1 >= number(task, "max_calls")) status = "BLOCKED";
          event(id, agent, p.userId(), "DRAFT_PROPOSAL", clean, requestId);
          if (!fileIds.isEmpty())
            event(id, agent, p.userId(), "DRAFT_ATTACHMENTS", String.join(",", fileIds), requestId);
          event(
              id,
              "SYSTEM",
              null,
              "HANDOFF",
              agent
                  + " decision="
                  + decision
                  + " / 다음 담당 "
                  + (next < participants(task).size() ? participants(task).get(next) : "사용자 승인"),
              requestId);
          db.update(
              "UPDATE ai_collaboration_tasks SET"
                  + " phase_index=?,status=?,calls_used=calls_used+1,version=version+1,updated_at=CURRENT_TIMESTAMP"
                  + " WHERE id=?",
              next,
              status,
              id);
          var result =
              Map.<String, Object>of(
                  "taskId",
                  id,
                  "status",
                  status,
                  "version",
                  version + 1,
                  "draft",
                  true,
                  "nextAgent",
                  next < participants(task).size() ? participants(task).get(next) : "");
          try {
            db.update(
                "INSERT INTO"
                    + " ai_collaboration_submissions(grant_id,request_id,task_id,body_sha,result_json)"
                    + " VALUES(?,?,?,?,?)",
                grant,
                requestId,
                id,
                hash,
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result));
          } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw error(500, "이력 저장 실패");
          }
          return result;
        });
  }

  public void githubSnapshot(AuthenticatedPrincipal p, String id, long version, String snapshot) {
    tx.executeWithoutResult(
        t -> {
          var task = task(id, true);
          owner(p, task);
          expected(task, version);
          event(id, "GITHUB", p.userId(), "PR_CI_SNAPSHOT", text(snapshot, 12000), null);
          db.update("UPDATE ai_collaboration_tasks SET version=version+1 WHERE id=?", id);
        });
  }
}
