package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 관리자 전용 학생명단(시간표) 자료실. 연도마다 미리보기 PDF + 원본 Word 한 쌍을 Studio 리비전으로 저장한다.
 *
 * <p>삭제 API는 두지 않는다. 잘못 올리면 같은 연도로 다시 올려 새 버전으로 바꾸고, 이전 버전은 이력으로 남는다.
 */
@Controller
@RequiredArgsConstructor
public class StudentRosterController {
  public static final String PROJECT = "student-roster";
  static final Set<String> UPLOAD_EXTENSIONS = Set.of("pdf", "doc", "docx");
  private final StudioService studio;
  private final JdbcTemplate db;

  @Value("${app.studio.public-base-url:${app.mcp.public-base-url:https://feel2real.co.kr}}")
  private String publicBase;

  @ModelAttribute
  public void guard(@AuthenticationPrincipal AuthenticatedPrincipal p, HttpServletRequest request,
                    HttpServletResponse response) {
    requireAdmin(p);
    response.setHeader("Cache-Control", "private, no-store");
    if (!Set.of("GET", "HEAD").contains(request.getMethod()) && !publicBase.equals(request.getHeader("Origin")))
      throw StudioService.error(403, "같은 사이트에서 작업하세요.");
    request.setAttribute(StudioService.MCP_PROJECT_ATTRIBUTE, PROJECT);
  }

  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  @ResponseBody
  public ResponseEntity<?> error(org.springframework.web.server.ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", Objects.toString(e.getReason(), "요청 실패")));
  }

  @GetMapping("/admin/student-roster")
  public String page() { return "admin/student_roster"; }

  private void ensureProject() {
    db.update("INSERT IGNORE INTO studio_projects(id,title) VALUES (?,?)", PROJECT, "학생명단(시간표)");
  }

  /** "2026" 같은 4자리 연도만 받는다. */
  static int year(String value) {
    if (value == null || !value.strip().matches("\\d{4}")) throw StudioService.error(400, "연도를 4자리로 적어 주세요.");
    int y = Integer.parseInt(value.strip());
    if (y < 1990 || y > 2100) throw StudioService.error(400, "연도를 4자리로 적어 주세요.");
    return y;
  }

  static String itemKey(int year) { return "roster-" + year; }

  /** DB 행을 화면용 값으로. 쪽수는 저장 당시 PDF에서 센 값(direction 칸)이다. */
  static Map<String, Object> row(Map<String, Object> r) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("year", Integer.parseInt(Objects.toString(r.get("title"))));
    out.put("revisionId", r.get("id"));
    out.put("pdfFileId", r.get("file_id"));
    out.put("pdfFilename", r.get("pdf_filename"));
    out.put("wordFileId", r.get("dialogue"));
    out.put("wordFilename", r.get("word_filename"));
    String pages = Objects.toString(r.get("direction"), "");
    out.put("pages", pages.matches("\\d{1,4}") ? Integer.parseInt(pages) : null);
    out.put("uploadedAt", r.get("created_at"));
    if (r.containsKey("versions")) out.put("versions", r.get("versions"));
    if (r.containsKey("current"))
      out.put("current", r.get("current") instanceof Number n ? n.intValue() == 1 : Boolean.TRUE.equals(r.get("current")));
    return out;
  }

  private static final String COLUMNS =
      "r.id,r.title,r.file_id,r.dialogue,r.direction,r.created_at,pf.filename AS pdf_filename,wf.filename AS word_filename";
  private static final String FILES =
      " LEFT JOIN studio_files pf ON pf.id=r.file_id AND pf.project_id=r.project_id"
          + " LEFT JOIN studio_files wf ON wf.id=r.dialogue AND wf.project_id=r.project_id";

  @GetMapping("/api/admin/student-roster/entries") @ResponseBody
  public List<Map<String, Object>> entries() {
    return db.queryForList("SELECT " + COLUMNS + ",(SELECT COUNT(*) FROM studio_revisions h WHERE h.project_id=i.project_id AND h.item_key=i.item_key) AS versions"
            + " FROM studio_items i JOIN studio_revisions r ON r.id=i.head_id AND r.project_id=i.project_id" + FILES
            + " WHERE i.project_id=? AND i.item_key LIKE 'roster-%' ORDER BY r.title DESC LIMIT 200", PROJECT)
        .stream().map(StudentRosterController::row).toList();
  }

  @PostMapping("/api/admin/student-roster/files") @ResponseBody @Transactional
  public Object upload(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam MultipartFile file) throws IOException {
    String name = Objects.toString(file == null ? null : file.getOriginalFilename(), "");
    String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
    if (!UPLOAD_EXTENSIONS.contains(ext)) throw StudioService.error(400, "PDF 또는 Word(doc·docx) 파일만 올릴 수 있습니다.");
    ensureProject();
    return studio.upload(p, file);
  }

  public record Entry(String year, String pdfFileId, String wordFileId) {}

  @PostMapping("/api/admin/student-roster/entries") @ResponseBody @Transactional
  public Object save(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody Entry e) throws IOException {
    int y = year(e == null ? null : e.year());
    if (e.pdfFileId() == null || e.pdfFileId().isBlank() || e.wordFileId() == null || e.wordFileId().isBlank())
      throw StudioService.error(400, "미리보기 PDF와 원본 Word를 함께 올려 주세요.");
    ensureProject();
    var pdf = studio.file(p, e.pdfFileId());
    if (!"pdf".equals(pdf.get("extension"))) throw StudioService.error(400, "미리보기는 PDF 파일이어야 합니다.");
    var word = studio.file(p, e.wordFileId());
    if (!StudioService.ROSTER_WORD_EXTENSIONS.contains(Objects.toString(word.get("extension"))))
      throw StudioService.error(400, "원본은 Word(doc·docx) 파일이어야 합니다.");
    int pages = pages(studio.bytes(p, e.pdfFileId()));
    var head = db.queryForList("SELECT head_id FROM studio_items WHERE project_id=? AND item_key=?", PROJECT, itemKey(y));
    String parent = head.isEmpty() ? null : (String) head.getFirst().get("head_id");
    var saved = studio.save(p, new StudioService.Revision(itemKey(y), parent, "v-" + UUID.randomUUID(), "JOB", null, null,
        "USER_EDIT", Integer.toString(y), y + "년 학생명단(시간표)", e.wordFileId(), Integer.toString(pages), "READY", e.pdfFileId()));
    return Map.of("message", "저장했습니다", "year", y, "pages", pages, "revisionId", saved.get("id"));
  }

  /** PDF 쪽수. 읽을 수 없는 PDF는 저장하지 않는다. */
  static int pages(byte[] pdf) {
    try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
      return doc.getNumberOfPages();
    } catch (IOException e) {
      throw StudioService.error(400, "PDF를 열 수 없습니다. 파일을 확인해 주세요.");
    }
  }

  @GetMapping("/api/admin/student-roster/history") @ResponseBody
  public List<Map<String, Object>> history(@RequestParam String year) {
    int y = year(year);
    return db.queryForList("SELECT " + COLUMNS + ",(r.id=i.head_id) AS current FROM studio_revisions r"
            + " JOIN studio_items i ON i.project_id=r.project_id AND i.item_key=r.item_key" + FILES
            + " WHERE r.project_id=? AND r.item_key=? ORDER BY r.created_at DESC,r.id LIMIT 100", PROJECT, itemKey(y))
        .stream().map(StudentRosterController::row).toList();
  }

  private static void requireAdmin(AuthenticatedPrincipal p) {
    if (p == null || !"ADMIN".equals(p.role())) throw StudioService.error(403, "관리자 전용입니다.");
  }

  static final String MCP_INSTRUCTION =
      "아래 숫자는 연도별 학생명단(시간표) Word 파일에서 서버가 집계한 값입니다. 학생 이름은 가명(S001…)으로 바꾸었고 실제 이름은 제공하지 않습니다."
          + " 실명을 추측하거나 요청하지 마세요. 이 집계만 근거로 교습소·학원 발전 방안(신규 모집 시기, 재등록·유지율, 요일 운영, 방학·시험기간 수요, 상담→등록 전환)을"
          + " 원장과 함께 논의하세요. 같은 이름은 같은 가명이므로 동명이인은 한 명으로, 표기가 바뀐 같은 학생은 다른 사람으로 셀 수 있습니다."
          + " 수업 수는 칸에 적힌 이름+회차 기록의 개수이며 매출·출결 확정 자료가 아닙니다.";

  /** MCP read_student_roster: 연도별 최신 Word를 읽어 이름 없는 집계만 돌려준다. 원문·파일 ID·실명은 내보내지 않는다. */
  public Map<String, Object> mcpRead(AuthenticatedPrincipal p) {
    requireAdmin(p);
    var request = ((org.springframework.web.context.request.ServletRequestAttributes)
        org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest();
    request.setAttribute(StudioService.MCP_PROJECT_ATTRIBUTE, PROJECT);
    List<StudentRosterAnalyzer.Sheet> sheets = new ArrayList<>();
    List<Map<String, Object>> sources = new ArrayList<>();
    for (var entry : entries()) {
      int y = (Integer) entry.get("year");
      String source;
      int months;
      try {
        String wordId = (String) entry.get("wordFileId"), pdfId = (String) entry.get("pdfFileId");
        String ext = Objects.toString(studio.file(p, wordId).get("extension"));
        var parsed = StudentRosterAnalyzer.read(ext, studio.bytes(p, wordId), pdfId == null ? null : studio.bytes(p, pdfId), y);
        sheets.addAll(parsed.sheets());
        source = parsed.source();
        months = parsed.sheets().size();
      } catch (IOException | RuntimeException e) {
        source = "unreadable";
        months = 0;
      }
      sources.add(Map.of("archiveYear", y, "readAs", source, "monthTables", months));
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("instruction", MCP_INSTRUCTION);
    out.put("privacy", "실명 없음 · 같은 이름=같은 가명(S001…) · 원본 파일 제공 없음");
    out.put("sources", sources);
    out.putAll(StudentRosterAnalyzer.summarize(sheets));
    out.put("url", "/admin/student-roster");
    return out;
  }
}
