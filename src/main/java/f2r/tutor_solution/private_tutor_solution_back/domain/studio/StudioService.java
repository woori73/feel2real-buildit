package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class StudioService {
  public static final String PROJECT = "ai-agent-part1";
  public static final long MAX_FILE = 100L * 1024 * 1024;
  private final JdbcTemplate db;
  private final FileStorage storage;

  public record Revision(
      String itemKey,
      String parentId,
      String label,
      String kind,
      Integer episode,
      Integer cutNumber,
      String origin,
      String title,
      String content,
      String dialogue,
      String direction,
      String workStatus,
      String fileId) {}

  public String permission(AuthenticatedPrincipal p) {
    if (p == null) throw error(401, "로그인이 필요합니다.");
    if ("ADMIN".equals(p.role())) return "APPROVER";
    return db
        .query(
            "SELECT permission FROM studio_members WHERE project_id=? AND user_id=?",
            (rs, n) -> rs.getString(1),
            PROJECT,
            p.userId())
        .stream()
        .findFirst()
        .orElseThrow(() -> error(403, "허가된 제작 담당자만 접근할 수 있습니다."));
  }

  public void require(AuthenticatedPrincipal p, boolean edit, boolean approve) {
    String role = permission(p);
    if ((edit || approve)
        && db.queryForObject("SELECT COUNT(*) FROM studio_projects WHERE id=?", Long.class, PROJECT)
            == 0) throw error(409, "먼저 프로젝트를 개설하세요.");
    if ((edit && role.equals("VIEWER")) || (approve && !role.equals("APPROVER")))
      throw error(403, "이 작업 권한이 없습니다.");
  }

  @Transactional
  public Map<String, Object> initialize(AuthenticatedPrincipal p) {
    if (p == null || !"ADMIN".equals(p.role())) throw error(403, "관리자가 프로젝트를 개설해야 합니다.");
    if (db.queryForObject("SELECT COUNT(*) FROM studio_projects WHERE id=?", Long.class, PROJECT)
        == 0)
      db.update("INSERT INTO studio_projects(id,title) VALUES (?,?)", PROJECT, "《AI AGENT》 1부");
    return overview(p);
  }

  public Map<String, Object> overview(AuthenticatedPrincipal p) {
    String permission = permission(p);
    return Map.of(
        "projectId",
        PROJECT,
        "title",
        "《AI AGENT》 1부",
        "episodes",
        14,
        "ep01Cuts",
        68,
        "permission",
        permission,
        "initialized",
        db.queryForObject("SELECT COUNT(*) FROM studio_projects WHERE id=?", Long.class, PROJECT)
            > 0,
        "files",
        db.queryForObject(
            "SELECT COUNT(*) FROM studio_files WHERE project_id=?", Long.class, PROJECT),
        "policy",
        "실제 자료 미등록 항목은 업로드 대기입니다. AI 제안은 명시적 사용자 승인 후에만 승인본이 됩니다.");
  }

  @Transactional
  public void member(AuthenticatedPrincipal p, long userId, String permission) {
    if (p == null || !"ADMIN".equals(p.role())) throw error(403, "관리자 전용입니다.");
    if (!Set.of("VIEWER", "EDITOR", "APPROVER", "REVOKE").contains(permission))
      throw error(400, "권한 값 오류");
    if (db.queryForObject("SELECT COUNT(*) FROM users WHERE user_id=?", Long.class, userId) == 0)
      throw error(400, "존재하는 회원 사용자 ID를 입력하세요.");
    db.update("DELETE FROM studio_members WHERE project_id=? AND user_id=?", PROJECT, userId);
    if (!permission.equals("REVOKE"))
      db.update("INSERT INTO studio_members VALUES (?,?,?)", PROJECT, userId, permission);
    event(null, "MEMBER", userId + ":" + permission, p.userId());
  }

  public List<Map<String, Object>> members(AuthenticatedPrincipal p) {
    if (p == null || !"ADMIN".equals(p.role())) throw error(403, "관리자 전용입니다.");
    return db.queryForList(
        "SELECT user_id,permission FROM studio_members WHERE project_id=?", PROJECT);
  }

  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> upload(AuthenticatedPrincipal p, MultipartFile file)
      throws IOException {
    require(p, true, false);
    if (file == null || file.isEmpty() || file.getSize() > MAX_FILE)
      throw error(413, "파일은 1 byte~100 MiB까지 가능합니다.");
    String name =
        Optional.ofNullable(file.getOriginalFilename())
            .orElse("file")
            .replaceAll("[\\\\/\\p{Cntrl}]", "_");
    if (name.length() > 255) throw error(400, "파일명이 너무 깁니다.");
    String ext =
        name.contains(".")
            ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
            : "";
    if (!Set.of("pdf", "html", "json", "md", "md5", "zip", "png", "mp4").contains(ext))
      throw error(400, "지원하지 않는 파일 형식입니다.");
    Path tmp = Files.createTempFile("f2r-studio-", ".upload");
    String createdKey = null;
    String createdId = null;
    try {
      MessageDigest digest = digest();
      long count = 0;
      try (InputStream in = file.getInputStream();
          OutputStream out = Files.newOutputStream(tmp)) {
        byte[] b = new byte[65536];
        int n;
        while ((n = in.read(b)) != -1) {
          count += n;
          if (count > MAX_FILE) throw error(413, "파일 크기 초과");
          digest.update(b, 0, n);
          out.write(b, 0, n);
        }
      }
      validateType(tmp, ext);
      String sha = HexFormat.of().formatHex(digest.digest());
      // Serialize project uploads to avoid duplicate objects during concurrent SHA checks.
      db.queryForList("SELECT id FROM studio_projects WHERE id=? FOR UPDATE", PROJECT);
      var duplicate =
          db.queryForList(
              "SELECT id,sha256,filename,size_bytes FROM studio_files WHERE project_id=? AND"
                  + " sha256=?",
              PROJECT,
              sha);
      if (!duplicate.isEmpty()) return Map.of("file", duplicate.getFirst(), "duplicate", true);
      String id = UUID.randomUUID().toString(), key;
      try (InputStream in = Files.newInputStream(tmp)) {
        key = storage.saveRaw(p.userId(), "studio-" + id + "." + ext, in, count);
        createdKey = key;
        createdId = id;
        // Register before any SQL write (and before stream close can fail). Duplicate SHA paths
        // return above and never register an existing original for deletion.
        f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage
            .TransactionalFileDeletion.onRollback(() -> cleanupNewUpload(id, key));
      }
      db.update(
          "INSERT INTO"
              + " studio_files(id,project_id,sha256,storage_key,filename,extension,size_bytes,uploaded_by)"
              + " VALUES (?,?,?,?,?,?,?,?)",
          id,
          PROJECT,
          sha,
          key,
          name,
          ext,
          count,
          p.userId());
      event(null, "UPLOAD", id, p.userId());
      return Map.of(
          "file",
          Map.of("id", id, "sha256", sha, "filename", name, "size_bytes", count),
          "duplicate",
          false);
    } catch (IOException | RuntimeException failure) {
      // Direct, non-proxied unit calls have no transaction callback. Production uses the hook.
      if (createdKey != null
          && !org.springframework.transaction.support.TransactionSynchronizationManager
              .isSynchronizationActive()) cleanupNewUpload(createdId, createdKey);
      throw failure;
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  private void cleanupNewUpload(String id, String key) {
    try {
      if (storage.deleteReported(key, "UNKNOWN")) return;
    } catch (RuntimeException ignored) {
      // Cleanup failure must not hide the DB error or prevent other rollback hooks from running.
    }
    org.slf4j.LoggerFactory.getLogger(StudioService.class)
        .warn("Studio new-upload rollback cleanup failed; fileId={}", id);
  }

  static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static void validateType(Path path, String ext) throws IOException {
    byte[] b;
    try (var in = Files.newInputStream(path)) {
      b = in.readNBytes(16);
    }
    boolean ok =
        switch (ext) {
          case "pdf" -> new String(b, StandardCharsets.ISO_8859_1).startsWith("%PDF-");
          case "png" ->
              b.length >= 8
                  && Arrays.equals(
                      Arrays.copyOf(b, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
          case "zip" ->
              b.length >= 4 && b[0] == 80 && b[1] == 75 && (b[2] == 3 || b[2] == 5 || b[2] == 7);
          case "mp4" ->
              b.length >= 12 && new String(b, 4, 4, StandardCharsets.ISO_8859_1).equals("ftyp");
          default -> true;
        };
    if (!ok) throw error(400, "확장자와 파일 형식이 일치하지 않습니다.");
    if (Set.of("html", "json", "md", "md5").contains(ext)) {
      // Strict UTF-8, streamed validation. Never execute HTML or unpack ZIP archives.
      try (var reader =
          new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8.newDecoder())) {
        char[] chars = new char[8192];
        int n;
        while ((n = reader.read(chars)) != -1)
          for (int i = 0; i < n; i++) if (chars[i] == 0) throw error(400, "텍스트 파일에 NUL이 있습니다.");
      } catch (java.nio.charset.CharacterCodingException e) {
        throw error(400, "UTF-8 텍스트가 필요합니다.");
      }
    }
    if (ext.equals("json")) {
      // 원본은 바꾸지 않고 문법만 확인한다. 깨진 JSON은 일괄 등록·열람 때 늦게 실패하므로 업로드에서 막는다.
      try (var in = Files.newInputStream(path)) {
        var tree = JSON_SYNTAX.readTree(in);
        if (tree == null || tree.isMissingNode()) throw error(400, "JSON 값이 필요합니다.");
      } catch (RuntimeException e) {
        throw error(400, "JSON 문법 오류입니다. 원본을 확인하세요.");
      }
    }
  }

  private static final tools.jackson.databind.ObjectMapper JSON_SYNTAX =
      tools.jackson.databind.json.JsonMapper.builder()
          .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  @Transactional
  public Map<String, Object> save(AuthenticatedPrincipal p, Revision r) {
    require(p, true, false);
    validate(r);
    db.queryForList("SELECT id FROM studio_projects WHERE id=? FOR UPDATE", PROJECT);
    var items =
        db.queryForList(
            "SELECT head_id FROM studio_items WHERE project_id=? AND item_key=?",
            PROJECT,
            r.itemKey());
    String head = items.isEmpty() ? null : (String) items.getFirst().get("head_id");
    if (!Objects.equals(head, blankNull(r.parentId())))
      throw error(409, "새 버전이 있습니다. 다시 불러온 후 저장하세요.");
    if (r.fileId() != null && !r.fileId().isBlank()) file(p, r.fileId());
    if (db.queryForObject(
            "SELECT COUNT(*) FROM studio_revisions WHERE project_id=? AND item_key=? AND label=?",
            Long.class,
            PROJECT,
            r.itemKey(),
            r.label())
        > 0) throw error(409, "이 자료에 같은 버전 라벨이 있습니다. 새 라벨을 입력하세요.");
    if (items.isEmpty())
      db.update("INSERT INTO studio_items(project_id,item_key) VALUES (?,?)", PROJECT, r.itemKey());
    if (!items.isEmpty()) {
      var prev = revision(p, head);
      if (!Objects.equals(prev.get("kind"), r.kind())
          || !Objects.equals(prev.get("episode"), r.episode())
          || !Objects.equals(prev.get("cut_number"), r.cutNumber()))
        throw error(400, "자료 종류·회차·컷은 버전 사이에 변경할 수 없습니다.");
    }
    String id = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO"
            + " studio_revisions(id,project_id,item_key,parent_id,label,kind,episode,cut_number,origin,title,content,dialogue,direction,work_status,file_id,created_by)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        PROJECT,
        r.itemKey(),
        head,
        r.label(),
        r.kind(),
        r.episode(),
        r.cutNumber(),
        r.origin(),
        r.title(),
        text(r.content()),
        text(r.dialogue()),
        text(r.direction()),
        r.workStatus(),
        blankNull(r.fileId()),
        p.userId());
    db.update(
        "UPDATE studio_items SET head_id=? WHERE project_id=? AND item_key=?",
        id,
        PROJECT,
        r.itemKey());
    event(id, "REVISION", "", p.userId());
    return revision(p, id);
  }

  static String text(String v) {
    return v == null ? "" : v;
  }

  static String blankNull(String v) {
    return v == null || v.isBlank() ? null : v;
  }

  static void validate(Revision r) {
    if (r == null
        || r.itemKey() == null
        || !r.itemKey().matches("[a-zA-Z0-9_-]{1,100}")
        || r.label() == null
        || !r.label().matches("[a-zA-Z0-9._-]{1,40}")) throw error(400, "자료 키와 버전 라벨을 확인하세요.");
    if (!Set.of("SCRIPT", "STORYBOARD", "CHARACTER", "VIDEO", "JOB", "REVIEW")
            .contains(text(r.kind()))
        || !Set.of("ORIGINAL", "AI_PROPOSAL", "USER_EDIT").contains(text(r.origin()))
        || !Set.of("WAITING", "WORKING", "READY", "FAILED", "REVIEW")
            .contains(text(r.workStatus()))) throw error(400, "자료 유형·출처·작업 상태 오류");
    if (r.episode() != null && (r.episode() < 1 || r.episode() > 14))
      throw error(400, "1~14화만 가능합니다.");
    if (r.cutNumber() != null
        && (r.episode() == null
            || r.cutNumber() < 1
            || r.cutNumber() > (r.episode() == 1 ? 68 : 999))) throw error(400, "컷 번호 오류");
    if (r.title() == null
        || r.title().isBlank()
        || r.title().length() > 150
        || text(r.content()).length() > 200000
        || text(r.dialogue()).length() > 10000
        || text(r.direction()).length() > 10000) throw error(400, "제목 또는 본문 길이 오류");
  }

  @Transactional
  public Map<String, Object> restore(
      AuthenticatedPrincipal p, String sourceId, String expectedHead, String label) {
    require(p, true, false);
    var source = revision(p, sourceId);
    var restored =
        save(
            p,
            new Revision(
                (String) source.get("item_key"),
                expectedHead,
                label,
                (String) source.get("kind"),
                (Integer) source.get("episode"),
                (Integer) source.get("cut_number"),
                "USER_EDIT",
                (String) source.get("title"),
                (String) source.get("content"),
                (String) source.get("dialogue"),
                (String) source.get("direction"),
                "REVIEW",
                (String) source.get("file_id")));
    event((String) restored.get("id"), "RESTORE", "source=" + sourceId, p.userId());
    return restored;
  }

  public List<Map<String, Object>> cuts(AuthenticatedPrincipal p, int episode) {
    require(p, false, false);
    if (episode < 1 || episode > 14) throw error(400, "회차를 확인하세요.");
    var rows =
        db.queryForList(
            "SELECT r.*,i.head_id,i.approved_id FROM studio_items i JOIN studio_revisions r ON"
                + " r.id=i.head_id AND r.project_id=i.project_id WHERE i.project_id=? AND"
                + " r.kind='STORYBOARD' AND r.episode=? AND r.cut_number IS NOT NULL ORDER BY"
                + " r.cut_number,r.created_at DESC,r.id DESC",
            PROJECT,
            episode);
    var byCut = new LinkedHashMap<Object, Map<String, Object>>();
    for (var row : rows) byCut.putIfAbsent(row.get("cut_number"), row);
    return new ArrayList<>(byCut.values());
  }

  public List<Map<String, Object>> revisions(
      AuthenticatedPrincipal p, Integer episode, String kind, String query) {
    return revisions(p, episode, kind, query, 200, 0);
  }

  public List<Map<String, Object>> revisions(
      AuthenticatedPrincipal p, Integer episode, String kind, String query, int limit, int offset) {
    if (limit < 1 || limit > 200 || offset < 0 || offset > 1000000)
      throw error(400, "페이지 범위를 확인하세요.");
    require(p, false, false);
    String sql =
        "SELECT r.*,i.head_id,i.approved_id,f.filename,f.sha256 FROM studio_revisions r JOIN"
            + " studio_items i ON i.project_id=r.project_id AND i.item_key=r.item_key LEFT JOIN"
            + " studio_files f ON f.id=r.file_id WHERE r.project_id=?";
    List<Object> args = new ArrayList<>(List.of(PROJECT));
    if (episode != null) {
      sql += " AND r.episode=?";
      args.add(episode);
    }
    if (kind != null && !kind.isBlank()) {
      sql += " AND r.kind=?";
      args.add(kind);
    }
    if (query != null && !query.isBlank()) {
      if (query.length() > 200) throw error(400, "검색어는 200자 이하입니다.");
      sql +=
          " AND (LOCATE(?,r.title)>0 OR LOCATE(?,r.content)>0 OR LOCATE(?,r.dialogue)>0 OR"
              + " LOCATE(?,r.direction)>0)";
      args.addAll(Collections.nCopies(4, query));
    }
    args.add(limit);
    args.add(offset);
    return db.queryForList(
        sql + " ORDER BY r.created_at DESC,r.id LIMIT ? OFFSET ?", args.toArray());
  }

  public Map<String, Object> revision(AuthenticatedPrincipal p, String id) {
    require(p, false, false);
    return db
        .queryForList(
            "SELECT r.*,i.head_id,i.approved_id FROM studio_revisions r JOIN studio_items i ON"
                + " i.project_id=r.project_id AND i.item_key=r.item_key WHERE r.project_id=? AND"
                + " r.id=?",
            PROJECT,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> error(404, "자료가 없습니다."));
  }

  @Transactional
  public void review(AuthenticatedPrincipal p, String id, String action, String note) {
    require(p, false, true);
    if (!Set.of("APPROVE", "REJECT").contains(text(action)) || text(note).length() > 2000)
      throw error(400, "검수 값 오류");
    var revision = revision(p, id);
    db.queryForList(
        "SELECT item_key FROM studio_items WHERE project_id=? AND item_key=? FOR UPDATE",
        PROJECT,
        revision.get("item_key"));
    if (action.equals("APPROVE"))
      db.update(
          "UPDATE studio_items SET approved_id=? WHERE project_id=? AND item_key=?",
          id,
          PROJECT,
          revision.get("item_key"));
    else
      db.update(
          "UPDATE studio_items SET approved_id=NULL WHERE project_id=? AND item_key=? AND"
              + " approved_id=?",
          PROJECT,
          revision.get("item_key"),
          id);
    event(id, action, text(note), p.userId());
  }

  public List<Map<String, Object>> files(AuthenticatedPrincipal p) {
    return files(p, 200, 0);
  }

  public List<Map<String, Object>> files(AuthenticatedPrincipal p, int limit, int offset) {
    if (limit < 1 || limit > 200 || offset < 0 || offset > 1000000)
      throw error(400, "파일 페이지 범위를 확인하세요.");
    require(p, false, false);
    return db.queryForList(
        "SELECT id,filename,extension,sha256,size_bytes,uploaded_by,created_at FROM studio_files"
            + " WHERE project_id=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?",
        PROJECT,
        limit,
        offset);
  }

  public Map<String, Object> file(AuthenticatedPrincipal p, String id) {
    require(p, false, false);
    return db
        .queryForList("SELECT * FROM studio_files WHERE project_id=? AND id=?", PROJECT, id)
        .stream()
        .findFirst()
        .orElseThrow(() -> error(404, "파일이 없습니다."));
  }

  public byte[] bytes(AuthenticatedPrincipal p, String id) throws IOException {
    var f = file(p, id);
    return storage.readSingleLimited((String) f.get("storage_key"), "UNKNOWN", MAX_FILE);
  }

  public List<Map<String, Object>> events(AuthenticatedPrincipal p) {
    require(p, false, false);
    return db.queryForList(
        "SELECT * FROM studio_events WHERE project_id=? ORDER BY id DESC LIMIT 200", PROJECT);
  }

  private void event(String id, String action, String note, Long actor) {
    db.update(
        "INSERT INTO studio_events(project_id,revision_id,action,note,actor_id) VALUES (?,?,?,?,?)",
        PROJECT,
        id,
        action,
        note,
        actor);
  }

  static ResponseStatusException error(int code, String message) {
    return new ResponseStatusException(HttpStatus.valueOf(code), message);
  }
}
