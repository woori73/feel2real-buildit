package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only catalogue. Unlinked uploads stay visible; original records and approvals are untouched. */
@Service
@RequiredArgsConstructor
public class StudioCatalog {
  private final JdbcTemplate db;
  private final StudioService studio;

  private static final String COLUMNS =
      "c.id,c.entry_type,c.item_key,c.label,c.kind,c.episode,c.cut_number,c.origin,c.title,"
          + "c.work_status,c.file_id,c.head_id,c.approved_id,c.filename,c.sha256,c.extension,"
          + "c.size_bytes,c.created_at,c.last_review";
  private static final String SOURCE = """
      (SELECT r.id,'REVISION' AS entry_type,r.item_key,r.label,r.kind,r.episode,r.cut_number,
        r.origin,r.title,r.content,r.dialogue,r.direction,r.work_status,r.file_id,
        i.head_id,i.approved_id,f.filename,f.sha256,f.extension,f.size_bytes,r.created_at,
        COALESCE((SELECT e.action FROM studio_events e
          WHERE e.project_id=r.project_id AND e.revision_id=r.id
          AND e.action IN ('APPROVE','REJECT','REQUEST_CHANGES','WITHDRAW_APPROVAL')
          ORDER BY e.id DESC LIMIT 1),'') AS last_review
       FROM studio_revisions r JOIN studio_items i
         ON i.project_id=r.project_id AND i.item_key=r.item_key
       LEFT JOIN studio_files f ON f.project_id=r.project_id AND f.id=r.file_id
       WHERE r.project_id=?
       UNION ALL
       SELECT f.id,'FILE',NULL,NULL,'FILE',NULL,NULL,'ORIGINAL',f.filename,'','','',
         'UPLOADED',f.id,NULL,NULL,f.filename,f.sha256,f.extension,f.size_bytes,f.created_at,''
       FROM studio_files f WHERE f.project_id=?
         AND NOT EXISTS (SELECT 1 FROM studio_revisions r
           WHERE r.project_id=f.project_id AND r.file_id=f.id)) c
      """;

  public List<Map<String, Object>> list(
      AuthenticatedPrincipal principal, String section, String episode, String kind,
      String media, String status, String versions, String query, int limit, int offset) {
    studio.require(principal, false, false);
    if (limit < 1 || limit > 100 || offset < 0 || offset > 1000000)
      throw StudioService.error(400, "페이지 범위를 확인하세요.");
    allowed(section, Set.of("materials", "review"));
    allowed(versions, Set.of("latest", "all"));
    allowed(media, Set.of("", "VIDEO", "IMAGE", "AUDIO", "DOCUMENT", "PACKAGE"));
    allowed(status, Set.of("", "PENDING", "APPROVED", "CHANGES", "REJECTED", "UNASSIGNED"));
    allowed(kind, Set.of("", "SCRIPT", "STORYBOARD", "CHARACTER", "VIDEO", "MUSIC", "JOB", "REVIEW"));
    if (query == null || query.length() > 200)
      throw StudioService.error(400, "검색어는 200자 이하입니다.");
    List<Object> args = new ArrayList<>(List.of(studio.projectId(), studio.projectId()));
    StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM " + SOURCE + " WHERE 1=1");
    if (versions.equals("latest"))
      sql.append(" AND (c.entry_type='FILE' OR c.id=c.head_id OR c.id=c.approved_id)");
    if (section.equals("review") && !status.equals("UNASSIGNED"))
      sql.append(" AND (c.kind='REVIEW' OR c.work_status='REVIEW' OR c.id=c.approved_id"
          + " OR c.last_review<>'' OR (c.entry_type='FILE' AND ("
          + "LOWER(c.filename) LIKE '%review%' OR LOWER(c.filename) LIKE '%compare%'"
          + " OR LOWER(c.filename) LIKE '%preview%' OR LOWER(c.filename) LIKE 'qa_%'"
          + " OR c.filename LIKE '%검수%' OR c.filename LIKE '%비교%')))");
    if (episode.equals("common")) sql.append(" AND c.episode IS NULL");
    else if (!episode.isEmpty()) {
      int number;
      try { number = Integer.parseInt(episode); }
      catch (NumberFormatException e) { throw StudioService.error(400, "회차를 확인하세요."); }
      if (number < 1 || number > 14) throw StudioService.error(400, "회차를 확인하세요.");
      sql.append(" AND c.episode=?"); args.add(number);
    }
    if (!kind.isEmpty()) {
      sql.append(" AND (c.kind=?"); args.add(kind);
      if (kind.equals("VIDEO")) sql.append(" OR LOWER(c.extension) IN ('mp4','mov')");
      if (kind.equals("MUSIC")) sql.append(" OR LOWER(c.extension) IN ('mp3','wav','flac')");
      sql.append(")");
    }
    switch (media) {
      case "VIDEO" -> sql.append(" AND LOWER(c.extension) IN ('mp4','mov')");
      case "IMAGE" -> sql.append(" AND LOWER(c.extension) IN ('png','jpg','jpeg','webp')");
      case "AUDIO" -> sql.append(" AND LOWER(c.extension) IN ('mp3','wav','flac')");
      case "PACKAGE" -> sql.append(" AND LOWER(c.extension) IN ('zip','glb','gltf')");
      case "DOCUMENT" -> sql.append(" AND (c.extension IS NULL OR LOWER(c.extension) NOT IN"
          + " ('mp4','mov','png','jpg','jpeg','webp','mp3','wav','flac','zip','glb','gltf'))");
      default -> { }
    }
    switch (status) {
      case "APPROVED" -> sql.append(" AND c.id=c.approved_id");
      case "PENDING" -> sql.append(" AND c.work_status='REVIEW' AND"
          + " (c.approved_id IS NULL OR c.id<>c.approved_id) AND c.last_review<>'REJECT'");
      case "CHANGES" -> sql.append(" AND c.last_review='REQUEST_CHANGES'");
      case "REJECTED" -> sql.append(" AND c.last_review='REJECT'");
      case "UNASSIGNED" -> sql.append(" AND c.entry_type='FILE'");
      default -> { }
    }
    if (!query.isBlank()) {
      sql.append(" AND (");
      String[] fields = {"title", "item_key", "label", "filename", "sha256", "content", "dialogue", "direction"};
      for (int n = 0; n < fields.length; n++) {
        if (n > 0) sql.append(" OR ");
        sql.append("LOCATE(LOWER(?),LOWER(COALESCE(c." + fields[n] + ",'')))>0");
        args.add(query.trim());
      }
      sql.append(")");
    }
    args.add(limit); args.add(offset);
    return db.queryForList(sql + " ORDER BY c.created_at DESC,c.id DESC LIMIT ? OFFSET ?", args.toArray());
  }

  private static void allowed(String value, Set<String> options) {
    if (value == null || !options.contains(value))
      throw StudioService.error(400, "목록 필터를 확인하세요.");
  }
}
