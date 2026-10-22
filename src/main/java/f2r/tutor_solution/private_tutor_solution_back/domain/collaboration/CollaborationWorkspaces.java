package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import static f2r.tutor_solution.private_tutor_solution_back.domain.collaboration.CollaborationService.error;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollaborationWorkspaces {
  public static final String DEFAULT = "ai-agent-part1";
  private final JdbcTemplate db;
  private final StudioService studio;

  public CollaborationWorkspaces(JdbcTemplate db, StudioService studio) {
    this.db = db;
    this.studio = studio;
  }

  public String require(AuthenticatedPrincipal p, String id, boolean edit, boolean manage) {
    studio.require(p, edit, false);
    if (id == null || id.length() > 64) throw error(400, "프로젝트 ID를 확인하세요.");
    if (db.queryForObject(
            "SELECT COUNT(*) FROM ai_collaboration_workspaces WHERE id=?", Long.class, id)
        == 0) throw error(404, "프로젝트가 없습니다.");
    String role;
    if ("ADMIN".equals(p.role())) role = "MANAGER";
    else if (DEFAULT.equals(id))
      role = studio.permission(p).equals("APPROVER") ? "MANAGER" : studio.permission(p);
    else
      role =
          db
              .queryForList(
                  "SELECT permission FROM ai_collaboration_workspace_members WHERE workspace_id=?"
                      + " AND user_id=?",
                  id,
                  p.userId())
              .stream()
              .findFirst()
              .map(row -> row.get("permission").toString())
              .orElseThrow(() -> error(403, "이 프로젝트의 참여자가 아닙니다."));
    if ((edit && role.equals("VIEWER")) || (manage && !role.equals("MANAGER")))
      throw error(403, "프로젝트 권한이 없습니다.");
    return role;
  }

  public List<Map<String, Object>> list(AuthenticatedPrincipal p) {
    studio.require(p, false, false);
    if ("ADMIN".equals(p.role()))
      return db.queryForList(
          "SELECT id,title,owner_id FROM ai_collaboration_workspaces ORDER BY created_at,id");
    return db.queryForList(
        "SELECT w.id,w.title,w.owner_id FROM ai_collaboration_workspaces w WHERE w.id=? OR"
            + " EXISTS(SELECT 1 FROM ai_collaboration_workspace_members m WHERE m.workspace_id=w.id"
            + " AND m.user_id=?) ORDER BY w.created_at,w.id",
        DEFAULT,
        p.userId());
  }

  @Transactional
  public Map<String, Object> create(AuthenticatedPrincipal p, String title) {
    studio.require(p, true, false);
    String clean = CollaborationService.text(title, 150), id = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO ai_collaboration_workspaces(id,title,owner_id) VALUES(?,?,?)",
        id,
        clean,
        p.userId());
    db.update(
        "INSERT INTO ai_collaboration_workspace_members VALUES(?,?,'MANAGER')", id, p.userId());
    memory(p, id, "PROJECT_CREATED", clean, null);
    return snapshot(p, id);
  }

  @Transactional
  public void member(AuthenticatedPrincipal p, String id, long userId, String permission) {
    require(p, id, true, true);
    if (DEFAULT.equals(id)) throw error(409, "기본 제작실 권한은 기존 제작실 접근 관리에서 변경하세요.");
    if (!Set.of("VIEWER", "EDITOR", "MANAGER", "REVOKE").contains(permission))
      throw error(400, "권한 값 오류");
    var target =
        db.queryForList("SELECT user_id,role FROM users WHERE user_id=?", userId).stream()
            .findFirst()
            .orElseThrow(() -> error(400, "제작 담당자 ID를 확인하세요."));
    studio.require(
        new AuthenticatedPrincipal(userId, "permission-check", target.get("role").toString()),
        false,
        false);
    long owner =
        db.queryForObject(
            "SELECT owner_id FROM ai_collaboration_workspaces WHERE id=?", Long.class, id);
    if (owner == userId) throw error(409, "프로젝트 등록자의 관리 권한은 보존합니다.");
    db.update(
        "DELETE FROM ai_collaboration_workspace_members WHERE workspace_id=? AND user_id=?",
        id,
        userId);
    if (!permission.equals("REVOKE"))
      db.update(
          "INSERT INTO ai_collaboration_workspace_members VALUES(?,?,?)", id, userId, permission);
    memory(p, id, "MEMBER", userId + ":" + permission, null);
  }

  @Transactional
  public void memory(AuthenticatedPrincipal p, String id, String type, String body, String agent) {
    require(p, id, true, false);
    if (!Set.of(
            "NOTE",
            "DECISION",
            "MILESTONE",
            "HUMAN_REVIEW",
            "PROJECT_CREATED",
            "MEMBER",
            "FILE_DRAFT",
            "FILE_REVIEW",
            "CONNECTOR_BOUND",
            "CONNECTOR_REVOKED")
        .contains(type)) throw error(400, "기억 유형 오류");
    db.update(
        "INSERT INTO"
            + " ai_collaboration_workspace_events(workspace_id,actor_id,actor_agent,event_type,body)"
            + " VALUES(?,?,?,?,?)",
        id,
        p.userId(),
        agent,
        type,
        CollaborationService.text(body, 4000));
  }

  public Map<String, Object> snapshot(AuthenticatedPrincipal p, String id) {
    String permission = require(p, id, false, false);
    var result =
        new LinkedHashMap<>(
            db.queryForMap(
                "SELECT id,title,owner_id,created_at FROM ai_collaboration_workspaces WHERE id=?",
                id));
    result.put("permission", permission);
    result.put(
        "memory",
        db.queryForList(
            "SELECT id,actor_id,actor_agent,event_type,body,created_at FROM"
                + " ai_collaboration_workspace_events WHERE workspace_id=? ORDER BY id DESC LIMIT"
                + " 200",
            id));
    result.put(
        "files",
        db.queryForList(
            "SELECT"
                + " id,parent_id,filename,extension,sha256,size_bytes,status,registered_by,registered_agent,created_at"
                + " FROM ai_collaboration_files WHERE workspace_id=? ORDER BY created_at DESC,id"
                + " DESC LIMIT 200",
            id));
    if (permission.equals("MANAGER"))
      result.put(
          "members",
          db.queryForList(
              "SELECT user_id,permission FROM ai_collaboration_workspace_members WHERE"
                  + " workspace_id=?",
              id));
    return result;
  }

  public List<Map<String, Object>> search(AuthenticatedPrincipal p, String id, String query) {
    require(p, id, false, false);
    String q =
        "%"
            + CollaborationService.text(query, 200)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
            + "%";
    return db.queryForList(
        "SELECT 'TASK' AS kind,id AS source_id,title AS body,created_at FROM ai_collaboration_tasks"
            + " WHERE COALESCE(workspace_id,?)=? AND (title LIKE ? ESCAPE '!' OR instruction LIKE ?"
            + " ESCAPE '!') UNION ALL SELECT 'MEMORY',CAST(id AS CHAR),body,created_at FROM"
            + " ai_collaboration_workspace_events WHERE workspace_id=? AND body LIKE ? ESCAPE '!'"
            + " ORDER BY created_at DESC LIMIT 100",
        DEFAULT,
        id,
        q,
        q,
        id,
        q);
  }
}
