package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project lifecycle and in-app invitations. Files/revisions/approval pointers are never deleted.
 */
@Service
@RequiredArgsConstructor
public class StudioProjectService {
  private final JdbcTemplate db;
  private final StudioService studio;
  private static final Set<String> ROLES = Set.of("VIEWER", "EDITOR", "APPROVER");

  private void admin(AuthenticatedPrincipal p) {
    if (p == null || !"ADMIN".equals(p.role()))
      throw StudioService.error(403, "프로젝트 관리는 관리자 전용입니다.");
  }

  private String title(String title) {
    if (title == null
        || title.isBlank()
        || title.strip().length() > 150
        || title.chars().anyMatch(c -> c < 32))
      throw StudioService.error(400, "프로젝트 제목은 1~150자로 입력하세요.");
    return title.strip();
  }

  public Object projects(AuthenticatedPrincipal p) {
    if (p == null) throw StudioService.error(401, "로그인이 필요합니다.");
    return db.queryForList(
        "SELECT p.id,p.title,p.created_at,COALESCE((SELECT action='PROJECT_ARCHIVE' FROM"
            + " studio_events e WHERE e.project_id=p.id AND e.action IN"
            + " ('PROJECT_ARCHIVE','PROJECT_REOPEN') ORDER BY e.id DESC LIMIT 1),false) AS archived"
            + " FROM studio_projects p WHERE ?='ADMIN' OR EXISTS(SELECT 1 FROM studio_members m"
            + " WHERE m.project_id=p.id AND m.user_id=? AND m.permission IN"
            + " ('VIEWER','EDITOR','APPROVER')) ORDER BY archived,p.created_at,p.id",
        p.role(),
        p.userId());
  }

  public Object capabilities(AuthenticatedPrincipal p) {
    if (p == null) throw StudioService.error(401, "로그인이 필요합니다.");
    return Map.of("canManage", "ADMIN".equals(p.role()));
  }

  @Transactional
  public Object create(AuthenticatedPrincipal p, String title) {
    admin(p);
    String id = UUID.randomUUID().toString(), checked = title(title);
    db.update("INSERT INTO studio_projects(id,title) VALUES (?,?)", id, checked);
    event(id, "PROJECT_CREATE", checked, p);
    return Map.of("id", id, "title", checked, "archived", false);
  }

  @Transactional
  public Object change(AuthenticatedPrincipal p, String action, String title) {
    admin(p);
    studio.lockProject();
    switch (action == null ? "" : action) {
      case "TITLE" -> {
        String checked = title(title);
        String old =
            db.queryForObject(
                "SELECT title FROM studio_projects WHERE id=?", String.class, studio.projectId());
        if (!checked.equals(old)) {
          db.update("UPDATE studio_projects SET title=? WHERE id=?", checked, studio.projectId());
          event(studio.projectId(), "PROJECT_TITLE", old + " → " + checked, p);
        }
      }
      case "ARCHIVE", "REOPEN" -> {
        if (studio.archivedForWrite() != action.equals("ARCHIVE"))
          event(studio.projectId(), "PROJECT_" + action, "자료·SHA·버전·승인·작업 기억 전체 보존", p);
      }
      default -> throw StudioService.error(400, "제목 수정·보관·재개만 지원합니다. 영구 삭제는 지원하지 않습니다.");
    }
    return studio.overview(p);
  }

  public Object searchUsers(AuthenticatedPrincipal p, String q) {
    admin(p);
    studio.require(p, false, false);
    if (q == null || q.strip().length() < 2 || q.length() > 100)
      throw StudioService.error(400, "검색어는 2~100자입니다.");
    // Locate with literal substring semantics: '%' and '_' never expand to enumerate all accounts.
    return db.queryForList(
        "SELECT user_id,name,nickname FROM users WHERE LOCATE(?,name)>0 OR LOCATE(?,nickname)>0"
            + " ORDER BY user_id LIMIT 20",
        q.strip(),
        q.strip());
  }

  @Transactional
  public Object invite(AuthenticatedPrincipal p, long userId, String permission) {
    admin(p);
    studio.lockProject();
    if (studio.archivedForWrite()) throw StudioService.error(409, "프로젝트를 재개한 뒤 초대하세요.");
    if (!ROLES.contains(permission == null ? "" : permission))
      throw StudioService.error(400, "권한 오류");
    if (db.queryForObject(
            "SELECT COUNT(*) FROM users WHERE user_id=? AND role<>'ADMIN'", Long.class, userId)
        != 1) throw StudioService.error(400, "일반 회원을 선택하세요. 관리자는 이미 접근 권한이 있습니다.");
    if (db.queryForObject(
            "SELECT COUNT(*) FROM studio_members WHERE project_id=? AND user_id=?",
            Long.class,
            studio.projectId(),
            userId)
        != 0) throw StudioService.error(409, "이미 참여 중이거나 초대 대기 중입니다. 기존 접근 권한은 접근 관리에서 변경하세요.");
    db.update(
        "INSERT INTO studio_members VALUES (?,?,?)",
        studio.projectId(),
        userId,
        "PENDING_" + permission);
    event(studio.projectId(), "MEMBER_INVITE", userId + ":" + permission, p);
    return Map.of("userId", userId, "permission", permission, "status", "PENDING");
  }

  public Object invitations(AuthenticatedPrincipal p) {
    if (p == null) throw StudioService.error(401, "로그인이 필요합니다.");
    return db.queryForList(
        "SELECT p.id,p.title,m.permission FROM studio_projects p JOIN studio_members m ON"
            + " m.project_id=p.id WHERE m.user_id=? AND m.permission IN"
            + " ('PENDING_VIEWER','PENDING_EDITOR','PENDING_APPROVER') ORDER BY p.created_at",
        p.userId());
  }

  @Transactional
  public Object answerInvitation(AuthenticatedPrincipal p, boolean accept) {
    if (p == null) throw StudioService.error(401, "로그인이 필요합니다.");
    studio.lockProject();
    var roles =
        db.query(
            "SELECT permission FROM studio_members WHERE project_id=? AND user_id=? FOR UPDATE",
            (rs, n) -> rs.getString(1),
            studio.projectId(),
            p.userId());
    String pending = roles.stream().findFirst().orElse("");
    if (!Set.of("PENDING_VIEWER", "PENDING_EDITOR", "PENDING_APPROVER").contains(pending))
      throw StudioService.error(403, "본인에게 온 대기 중 초대만 처리할 수 있습니다.");
    if (accept) {
      if (studio.archivedForWrite()) throw StudioService.error(409, "프로젝트가 보관되었습니다. 재개 후 수락하세요.");
      db.update(
          "UPDATE studio_members SET permission=? WHERE project_id=? AND user_id=?",
          pending.substring(8),
          studio.projectId(),
          p.userId());
    } else
      db.update(
          "DELETE FROM studio_members WHERE project_id=? AND user_id=?",
          studio.projectId(),
          p.userId());
    event(studio.projectId(), accept ? "MEMBER_ACCEPT" : "MEMBER_DECLINE", pending.substring(8), p);
    return Map.of("accepted", accept);
  }

  public Object notifications(AuthenticatedPrincipal p, Long beforeId) {
    studio.require(p, false, false);
    if (beforeId != null && beforeId < 1) throw StudioService.error(400, "알림 커서 오류");
    long read =
        db
            .query(
                "SELECT note FROM studio_events WHERE project_id=? AND actor_id=? AND"
                    + " action='NOTIFY_READ' ORDER BY id DESC LIMIT 1",
                (rs, n) -> Long.parseLong(rs.getString(1)),
                studio.projectId(),
                p.userId())
            .stream()
            .findFirst()
            .orElse(0L);
    var rows =
        db.queryForList(
            "SELECT e.id,e.action,e.note,e.revision_id,e.created_at,u.name AS actor_name,r.title"
                + " FROM studio_events e LEFT JOIN users u ON u.user_id=e.actor_id LEFT JOIN"
                + " studio_revisions r ON r.id=e.revision_id AND r.project_id=e.project_id WHERE"
                + " e.project_id=? AND e.actor_id<>? AND e.action IN"
                + " ('APPROVE','REJECT','REQUEST_CHANGES','COMMENT') AND e.id<? ORDER BY e.id DESC"
                + " LIMIT 100",
            studio.projectId(),
            p.userId(),
            beforeId == null ? Long.MAX_VALUE : beforeId);
    return Map.of(
        "readThrough",
        read,
        "items",
        rows,
        "nextBeforeId",
        rows.size() == 100 ? rows.getLast().get("id") : 0);
  }

  @Transactional
  public Object markNotifications(AuthenticatedPrincipal p, long throughId) {
    studio.lockProject();
    studio.require(p, false, false);
    if (throughId < 1
        || db.queryForObject(
                "SELECT COUNT(*) FROM studio_events WHERE project_id=? AND id=? AND actor_id<>? AND"
                    + " action IN ('APPROVE','REJECT','REQUEST_CHANGES','COMMENT')",
                Long.class,
                studio.projectId(),
                throughId,
                p.userId())
            != 1) throw StudioService.error(400, "이 프로젝트에서 확인한 알림을 선택하세요.");
    var previous =
        db
            .query(
                "SELECT note FROM studio_events WHERE project_id=? AND actor_id=? AND"
                    + " action='NOTIFY_READ' ORDER BY id DESC LIMIT 1",
                (rs, n) -> Long.parseLong(rs.getString(1)),
                studio.projectId(),
                p.userId())
            .stream()
            .findFirst()
            .orElse(0L);
    if (throughId > previous) event(studio.projectId(), "NOTIFY_READ", Long.toString(throughId), p);
    return Map.of("readThrough", Math.max(previous, throughId));
  }

  private void event(String projectId, String action, String note, AuthenticatedPrincipal p) {
    db.update(
        "INSERT INTO studio_events(project_id,action,note,actor_id) VALUES (?,?,?,?)",
        projectId,
        action,
        note,
        p.userId());
  }
}
