package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import static f2r.tutor_solution.private_tutor_solution_back.domain.collaboration.CollaborationService.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only provenance and keyset pages. No import/approval/mutation web endpoint. */
@Service
public class CollaborationSources {
  private final JdbcTemplate db;
  private final CollaborationWorkspaces spaces;

  public CollaborationSources(JdbcTemplate db, CollaborationWorkspaces spaces) {
    this.db = db;
    this.spaces = spaces;
  }

  public Map<String, Object> files(
      AuthenticatedPrincipal p, String workspace, String before, int limit, String query) {
    spaces.require(p, workspace, false, false);
    if (limit < 1 || limit > 200) throw error(400, "페이지 크기는1~200입니다.");
    String q = query == null ? "" : query.strip();
    if (q.length() > 200) throw error(400, "검색어 길이 오류");
    String pattern = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    String filter =
        "f.workspace_id=? AND (f.filename LIKE ? ESCAPE '!' OR s.source_path LIKE ? ESCAPE '!')";
    var args = new ArrayList<Object>(List.of(workspace, pattern, pattern));
    if (before != null && !before.isBlank()) {
      var cursor =
          db
              .queryForList(
                  "SELECT created_at,id FROM ai_collaboration_files WHERE id=? AND workspace_id=?",
                  before,
                  workspace)
              .stream()
              .findFirst()
              .orElseThrow(() -> error(400, "페이지 위치 오류"));
      filter += " AND (f.created_at<? OR (f.created_at=? AND f.id<?))";
      args.add(cursor.get("created_at"));
      args.add(cursor.get("created_at"));
      args.add(before);
    }
    args.add(limit + 1);
    var rows =
        db.queryForList(
            "SELECT"
                + " f.id,f.workspace_id,f.parent_id,f.filename,f.extension,f.sha256,f.size_bytes,f.status,f.registered_by,f.registered_agent,f.created_at,s.source_path,s.version_label,s.category,s.source_approval"
                + " FROM ai_collaboration_files f LEFT JOIN ai_collaboration_sources s ON"
                + " s.file_id=f.id WHERE "
                + filter
                + " ORDER BY f.created_at DESC,f.id DESC LIMIT ?",
            args.toArray());
    boolean more = rows.size() > limit;
    var items = rows.subList(0, Math.min(limit, rows.size()));
    long count =
        db.queryForObject(
            "SELECT COUNT(*) FROM ai_collaboration_files f LEFT JOIN ai_collaboration_sources s ON"
                + " s.file_id=f.id WHERE f.workspace_id=? AND (f.filename LIKE ? ESCAPE '!' OR"
                + " s.source_path LIKE ? ESCAPE '!')",
            Long.class,
            workspace,
            pattern,
            pattern);
    return Map.of(
        "items", items, "nextBeforeId", more ? items.getLast().get("id") : "", "totalCount", count);
  }

  public Map<String, Object> production(AuthenticatedPrincipal p, String workspace) {
    spaces.require(p, workspace, false, false);
    var batches =
        db.queryForList(
            "SELECT"
                + " id,source_ref,manifest_sha,inventory_sha,expected_files,registered_files,status,created_at"
                + " FROM ai_collaboration_imports WHERE workspace_id=? AND status='COMPLETED' ORDER"
                + " BY created_at DESC,id DESC",
            workspace);
    if (batches.isEmpty())
      return Map.of("batches", List.of(), "cuts", List.of(), "comparedCuts", 0, "passConfirmed", 0);
    String latest = batches.getFirst().get("id").toString();
    var cuts =
        db.queryForList(
            "SELECT"
                + " c.cut_number,c.panel_file_id,c.record_json,c.comparison_status,c.pass_confirmed,f.sha256,f.status,s.source_path,s.version_label,s.source_approval"
                + " FROM ai_collaboration_storyboard_cuts c JOIN ai_collaboration_files f ON"
                + " f.id=c.panel_file_id LEFT JOIN ai_collaboration_sources s ON s.file_id=f.id"
                + " WHERE c.import_id=? ORDER BY c.cut_number",
            latest);
    return Map.of(
        "batches",
        batches,
        "cuts",
        cuts,
        "comparedCuts",
        db.queryForObject(
            "SELECT COUNT(*) FROM ai_collaboration_storyboard_cuts WHERE import_id=? AND"
                + " comparison_status='COMPARED'",
            Integer.class,
            latest),
        "passConfirmed",
        db.queryForObject(
            "SELECT COUNT(*) FROM ai_collaboration_storyboard_cuts WHERE import_id=? AND"
                + " pass_confirmed=TRUE",
            Integer.class,
            latest));
  }
}
