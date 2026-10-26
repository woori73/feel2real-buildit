package f2r.tutor_solution.private_tutor_solution_back.domain.naesin;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.*;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.*;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

/** Educational adapter over the unchanged Studio revision/ACL/audit engine. No new schema. */
@Service
@RequiredArgsConstructor
public class NaesinProductionService {
  public static final Set<String> CHECKS = Set.of("QUESTION", "EXPLANATION", "FORMAT", "RIGHTS");
  private static final Set<String> FORMATS = Set.of("hwp", "hwpx", "pdf", "docx", "txt", "md", "html", "jpg", "jpeg", "png");
  private final StudioService studio;
  private final StudioProjectService projects;
  private final StudioAccessGuard access;
  private final JdbcTemplate db;
  private final FileStorage storage;
  private final DocumentUploadValidator validator;
  private final NaesinTextExtractor extractor;
  private final ObjectMapper json;

  public record Project(String title, String school, String grade, String examRange) {}
  public record Work(String itemKey, String parentId, String label, String category, String title,
      String ai, String goal, String prompt, String model, String tools, String result, String errors,
      String nextWork, String status, String fileId, List<String> sourceFileIds, List<String> referenceRevisionIds) {}
  public record Check(String dimension, boolean passed, String note) {}
  public record Decision(String action, String note) {}
  public record Publication(String expectedApprovedId) {}

  public void access(AuthenticatedPrincipal p) {
    if (p == null) throw error(401, "로그인이 필요합니다.");
    if (!access.isAllowed(p)) throw error(403, "내부 제작 담당자만 접근할 수 있습니다.");
  }
  public void select(AuthenticatedPrincipal p, String project) {
    access(p);
    if (project == null || !project.matches("naesin-[a-f0-9-]{36}")) throw error(400, "내신 제작 프로젝트를 선택하세요.");
    var attrs = RequestContextHolder.getRequestAttributes();
    if (!(attrs instanceof ServletRequestAttributes web)) throw new IllegalStateException("Request scope required");
    web.getRequest().setAttribute(StudioService.MCP_PROJECT_ATTRIBUTE, project);
    studio.require(p, false, false);
    if (db.queryForObject("SELECT COUNT(*) FROM studio_projects WHERE id=?", Long.class, project) != 1)
      throw error(404, "프로젝트가 없습니다.");
  }
  public Object list(AuthenticatedPrincipal p) {
    access(p);
    return db.queryForList("SELECT id,title,created_at FROM studio_projects WHERE id LIKE 'naesin-%' ORDER BY created_at DESC,id");
  }
  @Transactional
  public Object create(AuthenticatedPrincipal p, Project r) {
    access(p);
    if (!"ADMIN".equals(p.role())) throw error(403, "프로젝트 개설은 관리자 전용입니다.");
    if (r == null) throw error(400, "프로젝트 정보를 입력하세요.");
    String title = bounded(r.title(), 150, true), id = "naesin-" + UUID.randomUUID();
    if (!Set.of("중1", "중2", "중3").contains(Objects.toString(r.grade(), ""))) throw error(400, "이번 제작소는 중1·중2·중3만 지원합니다.");
    bounded(r.school(), 100, false); bounded(r.grade(), 40, false); bounded(r.examRange(), 1000, false);
    db.update("INSERT INTO studio_projects(id,title) VALUES (?,?)", id, title);
    select(p, id);
    studio.save(p, new StudioService.Revision("project-profile", null, "original", "JOB", null, null,
        "ORIGINAL", title, encode(r), "", "", "READY", null));
    event(null, "PROJECT_CREATE", "내신 제작 프로젝트", p);
    return Map.of("id", id, "title", title);
  }
  public Object state(AuthenticatedPrincipal p, String query, int offset) {
    bounded(query, 200, false);
    var all = studio.revisions(p, null, null, query, 200, offset);
    var fileRows = db.queryForList("SELECT id,filename,extension,sha256,size_bytes,created_at FROM studio_files WHERE project_id=?"
        + " AND (LOCATE(?,filename)>0 OR LOCATE(?,sha256)>0) ORDER BY created_at DESC,id LIMIT 200 OFFSET ?",
        studio.projectId(), Objects.toString(query, ""), Objects.toString(query, ""), offset);
    return Map.of("project", studio.overview(p), "revisions", all, "files", fileRows,
        "events", studio.events(p), "nextOffset", all.size() == 200 || fileRows.size() == 200 ? offset + 200 : -1);
  }
  @Transactional
  public Object projectChange(AuthenticatedPrincipal p, String action, String title) {
    return projects.change(p, action, title);
  }
  @Transactional(rollbackFor = Exception.class)
  public Object upload(AuthenticatedPrincipal p, MultipartFile file, String source, String rights) throws Exception {
    studio.require(p, true, false);
    bounded(source, 1000, true); bounded(rights, 1000, true);
    validator.validate(file, FORMATS);
    String name = Objects.requireNonNull(file.getOriginalFilename()).replaceAll("[\\\\/\\p{Cntrl}]", "_");
    bounded(name, 255, true);
    byte[] bytes = file.getBytes();
    String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    var existing = db.queryForList("SELECT id FROM studio_files WHERE project_id=? AND sha256=?", studio.projectId(), sha);
    String fileId;
    boolean duplicate = !existing.isEmpty();
    if (duplicate) fileId = (String) existing.getFirst().get("id");
    else {
      fileId = UUID.randomUUID().toString();
      String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
      String key = storage.saveRaw(p.userId(), "naesin-" + fileId + "." + ext, new ByteArrayInputStream(bytes), bytes.length);
      TransactionalFileDeletion.onRollback(() -> storage.delete(key, "UNKNOWN"));
      db.update("INSERT INTO studio_files(id,project_id,sha256,storage_key,filename,extension,size_bytes,uploaded_by) VALUES (?,?,?,?,?,?,?,?)",
          fileId, studio.projectId(), sha, key, name, ext, bytes.length, p.userId());
      event(null, "UPLOAD", fileId, p);
    }
    // Exact retries do not duplicate history; changed provenance is retained as a separate source.
    if (duplicate) {
      for (var r : db.queryForList("SELECT * FROM studio_revisions WHERE project_id=? AND file_id=? AND origin='ORIGINAL' ORDER BY created_at,id", studio.projectId(), fileId)) {
        var metadata = json.readTree((String) r.get("content"));
        if (source.equals(metadata.path("source").asText()) && rights.equals(metadata.path("rights").asText()))
          return Map.of("file", safeFile(studio.file(p, fileId)), "revision", r, "duplicate", true);
      }
    }
    // Each registration retains its own provenance even when identical bytes reuse one blob.
    var revision = studio.save(p, new StudioService.Revision("source-" + UUID.randomUUID(), null, "original", "SCRIPT",
        null, null, "ORIGINAL", name.substring(0, Math.min(150, name.length())),
        encode(Map.of("category", "SOURCE", "source", source, "rights", rights, "sha256", sha)),
        "", "", "READY", fileId));
    return Map.of("file", safeFile(studio.file(p, fileId)), "revision", revision, "duplicate", duplicate);
  }
  @Transactional
  public Object save(AuthenticatedPrincipal p, Work r) {
    studio.require(p, true, false);
    if (r == null) throw error(400, "작업 정보를 입력하세요.");
    if (!Set.of("WORK", "RESULT", "HANDOFF").contains(Objects.toString(r.category(), ""))) throw error(400, "작업 분류 오류");
    bounded(r.goal(), 4000, true); bounded(r.prompt(), 16000, true); bounded(r.model(), 200, true);
    bounded(r.tools(), 2000, true); bounded(r.result(), 100000, false); bounded(r.errors(), 8000, "FAILED".equals(r.status()));
    bounded(r.nextWork(), 4000, true);
    if (!Set.of("Claude", "ChatGPT Work", "HUMAN").contains(Objects.toString(r.ai(), ""))) throw error(400, "담당 AI를 선택하세요.");
    validateFiles(p, r.sourceFileIds());
    if (r.sourceFileIds().isEmpty()) throw error(400, "사용한 원재료를 연결하세요.");
    if (r.referenceRevisionIds() != null) {
      if (r.referenceRevisionIds().size() > 50) throw error(400, "참조 버전은 50개 이하입니다.");
      for (String id : r.referenceRevisionIds()) studio.revision(p, id);
    }
    if (r.parentId() != null && !r.parentId().isBlank()) {
      var old = studio.revision(p, r.parentId());
      var data = json.readTree((String) old.get("content"));
      if (!r.category().equals(data.path("category").asText())) throw error(400, "원본·자료 분류를 변경할 수 없습니다.");
    }
    return studio.save(p, new StudioService.Revision(r.itemKey(), r.parentId(), r.label(),
        "RESULT".equals(r.category()) ? "SCRIPT" : "JOB", null, null, "HUMAN".equals(r.ai()) ? "USER_EDIT" : "AI_PROPOSAL", r.title(),
        encode(r), "", "", r.status(), r.fileId()));
  }
  private void validateFiles(AuthenticatedPrincipal p, List<String> ids) {
    if (ids == null || ids.size() > 50 || new HashSet<>(ids).size() != ids.size()) throw error(400, "원재료 파일은 중복 없이 50개 이하입니다.");
    for (String id : ids) studio.file(p, id);
  }
  public Object detail(AuthenticatedPrincipal p, String id) {
    var row = studio.revision(p, id);
    return Map.of("revision", row, "checks", checks(id), "parent", row.get("parent_id") == null ? Map.of() : studio.revision(p, (String) row.get("parent_id")), "history", db.queryForList(
        "SELECT id,parent_id,label,title,work_status,created_at FROM studio_revisions WHERE project_id=? AND item_key=? ORDER BY created_at,id",
        studio.projectId(), row.get("item_key")));
  }
  public Map<String, Boolean> checks(String id) {
    var result = new LinkedHashMap<String, Boolean>();
    for (var row : db.queryForList("SELECT note FROM studio_events WHERE project_id=? AND revision_id=? AND action='NAESIN_CHECK' ORDER BY id",
        studio.projectId(), id)) {
      var data = json.readTree((String) row.get("note"));
      result.put(data.path("dimension").asText(), data.path("passed").asBoolean());
    }
    return result;
  }
  @Transactional
  public Object check(AuthenticatedPrincipal p, String id, Check r) {
    studio.require(p, false, true); studio.revision(p, id);
    if (r == null) throw error(400, "검수 정보를 입력하세요.");
    if (!CHECKS.contains(Objects.toString(r.dimension(), ""))) throw error(400, "검수 항목 오류");
    bounded(r.note(), 1000, true);
    event(id, "NAESIN_CHECK", encode(r), p);
    return detail(p, id);
  }
  private void ready(AuthenticatedPrincipal p, String id) {
    var row = studio.revision(p, id);
    var work = json.readTree((String) row.get("content"));
    if (!"RESULT".equals(work.path("category").asText()) || row.get("file_id") == null
        || !Set.of("READY", "REVIEW").contains(row.get("work_status"))) throw error(409, "완성본 파일과 검수 가능한 상태가 필요합니다.");
    var checks = checks(id);
    if (!CHECKS.stream().allMatch(c -> Boolean.TRUE.equals(checks.get(c)))) throw error(409, "문항·해설·서식·권한 검수를 모두 완료하세요.");
  }
  @Transactional
  public Object review(AuthenticatedPrincipal p, String id, Decision r) {
    studio.require(p, false, true);
    if (r == null) throw error(400, "검수 판단을 입력하세요.");
    if ("APPROVE".equals(r.action())) ready(p, id);
    studio.review(p, id, r.action(), r.note());
    return detail(p, id);
  }
  @Transactional
  public Object publish(AuthenticatedPrincipal p, String id, Publication r) {
    if (!"ADMIN".equals(p.role())) throw error(403, "게시 승인은 관리자 전용입니다.");
    if (r == null) throw error(400, "승인 버전을 확인하세요.");
    studio.require(p, false, true); ready(p, id);
    var row = studio.revision(p, id);
    if (!id.equals(r.expectedApprovedId()) || !id.equals(row.get("approved_id"))) throw error(409, "승인본이 변경되었습니다.");
    var prior = db.queryForList("SELECT id FROM studio_events WHERE project_id=? AND revision_id=? AND action='NAESIN_PUBLISH'", studio.projectId(), id);
    if (prior.isEmpty()) event(id, "NAESIN_PUBLISH", "관리자 완성본 게시 승인", p);
    return publicationRows(id, 0).getFirst();
  }
  public Object published(AuthenticatedPrincipal p, int offset) {
    wikiAccess(p);
    if (offset < 0 || offset > 1000000) throw error(400, "페이지 범위 오류");
    return publicationRows(null, offset);
  }
  private List<Map<String, Object>> publicationRows(String revision, int offset) {
    return db.queryForList("SELECT e.id AS publication_id,r.id AS revision_id,r.project_id,r.label,r.title,r.created_at,f.filename,f.sha256,p.content AS profile"
        + " FROM studio_events e JOIN studio_revisions r ON r.id=e.revision_id AND r.project_id=e.project_id"
        + " JOIN studio_files f ON f.id=r.file_id AND f.project_id=r.project_id"
        + " JOIN studio_revisions p ON p.project_id=r.project_id AND p.item_key='project-profile' AND p.label='original'"
        + " WHERE e.action='NAESIN_PUBLISH' AND e.project_id LIKE 'naesin-%'"
        + (revision == null ? "" : " AND r.id=?") + " ORDER BY e.id DESC LIMIT 100 OFFSET ?",
        revision == null ? new Object[]{offset} : new Object[]{revision, offset});
  }
  public Map<String, Object> publishedFile(AuthenticatedPrincipal p, long publication) {
    wikiAccess(p);
    return db.queryForList("SELECT f.* FROM studio_events e JOIN studio_revisions r ON r.id=e.revision_id AND r.project_id=e.project_id"
        + " JOIN studio_files f ON f.id=r.file_id AND f.project_id=r.project_id WHERE e.id=? AND e.action='NAESIN_PUBLISH' AND e.project_id LIKE 'naesin-%'", publication)
        .stream().findFirst().orElseThrow(() -> error(404, "게시된 완성본이 없습니다."));
  }
  private void wikiAccess(AuthenticatedPrincipal p) {
    if (p == null) throw error(401, "로그인이 필요합니다.");
    if (!Set.of("STUDENT", "TUTOR", "ADMIN").contains(p.role())) throw error(403, "내신Wiki 이용 권한이 필요합니다.");
  }
  public byte[] fileBytes(Map<String, Object> f) throws IOException {
    return storage.readSingleLimited((String) f.get("storage_key"), "UNKNOWN", 20L * 1024 * 1024);
  }
  public Map<String, Object> file(AuthenticatedPrincipal p, String id) { return studio.file(p, id); }
  @Transactional
  public Object extract(AuthenticatedPrincipal p, String id, boolean retry) throws IOException {
    studio.require(p, true, false);
    var f = studio.file(p, id);
    var rows = db.queryForList("SELECT r.* FROM studio_items i JOIN studio_revisions r ON r.id=i.head_id WHERE i.project_id=? AND i.item_key=?",
        studio.projectId(), "extract-" + id);
    if (!rows.isEmpty() && ("READY".equals(rows.getFirst().get("work_status")) || !retry)) return rows.getFirst();
    String text = "", error = "";
    try { text = extractor.extract(new BytesFile((String) f.get("filename"), fileBytes(f))); }
    catch (IOException | RuntimeException failure) { error = "본문 추출 실패. 손상·암호·지원 형식을 확인하세요."; }
    return studio.save(p, new StudioService.Revision("extract-" + id,
        rows.isEmpty() ? null : (String) rows.getFirst().get("id"), "extract-" + UUID.randomUUID().toString().substring(0, 8),
        "JOB", null, null, "AI_PROPOSAL", "본문 추출 · " + ((String)f.get("filename")).substring(0, Math.min(130, ((String)f.get("filename")).length())),
        encode(Map.of("category", "EXTRACTION", "sourceFileIds", List.of(id), "text", text, "errors", error,
            "tool", "HWP5 record parser / existing ExamInsightTextExtractor", "layoutVerified", false)),
        "", "", error.isEmpty() ? "READY" : "FAILED", null));
  }
  static Map<String, Object> safeFile(Map<String, Object> file) {
    var out = new LinkedHashMap<>(file); out.remove("storage_key"); return out;
  }
  private void event(String revision, String action, String note, AuthenticatedPrincipal p) {
    db.update("INSERT INTO studio_events(project_id,revision_id,action,note,actor_id) VALUES (?,?,?,?,?)", studio.projectId(), revision, action, note, p.userId());
  }
  private String encode(Object value) { return json.writeValueAsString(value); }
  static String bounded(String v, int max, boolean required) {
    if ((required && (v == null || v.isBlank())) || (v != null && v.length() > max)) throw error(400, "필수 항목 또는 입력 길이를 확인하세요.");
    return v;
  }
  static ResponseStatusException error(int status, String message) { return new ResponseStatusException(HttpStatus.valueOf(status), message); }
  record BytesFile(String filename, byte[] bytes) implements MultipartFile {
    public String getName() { return "file"; }
    public String getOriginalFilename() { return filename; }
    public String getContentType() { return DocumentUploadValidator.contentType(filename); }
    public boolean isEmpty() { return bytes.length == 0; }
    public long getSize() { return bytes.length; }
    public byte[] getBytes() { return bytes; }
    public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
    public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), bytes); }
  }
}
