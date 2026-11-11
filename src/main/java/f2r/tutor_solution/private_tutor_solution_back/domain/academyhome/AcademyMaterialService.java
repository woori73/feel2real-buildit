package f2r.tutor_solution.private_tutor_solution_back.domain.academyhome;

import com.fasterxml.jackson.databind.JsonNode;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.AcademyMoveController;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudentRosterController;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.AcademyAssistantAdapter;
import java.io.IOException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 정보 화면: 이미 올린 자료(스튜디오 파일 저장소의 학생명단·학원이사 자료)를 기존 권한 그대로 찾아 글로 읽게 한다.
 * 자료를 새로 저장하거나 학생을 만들지 않는다. 학생 등록은 기존 AI 업무 비서 초안 → 원장 승인 절차만 쓴다.
 */
@Service
@RequiredArgsConstructor
public class AcademyMaterialService {
  static final Set<String> PROJECTS = Set.of(StudentRosterController.PROJECT, AcademyMoveController.PROJECT);
  private final JdbcTemplate db;
  private final StudioService studio;
  private final StudentRosterController roster;
  private final AcademyAssistantAdapter academy;
  private final AcademyRosterStudentLinkRepository links;

  /** 학생명단·학원이사 자료는 기존 규칙상 관리자 전용이다(StudioService.permission). 다른 역할에는 빈 목록과 이유를 준다. */
  public Map<String, Object> list(AuthenticatedPrincipal p) {
    if (p == null) throw AcademyMaterialService.error(401, "로그인이 필요합니다.");
    Map<String, Object> out = new LinkedHashMap<>();
    if (!"ADMIN".equals(p.role())) {
      out.put("items", List.of());
      out.put("notice", "보관된 학생명단·학원이사 자료는 관리자 전용입니다. 이 계정으로 열 수 있는 자료가 없습니다.");
      return out;
    }
    List<Map<String, Object>> items = new ArrayList<>();
    for (var r : db.queryForList(
        "SELECT r.title,r.file_id,r.dialogue,r.created_at,pf.filename pdf_name,pf.size_bytes pdf_size,wf.filename word_name,wf.extension word_ext,wf.size_bytes word_size"
            + " FROM studio_items i JOIN studio_revisions r ON r.id=i.head_id AND r.project_id=i.project_id"
            + " LEFT JOIN studio_files pf ON pf.id=r.file_id AND pf.project_id=r.project_id"
            + " LEFT JOIN studio_files wf ON wf.id=r.dialogue AND wf.project_id=r.project_id"
            + " WHERE i.project_id=? AND i.item_key LIKE 'roster-%' ORDER BY r.title DESC LIMIT 50", StudentRosterController.PROJECT)) {
      String year = Objects.toString(r.get("title"), "");
      List<Map<String, Object>> files = new ArrayList<>();
      if (r.get("dialogue") != null) files.add(file((String) r.get("dialogue"), r.get("word_name"), r.get("word_ext"), r.get("word_size"), "원본 Word"));
      if (r.get("file_id") != null) files.add(file((String) r.get("file_id"), r.get("pdf_name"), "pdf", r.get("pdf_size"), "미리보기 PDF"));
      items.add(item(StudentRosterController.PROJECT, "ROSTER", year + "년 학생명단(시간표)", "학생 명단 · 반·수업 시간표 · " + year + "년", r.get("created_at"), files, null, year));
    }
    for (var r : db.queryForList(
        "SELECT r.title,r.content,r.dialogue,r.direction,r.file_id,r.created_at,f.filename,f.extension,f.size_bytes"
            + " FROM studio_items i JOIN studio_revisions r ON r.id=i.head_id AND r.project_id=i.project_id"
            + " LEFT JOIN studio_files f ON f.id=r.file_id AND f.project_id=r.project_id"
            + " WHERE i.project_id=? AND r.kind='JOB' AND (r.direction IS NULL OR r.direction<>'ARCHIVED') ORDER BY r.created_at DESC LIMIT 50", AcademyMoveController.PROJECT)) {
      List<Map<String, Object>> files = new ArrayList<>();
      if (r.get("file_id") != null) files.add(file((String) r.get("file_id"), r.get("filename"), r.get("extension"), r.get("size_bytes"), "첨부 원본"));
      items.add(item(AcademyMoveController.PROJECT, "MOVE", Objects.toString(r.get("title"), ""), "학원이사 업무 · " + Objects.toString(r.get("dialogue"), "분류 없음"),
          r.get("created_at"), files, Objects.toString(r.get("content"), ""), null));
    }
    // 올렸지만 어떤 기록에도 붙지 않은 파일(저장 단계가 끝나지 않은 첨부). 다시 올리지 않고 여기서 읽는다.
    for (var r : db.queryForList(
        "SELECT f.id,f.project_id,f.filename,f.extension,f.size_bytes,f.created_at FROM studio_files f WHERE f.project_id IN (?,?)"
            + " AND NOT EXISTS (SELECT 1 FROM studio_revisions r WHERE r.project_id=f.project_id AND (r.file_id=f.id OR r.dialogue=f.id))"
            + " ORDER BY f.created_at DESC LIMIT 50", StudentRosterController.PROJECT, AcademyMoveController.PROJECT)) {
      String project = (String) r.get("project_id");
      items.add(item(project, "UNLINKED", "기록에 연결되지 않은 업로드 자료",
          (StudentRosterController.PROJECT.equals(project) ? "학생명단 자료실" : "학원이사") + " · 저장(기록 연결) 전 단계에서 멈춤",
          r.get("created_at"), List.of(file((String) r.get("id"), r.get("filename"), r.get("extension"), r.get("size_bytes"), "업로드 원본")), null, null));
    }
    out.put("items", items);
    out.put("notice", items.isEmpty() ? "보관된 자료가 없습니다." : null);
    return out;
  }

  private static Map<String, Object> file(String id, Object name, Object ext, Object size, String role) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("fileId", id); m.put("filename", Objects.toString(name, "")); m.put("extension", Objects.toString(ext, ""));
    m.put("sizeBytes", size); m.put("role", role);
    return m;
  }

  private static Map<String, Object> item(String project, String kind, String title, String context, Object createdAt,
                                          List<Map<String, Object>> files, String typedNote, String year) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("projectId", project); m.put("kind", kind); m.put("title", title); m.put("context", context);
    m.put("createdAt", createdAt == null ? null : createdAt.toString()); m.put("files", files);
    if (typedNote != null && !typedNote.isBlank()) m.put("typedNote", typedNote);
    if (year != null) m.put("year", year);
    return m;
  }

  /** 원본에서 글을 읽는다. 기존 파일 권한(StudioService.file)을 그대로 통과해야 한다. 저장하지 않는다(다시 읽기 = 재처리). */
  public Map<String, Object> read(AuthenticatedPrincipal p, String projectId, String fileId) throws IOException {
    select(projectId);
    if (fileId == null || !fileId.matches("[A-Za-z0-9-]{1,36}")) throw AcademyMaterialService.error(400, "자료 ID가 올바르지 않습니다.");
    var meta = studio.file(p, fileId);
    String ext = Objects.toString(meta.get("extension"), "");
    long size = meta.get("size_bytes") instanceof Number n ? n.longValue() : 0L;
    // 기존 스튜디오 글 보기(/api/studio/files/{id}/text)와 같은 20 MiB 한도. 넘으면 원본으로 확인한다.
    var result = size > 20L * 1024 * 1024
        ? new MaterialTextExtractor.Result("UNSUPPORTED", ext, "20 MiB를 넘는 자료는 화면에서 글로 읽지 않습니다. 원본을 열어 확인하세요.", List.of(), false, null)
        : MaterialTextExtractor.extract(ext, studio.bytes(p, fileId));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("fileId", fileId); out.put("projectId", projectId);
    out.put("filename", Objects.toString(meta.get("filename"), "")); out.put("extension", ext); out.put("sizeBytes", meta.get("size_bytes"));
    out.put("original", "/api/studio/files/" + fileId + "/content?projectId=" + projectId);
    out.put("preview", Set.of("png", "jpg", "jpeg", "webp", "pdf").contains(ext) ? "/api/studio/files/" + fileId + "/preview?projectId=" + projectId : null);
    out.put("result", result);
    return out;
  }

  /** 학생명단 이름 ↔ 선택 학원 학생 대조. 학원 권한은 AcademyCraft 연결 문맥으로 확인한다. */
  public Map<String, Object> reconcile(AuthenticatedPrincipal p, int year, Long academyId) throws IOException {
    var context = academy.context(p, academyId);
    long id = context.path("academyId").asLong();
    if (academyId == null || id != academyId) throw AcademyMaterialService.error(403, "이 학원에 연결된 권한이 없습니다.");
    var lessons = roster.lessonsOf(p, year);
    List<RosterReconciler.Student> students = students(p, id);
    var report = RosterReconciler.reconcile(lessons.stream().map(StudentRosterController.RosterLesson::name).toList(), students,
        links.findByAcademyIdAndRosterYear(id, year).stream().map(l -> new RosterReconciler.Link(l.getRosterName(), l.getLinkStatus(), l.getReaStudentId())).toList());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("academyId", id); out.put("year", year); out.put("role", context.path("role").asText());
    out.put("activeStudentCount", students.stream().filter(RosterReconciler.Student::active).count());
    out.put("report", report); out.put("balanced", report.balanced());
    return out;
  }

  public record LinkRequest(Long academyId, Integer year, String name, String status, Long studentId) {}

  /** 원장이 보류 행을 해결한다: 학생을 골라 연결(LINKED) 또는 제외(EXCLUDED). 같은 학원·연도·이름은 한 행만 남는다. */
  @Transactional
  public Map<String, Object> link(AuthenticatedPrincipal p, LinkRequest r) throws IOException {
    if (r == null || r.academyId() == null || r.year() == null || r.name() == null || r.name().isBlank())
      throw AcademyMaterialService.error(400, "학원·연도·이름을 확인하세요.");
    if (!Set.of(AcademyRosterStudentLink.LINKED, AcademyRosterStudentLink.EXCLUDED).contains(r.status()))
      throw AcademyMaterialService.error(400, "연결 또는 제외만 선택할 수 있습니다.");
    academy.owner(p, r.academyId());
    String name = RosterReconciler.key(r.name());
    if (name.length() > 40) throw AcademyMaterialService.error(400, "이름이 너무 깁니다.");
    boolean inRoster = roster.lessonsOf(p, r.year()).stream().anyMatch(l -> RosterReconciler.key(l.name()).equals(name));
    if (!inRoster) throw AcademyMaterialService.error(404, "이 연도 학생명단에 없는 이름입니다.");
    if (AcademyRosterStudentLink.LINKED.equals(r.status())) {
      boolean active = students(p, r.academyId()).stream().anyMatch(s -> s.active() && s.id() == Objects.requireNonNullElse(r.studentId(), -1L));
      if (!active) throw AcademyMaterialService.error(400, "이 학원의 재원 학생을 골라 주세요.");
    }
    var row = links.findByAcademyIdAndRosterYearAndRosterName(r.academyId(), r.year(), name)
        .orElseGet(() -> new AcademyRosterStudentLink(r.academyId(), r.year(), name));
    row.confirm(r.status(), r.studentId(), p.userId());
    links.save(row);
    return reconcile(p, r.year(), r.academyId());
  }

  private List<RosterReconciler.Student> students(AuthenticatedPrincipal p, long academyId) {
    List<RosterReconciler.Student> out = new ArrayList<>();
    add(out, academy.request(p, "GET", academy.base(academyId) + "/students", null), true);
    add(out, academy.request(p, "GET", academy.base(academyId) + "/students/inactive", null), false);
    return out;
  }

  private static void add(List<RosterReconciler.Student> out, JsonNode list, boolean active) {
    for (JsonNode s : list)
      out.add(new RosterReconciler.Student(s.path("id").asLong(), s.path("name").asText(""), s.path("grade").asText(""), s.path("schoolName").asText(""), active));
  }

  static org.springframework.web.server.ResponseStatusException error(int code, String message) {
    return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.valueOf(code), message);
  }

  /** 정보 화면이 읽는 자료 묶음만 허용하고, 스튜디오 권한 검사가 이 묶음 기준으로 돌게 한다. */
  private static void select(String projectId) {
    if (projectId == null || !PROJECTS.contains(projectId)) throw AcademyMaterialService.error(400, "읽을 수 없는 자료 묶음입니다.");
    var request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    request.setAttribute(StudioService.MCP_PROJECT_ATTRIBUTE, projectId);
  }
}
