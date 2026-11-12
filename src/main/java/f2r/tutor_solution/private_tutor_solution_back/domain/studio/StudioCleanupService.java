package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.TransactionalFileDeletion;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 폐지 자료 2단계 정리(감독 지시 2026-11-12).
 *
 * <ol>
 *   <li>보관(숨김): 감독이 확인한 버전만 목록·검색에서 빼고 보관 시각을 남긴다. 언제든 되살린다.
 *   <li>영구 삭제: 보관한 지 {@value #HOLD_DAYS}일이 지난 버전 가운데 감독이 확인한 목록만, DB 행과
 *       (다른 곳에서 쓰지 않는) 저장소 파일을 함께 지운다.
 * </ol>
 *
 * <p>최신본·승인본·원본·진행 중 JOB·현재 기준 자료·다른 자료가 참조하는 버전은 두 단계 모두 자동으로 막는다. 모든 실행은
 * {@code studio_cleanup_log}와 {@code studio_events}에 남는다. 보관·되살림·삭제는 관리자(감독)만 한다.
 */
@Service
@RequiredArgsConstructor
public class StudioCleanupService {
  static final int HOLD_DAYS = 14;
  static final int MAX_BATCH = 200;

  /** 현재 쓰는 기준 자료: 감정 원칙, 콘티, 배치도, 얼굴 시트, 오브젝트 시트(REF), 인수인계, 제작 로그. */
  static final List<String> REFERENCE_TOKENS =
      List.of("PRINCIPLES", "CONTI", "BLOCKING_MAP", "FACE_SHEET", "_REF", "HANDOFF", "PRODUCTION_LOG");

  /** S11 r1~r10 시험 테이크·실패 예(r11 이상은 해당 없음). */
  static final Pattern S11_TRIAL = Pattern.compile("^EP01_V\\d+_(GPT_)?S11_?R(10|[1-9])(?!\\d).*");

  private static final Set<String> ACTIVE_JOB_STATES = Set.of("WAITING", "WORKING", "REVIEW");

  private final JdbcTemplate db;
  private final StudioService studio;
  private final FileStorage storage;

  /**
   * DB 조회 없이 판정할 수 있는 삭제 금지 사유. 없으면 null.
   *
   * @param r id, item_key, origin, kind, work_status, head_id, approved_id
   */
  static String protection(Map<String, Object> r) {
    return protection(r, false);
  }

  /**
   * @param wholeItem 감독이 지정한 작업 ID 전체 보관(ITEM)이면 최신본 보호만 예외로 푼다. 승인본·원본·진행 중 JOB·기준
   *     자료는 그대로 막는다.
   */
  static String protection(Map<String, Object> r, boolean wholeItem) {
    String id = str(r.get("id"));
    String key = str(r.get("item_key"));
    if (!wholeItem && id.equals(str(r.get("head_id")))) return "최신본(head)";
    if (id.equals(str(r.get("approved_id")))) return "승인본";
    if (key.toLowerCase(Locale.ROOT).startsWith("source-") || "ORIGINAL".equals(r.get("origin")))
      return "원본";
    if ("JOB".equals(r.get("kind")) && ACTIVE_JOB_STATES.contains(str(r.get("work_status"))))
      return "진행 중인 JOB 요청서";
    if (key.equals("project-memory")) return "현재 쓰는 기준 자료";
    String upper = key.toUpperCase(Locale.ROOT);
    for (String token : REFERENCE_TOKENS)
      if (upper.contains(token)) return "현재 쓰는 기준 자료";
    return null;
  }

  /** 폐지 후보 사유. 후보가 아니면 null. */
  static String candidateReason(Map<String, Object> r) {
    boolean older = !str(r.get("id")).equals(str(r.get("head_id")));
    if (older && "NEEDS_CHANGES".equals(r.get("ai_verdict")))
      return "최신본보다 앞선 버전 · AI 검수 NEEDS_CHANGES";
    if (older && Set.of("REJECT", "REQUEST_CHANGES").contains(str(r.get("human_review"))))
      return "최신본보다 앞선 버전 · 감독 " + ("REJECT".equals(r.get("human_review")) ? "반려" : "수정 요청");
    if (older && "FAILED".equals(r.get("work_status"))) return "최신본보다 앞선 버전 · 작업 실패(FAILED)";
    if (S11_TRIAL.matcher(str(r.get("item_key"))).matches()) return "S11 r1~r10 시험·실패 자료";
    return null;
  }

  /** 후보 목록(감독 보고용). 보관 대상과, 기준에는 맞지만 자동으로 막힌 목록을 함께 돌려준다. */
  public Map<String, Object> candidates(AuthenticatedPrincipal p) {
    studio.require(p, false, false);
    guardProject();
    var candidates = new ArrayList<Map<String, Object>>();
    var blocked = new ArrayList<Map<String, Object>>();
    var items = new LinkedHashMap<String, Map<String, Object>>();
    long freeable = 0;
    for (var row : rows("r.archived_at IS NULL")) {
      String reason = candidateReason(row);
      if (reason == null) continue;
      String block = blockReason(row);
      var out = summary(row);
      out.put("reason", reason);
      if (block != null) {
        out.put("blockedBy", block);
        blocked.add(out);
        // 최신본 보호에만 걸렸으면 '작업 ID 전체 보관' 후보로 감독께 따로 보여 준다.
        String key = (String) row.get("item_key");
        if (block.equals("최신본(head)") && !items.containsKey(key)) {
          var item = itemCheck(key);
          if (item.get("blockedBy") == null) {
            item.put("reason", reason);
            items.put(key, item);
          }
        }
        continue;
      }
      boolean shared = fileShared(row);
      out.put("fileShared", shared);
      if (!shared) freeable += size(row);
      candidates.add(out);
    }
    return Map.of(
        "projectId", studio.projectId(),
        "holdDays", HOLD_DAYS,
        "candidates", candidates,
        "blocked", blocked,
        "itemCandidates", new ArrayList<>(items.values()),
        "freeableBytes", freeable,
        "note", "보관·삭제는 감독이 이 목록을 확인한 뒤 고른 버전·작업 ID만 실행합니다.");
  }

  /** 보관함: 보관한 버전과 영구 삭제 가능 시각. */
  public List<Map<String, Object>> archived(AuthenticatedPrincipal p) {
    studio.require(p, false, false);
    guardProject();
    var out = new ArrayList<Map<String, Object>>();
    for (var row : rows("r.archived_at IS NOT NULL")) {
      var s = summary(row);
      s.put("archivedAt", String.valueOf(row.get("archived_at")));
      s.put("archivedBy", row.get("archived_by"));
      s.put("archiveReason", row.get("archive_reason"));
      s.put("archiveScope", row.get("archive_scope"));
      s.put("purgeableAt", String.valueOf(row.get("purgeable_at")));
      s.put("purgeable", truthy(row.get("purgeable")));
      out.add(s);
    }
    return out;
  }

  public List<Map<String, Object>> log(AuthenticatedPrincipal p) {
    studio.require(p, false, false);
    return db.queryForList(
        "SELECT * FROM studio_cleanup_log WHERE project_id=? ORDER BY id DESC LIMIT 500",
        studio.projectId());
  }

  @Transactional
  public Map<String, Object> archive(AuthenticatedPrincipal p, List<String> ids, String note) {
    begin(p, ids, note);
    var done = new ArrayList<String>();
    var skipped = new ArrayList<Map<String, Object>>();
    for (String id : ids) {
      var row = one(id);
      String why =
          row == null
              ? "버전이 없습니다"
              : row.get("archived_at") != null ? "이미 보관됨" : blockReason(row);
      if (why != null) {
        skipped.add(Map.of("revisionId", id, "reason", why));
        continue;
      }
      String reason = Optional.ofNullable(candidateReason(row)).orElse("감독 지정");
      markArchived(p, id, reason, "VERSION");
      writeLog(p, "ARCHIVE", row, 0, reason, note);
      done.add(id);
    }
    return Map.of("archived", done, "skipped", skipped);
  }

  /**
   * 감독이 지정한 작업 ID 전체 보관(최신본 포함). 최신본 보호만 예외이고, 버전 하나라도 다른 이유(승인본·원본·진행 중 JOB·기준 자료·
   * 참조)로 막히면 그 작업 ID는 통째로 건너뛴다.
   */
  @Transactional
  public Map<String, Object> archiveItems(AuthenticatedPrincipal p, List<String> workIds, String note) {
    begin(p, workIds, note);
    var done = new ArrayList<Map<String, Object>>();
    var skipped = new ArrayList<Map<String, Object>>();
    for (String key : workIds) {
      var check = key == null || !key.matches("[a-zA-Z0-9_-]{1,100}") ? null : itemCheck(key);
      if (check == null || ((Number) check.get("versions")).intValue() == 0) {
        skipped.add(Map.of("workId", String.valueOf(key), "reason", "보관할 버전이 없습니다"));
        continue;
      }
      if (check.get("blockedBy") != null) {
        skipped.add(Map.of("workId", key, "reason", (String) check.get("blockedBy")));
        continue;
      }
      var ids = new ArrayList<String>();
      for (var row : itemRows(key)) {
        String reason =
            "감독 지정 작업 ID 전체 보관"
                + Optional.ofNullable(candidateReason(row)).map(r -> " · " + r).orElse("");
        markArchived(p, (String) row.get("id"), reason, "ITEM");
        writeLog(p, "ARCHIVE", row, 0, reason, note);
        ids.add((String) row.get("id"));
      }
      done.add(Map.of("workId", key, "revisionIds", ids));
    }
    return Map.of("archived", done, "skipped", skipped);
  }

  @Transactional
  public Map<String, Object> restore(AuthenticatedPrincipal p, List<String> ids, String note) {
    begin(p, ids, note);
    var done = new ArrayList<String>();
    var skipped = new ArrayList<Map<String, Object>>();
    for (String id : ids) {
      var row = one(id);
      if (row == null || row.get("archived_at") == null) {
        skipped.add(Map.of("revisionId", id, "reason", "보관함에 없습니다"));
        continue;
      }
      db.update(
          "UPDATE studio_revisions SET archived_at=NULL,archived_by=NULL,archive_reason=NULL,"
              + "archive_scope=NULL"
              + " WHERE project_id=? AND id=?",
          studio.projectId(), id);
      writeLog(p, "RESTORE", row, 0, "되살림", note);
      done.add(id);
    }
    return Map.of("restored", done, "skipped", skipped);
  }

  /** 영구 삭제. 보관 {@value #HOLD_DAYS}일이 지나고 여전히 보호 대상이 아닌 버전만 지운다. */
  @Transactional
  public Map<String, Object> purge(AuthenticatedPrincipal p, List<String> ids, String note) {
    begin(p, ids, note);
    var done = new ArrayList<Map<String, Object>>();
    var skipped = new ArrayList<Map<String, Object>>();
    long freed = 0;
    for (String id : ids) {
      var row = one(id);
      String why =
          row == null
              ? "버전이 없습니다"
              : row.get("archived_at") == null
                  ? "보관하지 않은 버전입니다(먼저 보관)"
                  : !truthy(row.get("purgeable"))
                      ? "보관 " + HOLD_DAYS + "일이 지나지 않았습니다(" + row.get("purgeable_at") + " 이후)"
                      : blockReason(row, "ITEM".equals(row.get("archive_scope")));
      if (why != null) {
        skipped.add(Map.of("revisionId", id, "reason", why));
        continue;
      }
      String fileId = (String) row.get("file_id");
      boolean deleteFile = fileId != null && !fileShared(row);
      db.update("DELETE FROM studio_reviews WHERE project_id=? AND revision_id=?", studio.projectId(), id);
      // 다음 버전의 부모를 지운 버전의 부모로 잇는다(버전 사슬 유지).
      db.update(
          "UPDATE studio_revisions SET parent_id=? WHERE project_id=? AND parent_id=?",
          row.get("parent_id"), studio.projectId(), id);
      // 작업 ID 전체 보관(ITEM)에서 최신본을 지우면 head는 그 부모(역시 보관됨)로 옮긴다.
      db.update(
          "UPDATE studio_items SET head_id=? WHERE project_id=? AND item_key=? AND head_id=?",
          row.get("parent_id"), studio.projectId(), row.get("item_key"), id);
      db.update("DELETE FROM studio_revisions WHERE project_id=? AND id=?", studio.projectId(), id);
      Long left =
          db.queryForObject(
              "SELECT COUNT(*) FROM studio_revisions WHERE project_id=? AND item_key=?",
              Long.class, studio.projectId(), row.get("item_key"));
      if (left != null && left == 0)
        db.update(
            "DELETE FROM studio_items WHERE project_id=? AND item_key=? AND approved_id IS NULL",
            studio.projectId(), row.get("item_key"));
      long bytes = 0;
      if (deleteFile) {
        var file =
            db.queryForList(
                "SELECT storage_key,size_bytes FROM studio_files WHERE project_id=? AND id=?",
                studio.projectId(), fileId);
        if (!file.isEmpty()) {
          bytes = ((Number) file.getFirst().get("size_bytes")).longValue();
          String key = (String) file.getFirst().get("storage_key");
          db.update("DELETE FROM studio_files WHERE project_id=? AND id=?", studio.projectId(), fileId);
          // 저장소 파일은 DB 커밋 뒤에만 지운다. 롤백되면 파일은 그대로 남는다.
          TransactionalFileDeletion.afterCommit(() -> deleteStored(fileId, key));
        }
      }
      writeLog(p, "PURGE", row, bytes, str(row.get("archive_reason")), note);
      freed += bytes;
      done.add(Map.of("revisionId", id, "fileDeleted", bytes > 0, "bytesFreed", bytes));
    }
    return Map.of("purged", done, "skipped", skipped, "bytesFreed", freed);
  }

  // ---------------------------------------------------------------------------------------------

  /** 자동 삭제 금지 판정(보관·삭제 공통). 없으면 null. */
  String blockReason(Map<String, Object> row) {
    return blockReason(row, false);
  }

  String blockReason(Map<String, Object> row, boolean wholeItem) {
    String fixed = protection(row, wholeItem);
    if (fixed != null) return fixed;
    String id = (String) row.get("id");
    Long tasks =
        db.queryForObject(
            "SELECT COUNT(*) FROM studio_tasks t WHERE t.project_id=? AND (t.revision_id=? OR"
                + " t.result_revision_id=? OR t.review_id IN (SELECT v.id FROM studio_reviews v"
                + " WHERE v.project_id=t.project_id AND v.revision_id=?))",
            Long.class, studio.projectId(), id, id, id);
    if (tasks != null && tasks > 0) return "수정 작업이 참조함";
    if (mentioned(id, id, wholeItem ? (String) row.get("item_key") : null))
      return "다른 자료가 이 버전을 참조함";
    return null;
  }

  /** 작업 ID 전체 보관이 가능한지: 보관 안 된 모든 버전이 최신본 예외만으로 통과해야 한다. */
  private Map<String, Object> itemCheck(String key) {
    var rows = itemRows(key);
    var out = new LinkedHashMap<String, Object>();
    long bytes = 0;
    String blocked = null;
    for (var row : rows) {
      String why = blockReason(row, true);
      if (why != null && blocked == null) blocked = row.get("label") + ": " + why;
      bytes += size(row);
    }
    out.put("workId", key);
    out.put("versions", rows.size());
    out.put("sizeBytes", bytes);
    out.put("kind", rows.isEmpty() ? null : rows.getFirst().get("kind"));
    out.put("blockedBy", blocked);
    return out;
  }

  private List<Map<String, Object>> itemRows(String key) {
    return db.queryForList(
        SELECT + " AND r.item_key=? AND r.archived_at IS NULL ORDER BY r.created_at,r.id",
        studio.projectId(), key);
  }

  private void markArchived(AuthenticatedPrincipal p, String id, String reason, String scope) {
    db.update(
        "UPDATE studio_revisions SET archived_at=CURRENT_TIMESTAMP,archived_by=?,archive_reason=?,"
            + "archive_scope=? WHERE project_id=? AND id=? AND archived_at IS NULL",
        p.userId(), cut(reason, 300), scope, studio.projectId(), id);
  }

  /** 파일을 다른 버전이 쓰거나 다른 자료 본문이 fileId를 언급하면 파일은 남긴다. */
  boolean fileShared(Map<String, Object> row) {
    String fileId = (String) row.get("file_id");
    if (fileId == null) return false;
    Long others =
        db.queryForObject(
            "SELECT COUNT(*) FROM studio_revisions WHERE project_id=? AND file_id=? AND id<>?",
            Long.class, studio.projectId(), fileId, row.get("id"));
    return (others != null && others > 0) || mentioned(fileId, (String) row.get("id"), null);
  }

  /** 다른 자료 본문이 needle을 언급하는지. sameItem이 있으면 같은 작업 ID 안의 언급은 셈하지 않는다(전체 보관). */
  private boolean mentioned(String needle, String self, String sameItem) {
    Long n =
        db.queryForObject(
            "SELECT COUNT(*) FROM studio_revisions WHERE project_id=? AND id<>? AND item_key<>? AND"
                + " (LOCATE(?,content)>0 OR LOCATE(?,dialogue)>0 OR LOCATE(?,direction)>0)",
            Long.class, studio.projectId(), self, sameItem == null ? "" : sameItem, needle, needle,
            needle);
    return n != null && n > 0;
  }

  private void begin(AuthenticatedPrincipal p, List<String> ids, String note) {
    if (p == null || !"ADMIN".equals(p.role())) throw StudioService.error(403, "보관·삭제는 감독(관리자)만 합니다.");
    // 프로젝트 잠금 + 보관(PROJECT_ARCHIVE)된 프로젝트 쓰기 차단.
    studio.require(p, true, true);
    guardProject();
    if (ids == null || ids.isEmpty() || ids.size() > MAX_BATCH)
      throw StudioService.error(400, "버전·작업 ID를 1~" + MAX_BATCH + "개 보내세요.");
    if (new HashSet<>(ids).size() != ids.size()) throw StudioService.error(400, "같은 ID가 중복되었습니다.");
    if (note == null || note.isBlank() || note.length() > 500)
      throw StudioService.error(400, "감독 확인 메모(1~500자)를 적으세요.");
  }

  /** 다른 서비스가 Studio 표를 함께 쓰는 프로젝트는 정리 대상에서 뺀다. */
  private void guardProject() {
    String project = studio.projectId().toLowerCase(Locale.ROOT);
    if (project.startsWith("naesin-")
        || project.equals(StudentRosterController.PROJECT)
        || project.equals("academy-relocation"))
      throw StudioService.error(400, "이 프로젝트는 폐지 자료 정리 대상이 아닙니다.");
  }

  private static final String SELECT =
      "SELECT r.id,r.item_key,r.label,r.parent_id,r.kind,r.origin,r.work_status,r.file_id,r.created_at,"
          + "r.archived_at,r.archived_by,r.archive_reason,r.archive_scope,i.head_id,i.approved_id,f.size_bytes,f.filename,"
          + "(r.archived_at + INTERVAL "
          + HOLD_DAYS
          + " DAY) AS purgeable_at,"
          + "(r.archived_at IS NOT NULL AND r.archived_at <= CURRENT_TIMESTAMP - INTERVAL "
          + HOLD_DAYS
          + " DAY) AS purgeable,"
          + "(SELECT v.verdict FROM studio_reviews v WHERE v.project_id=r.project_id AND"
          + " v.revision_id=r.id ORDER BY v.created_at DESC,v.id DESC LIMIT 1) AS ai_verdict,"
          + "(SELECT e.action FROM studio_events e WHERE e.project_id=r.project_id AND e.revision_id=r.id"
          + " AND e.action IN ('APPROVE','REJECT','REQUEST_CHANGES','WITHDRAW_APPROVAL') ORDER BY e.id"
          + " DESC LIMIT 1) AS human_review"
          + " FROM studio_revisions r JOIN studio_items i ON i.project_id=r.project_id AND"
          + " i.item_key=r.item_key LEFT JOIN studio_files f ON f.project_id=r.project_id AND"
          + " f.id=r.file_id WHERE r.project_id=?";

  private List<Map<String, Object>> rows(String condition) {
    return db.queryForList(
        SELECT + " AND " + condition + " ORDER BY r.item_key,r.created_at,r.id", studio.projectId());
  }

  private Map<String, Object> one(String id) {
    if (id == null || !id.matches("[A-Za-z0-9-]{1,36}")) return null;
    var found = db.queryForList(SELECT + " AND r.id=? FOR UPDATE", studio.projectId(), id);
    return found.isEmpty() ? null : found.getFirst();
  }

  private Map<String, Object> summary(Map<String, Object> row) {
    var out = new LinkedHashMap<String, Object>();
    out.put("revisionId", row.get("id"));
    out.put("workId", row.get("item_key"));
    out.put("version", row.get("label"));
    out.put("kind", row.get("kind"));
    out.put("filename", row.get("filename"));
    out.put("sizeBytes", size(row));
    out.put("createdAt", String.valueOf(row.get("created_at")));
    return out;
  }

  private void writeLog(
      AuthenticatedPrincipal p, String action, Map<String, Object> row, long bytes, String reason, String note) {
    db.update(
        "INSERT INTO studio_cleanup_log(project_id,action,revision_id,item_key,label,file_id,bytes_freed,"
            + "reason,note,actor_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
        studio.projectId(), action, row.get("id"), row.get("item_key"), row.get("label"),
        row.get("file_id"), bytes, cut(reason, 300), cut(note.strip(), 500), p.userId());
    db.update(
        "INSERT INTO studio_events(project_id,revision_id,action,note,actor_id) VALUES (?,?,?,?,?)",
        studio.projectId(), row.get("id"), "CLEANUP_" + action,
        cut(row.get("item_key") + " " + row.get("label") + " · " + note.strip(), 2000), p.userId());
  }

  private void deleteStored(String fileId, String key) {
    try {
      if (storage.deleteReported(key, "UNKNOWN")) return;
    } catch (RuntimeException ignored) {
      // DB는 이미 커밋됐다. 저장소 실패는 기록만 하고 다음 정리에서 다시 확인한다.
    }
    org.slf4j.LoggerFactory.getLogger(StudioCleanupService.class)
        .warn("Studio purge storage delete failed; fileId={}", fileId);
  }

  private static long size(Map<String, Object> row) {
    return row.get("size_bytes") instanceof Number n ? n.longValue() : 0L;
  }

  private static boolean truthy(Object v) {
    return v instanceof Boolean b ? b : v instanceof Number n && n.intValue() != 0;
  }

  private static String str(Object v) {
    return v == null ? "" : v.toString();
  }

  private static String cut(String v, int max) {
    return v.length() <= max ? v : v.substring(0, max);
  }
}
