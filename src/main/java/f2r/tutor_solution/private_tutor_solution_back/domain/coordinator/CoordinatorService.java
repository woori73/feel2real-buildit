package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyBusinessResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.EventView;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.StudentOverviewRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * F2R AI Coordinator 1단계: AI(MCP)가 학생을 찾고 출결 기록 <b>초안</b>을 만든다. 사용자가 확인해야 기존
 * {@link EasyAttendanceService#record}로 기록한다. 확인은 두 가지 중 하나다.
 *
 * <ul>
 *   <li>대화창 확인: AI가 대화에서 "기록할까요?"라고 묻고 사용자가 동의하면 {@link #confirmFromChat}(MCP 도구).
 *   <li>승인 화면: /admin/coord/approvals에서 [승인] — {@link #approve}.
 * </ul>
 *
 * <p>대화창으로 기록한 출결은 {@value #UNDO_MINUTES}분 안에 {@link #undoFromChat}으로 취소할 수 있다(기록은 지우지 않고
 * superseded로 남긴다).
 *
 * <ul>
 *   <li>AI 응답에는 실명 대신 가린 이름(김○수)·학년·반만 준다. 실명·학교는 승인 화면에서만 보인다.
 *   <li>같은 계정·같은 requestId는 같은 작업을 돌려준다(중복 방지). 그날 같은 구분의 기록이 있으면 초안을 만들지 않는다.
 *   <li>승인은 화면이 받은 planHash가 저장된 계획과 같을 때만 실행하고, 실행 직전에 중복을 다시 확인한다.
 *   <li>관리자(ADMIN) 전용. 출결 API(/api/admin/easy/attendance)와 같은 권한이다.
 * </ul>
 */
@Service
public class CoordinatorService {

  static final String KIND_ATTENDANCE = "ATTENDANCE";
  static final Set<String> EVENT_TYPES = Set.of("CHECK_IN", "CHECK_OUT", "ABSENT", "LATE", "EARLY_LEAVE");
  static final int APPROVAL_MINUTES = 30;
  static final int UNDO_MINUTES = 10;
  static final int MAX_MATCHES = 10;
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  private final CoordJobRepository jobs;
  private final CoordJobEventRepository events;
  private final EasyAttendanceService attendance;
  private final EasyTutorAccessService access;
  private final List<CoordActions> actions;
  private final Clock clock;

  @Autowired
  public CoordinatorService(CoordJobRepository jobs, CoordJobEventRepository events, EasyAttendanceService attendance,
      EasyTutorAccessService access, List<CoordActions> actions) {
    this(jobs, events, attendance, access, actions,
        Clock.system(f2r.tutor_solution.private_tutor_solution_back.config.KstTime.ZONE));
  }

  CoordinatorService(CoordJobRepository jobs, CoordJobEventRepository events, EasyAttendanceService attendance,
      EasyTutorAccessService access, Clock clock) {
    this(jobs, events, attendance, access, List.of(), clock);
  }

  CoordinatorService(CoordJobRepository jobs, CoordJobEventRepository events, EasyAttendanceService attendance,
      EasyTutorAccessService access, List<CoordActions> actions, Clock clock) {
    this.jobs = jobs;
    this.events = events;
    this.attendance = attendance;
    this.access = access;
    this.actions = actions;
    this.clock = clock;
  }

  private CoordActions forArea(String area) {
    return actions.stream().filter(a -> a.areas().contains(area)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 작업 묶음입니다: " + area));
  }

  private CoordActions forKind(String kind) {
    return actions.stream().filter(a -> a.kinds().contains(kind)).findFirst()
        .orElseThrow(() -> new IllegalStateException("지원하지 않는 작업입니다: " + kind));
  }

  /* ---------------- 조회 ---------------- */

  /** 재원생 이름 검색(부분 일치). AI에는 가린 이름만 준다. 같은 이름이 둘 이상이면 duplicateName=true. */
  @Transactional(readOnly = true)
  public Map<String, Object> searchStudents(AuthenticatedPrincipal p, Long businessId, String query) {
    Long bid = business(p, businessId);
    String q = query == null ? "" : query.strip();
    if (q.isEmpty()) throw new IllegalArgumentException("학생 이름을 입력해 주세요.");
    List<StudentOverviewRow> rows = attendance.studentOverview(bid).stream()
        .filter(r -> r.name() != null && r.name().contains(q)).toList();
    long exact = rows.stream().filter(r -> r.name().equals(q)).count();
    List<Map<String, Object>> matches = new ArrayList<>();
    for (StudentOverviewRow r : rows.stream().limit(MAX_MATCHES).toList()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("studentId", r.studentId());
      m.put("displayName", maskName(r.name()));
      m.put("grade", r.grade());
      m.put("classes", r.classes());
      m.put("weekdays", r.weekdays());
      m.put("lastCheckIn", r.lastCheckIn() == null ? null : r.lastCheckIn().toString());
      matches.add(m);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("businessId", bid);
    out.put("matches", matches);
    out.put("duplicateName", exact > 1 || (exact == 0 && rows.size() > 1));
    out.put("note", "이름은 가려서 보여 줍니다. 여러 명이면 학년·반을 사용자에게 보여 주고 고르게 하세요.");
    return out;
  }

  /** 그날 출결 기록(유효한 것만, 최신순). */
  @Transactional(readOnly = true)
  public Map<String, Object> checkAttendance(AuthenticatedPrincipal p, Long businessId, Long studentId, String date) {
    Long bid = business(p, businessId);
    LocalDate day = date == null || date.isBlank() ? LocalDate.now(clock) : LocalDate.parse(date.strip());
    List<Map<String, Object>> list = new ArrayList<>();
    for (EventView e : attendance.studentHistory(bid, studentId)) {
      if (!e.occurredAt().toLocalDate().equals(day)) continue;
      list.add(Map.of("eventId", e.eventId(), "eventType", e.eventType(), "label", e.label(),
          "at", e.occurredAt().format(MINUTE), "source", e.source()));
    }
    return Map.of("businessId", bid, "studentId", studentId, "date", day.toString(), "events", list);
  }

  /* ---------------- 초안 ---------------- */

  public record AttendanceRequest(String requestId, Long businessId, Long studentId, String eventType, Long classId,
      String occurredAt) {}

  /**
   * 출결 기록 초안. 기록은 하지 않는다. 같은 requestId면 같은 작업, 그날 같은 구분의 기록이 있으면 ALREADY_RECORDED.
   */
  @Transactional
  public Map<String, Object> requestAttendance(AuthenticatedPrincipal p, String source, String clientName,
      AttendanceRequest r, String baseUrl) {
    requireAdmin(p);
    String requestId = requireRequestId(r.requestId());
    var existing = jobs.findByUserIdAndRequestId(p.userId(), requestId);
    if (existing.isPresent()) return view(existing.get(), baseUrl, false);

    Long bid = business(p, r.businessId());
    if (r.studentId() == null) throw new IllegalArgumentException("studentId가 필요합니다. 먼저 f2r_student_search로 찾으세요.");
    String type = r.eventType() == null || r.eventType().isBlank() ? "CHECK_IN" : r.eventType().strip().toUpperCase();
    if (!EVENT_TYPES.contains(type)) throw new IllegalArgumentException("출결 구분은 CHECK_IN·CHECK_OUT·ABSENT·LATE·EARLY_LEAVE 중 하나입니다.");
    StudentOverviewRow student = attendance.studentOverview(bid).stream()
        .filter(s -> s.studentId().equals(r.studentId())).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("재원 학생을 찾을 수 없습니다."));
    LocalDateTime now = LocalDateTime.now(clock).withSecond(0).withNano(0);
    LocalDateTime at = r.occurredAt() == null || r.occurredAt().isBlank() ? now : LocalDateTime.parse(r.occurredAt().strip());

    var already = attendance.activeEventOn(bid, student.studentId(), type, at.toLocalDate());
    if (already.isPresent()) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("status", "ALREADY_RECORDED");
      out.put("message", maskName(student.name()) + " 학생은 " + already.get().occurredAt().format(MINUTE).replace('T', ' ')
          + "에 이미 " + already.get().label() + " 기록이 있어 새로 만들지 않았습니다.");
      out.put("eventId", already.get().eventId());
      return out;
    }

    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put("action", "ATTENDANCE_RECORD");
    plan.put("businessId", bid);
    plan.put("businessName", businessName(p, bid));
    plan.put("studentId", student.studentId());
    plan.put("studentName", student.name());
    plan.put("school", student.school());
    plan.put("grade", student.grade());
    plan.put("classes", student.classes());
    plan.put("classId", r.classId());
    plan.put("eventType", type);
    plan.put("occurredAt", at.format(MINUTE));
    String planJson = JSON.writeValueAsString(plan);
    CoordJob job = new CoordJob(UUID.randomUUID().toString(), p.userId(), bid, requestId, KIND_ATTENDANCE, source,
        clientName == null ? null : clip(clientName, 100), planJson, sha256(planJson), now, now.plusMinutes(APPROVAL_MINUTES));
    jobs.save(job);
    log(job, source, p.loginId(), null, CoordJob.REVIEW_REQUIRED, "출결 초안 생성");
    return view(job, baseUrl, false);
  }

  /* ---------------- 학생·반 관리 초안 ---------------- */

  /** 반 목록(가린 이름). classId를 주면 그 반 학생도. */
  @Transactional(readOnly = true)
  public Map<String, Object> classes(AuthenticatedPrincipal p, Long businessId, Long classId, boolean includeEnded) {
    CoordActions a = forArea("CLASS");
    return ((CoordStudentActions) a).classes(business(p, businessId), classId, includeEnded);
  }

  /** 월별 수강료 수납 현황(가린 이름). */
  @Transactional(readOnly = true)
  public Map<String, Object> tuitionStatus(AuthenticatedPrincipal p, Long businessId, String month, String status) {
    return ((CoordTuitionActions) forArea("TUITION")).status(business(p, businessId), month, status);
  }

  /** 학생부(최근 출결·수업 기록·채점 기록·오답 기록). AI용으로 이름을 가린다. */
  @Transactional(readOnly = true)
  public Map<String, Object> studentBook(AuthenticatedPrincipal p, Long businessId, Long studentId) {
    if (studentId == null) throw new IllegalArgumentException("studentId가 필요합니다. 먼저 f2r_student_search로 찾으세요.");
    return ((CoordRecordActions) forArea("RECORD")).book(business(p, businessId), studentId);
  }

  /** 장부 손익 요약. */
  @Transactional(readOnly = true)
  public Map<String, Object> ledgerSummary(AuthenticatedPrincipal p, Long businessId, String from, String to) {
    return ((CoordTuitionActions) forArea("TUITION")).ledgerSummary(business(p, businessId), from, to);
  }

  /**
   * 학생(area=STUDENT)·반(area=CLASS)·수강료(area=TUITION) 변경 초안. 기록하지 않는다 — 출결과 같이 대화창 확인(f2r_confirm) 또는 승인 화면에서 실행.
   * 같은 계정·같은 requestId는 같은 작업.
   */
  @Transactional
  public Map<String, Object> requestChange(AuthenticatedPrincipal p, String source, String clientName, String area,
      String requestId, Long businessId, String action, JsonNode args, String baseUrl) {
    requireAdmin(p);
    String req = requireRequestId(requestId);
    var existing = jobs.findByUserIdAndRequestId(p.userId(), req);
    if (existing.isPresent()) return view(existing.get(), baseUrl, false);
    Long bid = business(p, businessId);
    CoordActions.Draft d = forArea(area).prepare(area, bid, action, args);
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put("action", d.kind());
    plan.put("businessId", bid);
    plan.put("businessName", businessName(p, bid));
    plan.put("title", d.title());
    plan.put("aiTitle", d.aiTitle());
    plan.putAll(d.plan());
    String planJson = JSON.writeValueAsString(plan);
    LocalDateTime now = LocalDateTime.now(clock).withSecond(0).withNano(0);
    CoordJob job = new CoordJob(UUID.randomUUID().toString(), p.userId(), bid, req, d.kind(), source,
        clientName == null ? null : clip(clientName, 100), planJson, sha256(planJson), now, now.plusMinutes(APPROVAL_MINUTES));
    jobs.save(job);
    log(job, source, p.loginId(), null, CoordJob.REVIEW_REQUIRED, "초안 생성 · " + d.kind());
    return view(job, baseUrl, false);
  }

  /* ---------------- 승인 화면 ---------------- */

  @Transactional
  public List<Map<String, Object>> list(AuthenticatedPrincipal p, String baseUrl) {
    requireAdmin(p);
    LocalDateTime now = LocalDateTime.now(clock);
    List<Map<String, Object>> out = new ArrayList<>();
    for (CoordJob j : jobs.findTop50ByUserIdOrderByCreatedAtDesc(p.userId())) {
      expireIfNeeded(j, now);
      out.add(view(j, baseUrl, true));
    }
    return out;
  }

  @Transactional
  public Map<String, Object> get(AuthenticatedPrincipal p, String jobId, String baseUrl, boolean forHuman) {
    CoordJob j = own(p, jobId);
    expireIfNeeded(j, LocalDateTime.now(clock));
    Map<String, Object> out = view(j, baseUrl, forHuman);
    if (forHuman) {
      out.put("events", events.findAllByJobIdOrderByAtAscEventIdAsc(j.getJobId()).stream()
          .map(e -> Map.of("at", e.getAt().format(MINUTE), "actor", e.getActor(), "to", e.getToStatus(),
              "message", e.getMessage() == null ? "" : e.getMessage()))
          .toList());
    }
    return out;
  }

  /** 승인 화면의 [승인]. planHash가 같고 대기 중일 때만 실행한다. 응답에는 실명이 들어간다(사람용). */
  @Transactional
  public Map<String, Object> approve(AuthenticatedPrincipal p, String jobId, String planHash, String baseUrl) {
    CoordJob j = own(p, jobId);
    execute(p, j, planHash, "USER", "승인", "AI 코디네이터 요청 · 승인 " + p.loginId());
    return view(j, baseUrl, true);
  }

  /**
   * 대화창 확인: 사용자가 대화에서 동의한 뒤 AI가 부른다. 승인 화면과 같은 검사(planHash·대기 중·30분·중복 재확인)를 거친다.
   * AI에게는 가린 이름만 돌려준다.
   */
  @Transactional
  public Map<String, Object> confirmFromChat(AuthenticatedPrincipal p, String jobId, String planHash, String baseUrl) {
    CoordJob j = own(p, jobId);
    execute(p, j, planHash, "CHAT", "대화창 확인", "AI 코디네이터 요청 · 대화 확인 " + p.loginId());
    Map<String, Object> out = view(j, baseUrl, false);
    if (CoordJob.COMPLETED.equals(j.getStatus())) {
      out.put("message", (KIND_ATTENDANCE.equals(j.getKind()) ? "기록했습니다. " : "처리했습니다. ") + UNDO_MINUTES + "분 안에는 f2r_undo로 취소할 수 있습니다.");
    }
    return out;
  }

  /**
   * 대화창 취소: 이 작업으로 기록한 출결을 기록 뒤 {@value #UNDO_MINUTES}분 안에만 취소한다. 출결 행은 지우지 않고
   * superseded로 남기며, 취소 사유는 작업 기록(coord_job_events)에 남긴다.
   */
  @Transactional
  public Map<String, Object> undoFromChat(AuthenticatedPrincipal p, String jobId, String reason, String baseUrl) {
    CoordJob j = own(p, jobId);
    LocalDateTime now = LocalDateTime.now(clock);
    if (!CoordJob.COMPLETED.equals(j.getStatus())) throw new IllegalStateException("기록이 끝난 요청만 취소할 수 있습니다.");
    if (now.isAfter(j.getUpdatedAt().plusMinutes(UNDO_MINUTES))) {
      throw new IllegalStateException("기록한 지 " + UNDO_MINUTES + "분이 지나 대화창에서 취소할 수 없습니다. 출결부에서 정정해 주세요.");
    }
    JsonNode result = JSON.readTree(j.getResultJson());
    String what;
    if (KIND_ATTENDANCE.equals(j.getKind())) {
      long eventId = result.get("eventId").asLong();
      attendance.cancel(j.getBusinessId(), eventId);
      what = "출결 #" + eventId;
    } else {
      forKind(j.getKind()).undo(j.getKind(), JSON.readTree(j.getPlanJson()), result);
      what = j.getKind();
    }
    String why = reason == null || reason.isBlank() ? "사용자 요청" : clip(reason.strip(), 200);
    j.moveTo(CoordJob.CANCELLED, now);
    log(j, "CHAT", p.loginId(), CoordJob.COMPLETED, CoordJob.CANCELLED, "대화창 취소 " + what + " · " + why);
    Map<String, Object> out = view(j, baseUrl, false);
    out.put("message", KIND_ATTENDANCE.equals(j.getKind()) ? "출결 기록을 취소했습니다." : "되돌렸습니다.");
    return out;
  }

  /** 승인 공통: planHash가 같고 대기 중일 때만. 실행 직전에 중복을 다시 확인하고, 기록 뒤 다시 읽어 확인한다. */
  private void execute(AuthenticatedPrincipal p, CoordJob j, String planHash, String actor, String label, String note) {
    LocalDateTime now = LocalDateTime.now(clock);
    if (expireIfNeeded(j, now)) throw new IllegalStateException("승인 시간(30분)이 지났습니다. 다시 요청해 주세요.");
    if (!CoordJob.REVIEW_REQUIRED.equals(j.getStatus())) throw new IllegalStateException("이미 처리된 요청입니다.");
    if (planHash == null || !MessageDigest.isEqual(planHash.getBytes(StandardCharsets.UTF_8),
        j.getPlanHash().getBytes(StandardCharsets.UTF_8))) {
      throw new IllegalStateException("화면의 내용이 최신이 아닙니다. 새로고침 후 다시 확인해 주세요.");
    }
    j.moveTo(CoordJob.APPROVED, now);
    log(j, actor, p.loginId(), CoordJob.REVIEW_REQUIRED, CoordJob.APPROVED, label);
    JsonNode plan = JSON.readTree(j.getPlanJson());
    if (!KIND_ATTENDANCE.equals(j.getKind())) {
      try {
        Map<String, Object> result = forKind(j.getKind()).execute(j.getKind(), plan);
        j.complete(JSON.writeValueAsString(result), now);
        log(j, "SERVER", null, CoordJob.APPROVED, CoordJob.COMPLETED, result.get("label") + " 완료");
      } catch (RuntimeException e) {
        j.fail(e.getMessage(), now);
        log(j, "SERVER", null, CoordJob.APPROVED, CoordJob.FAILED, e.getMessage());
      }
      return;
    }
    Long studentId = plan.get("studentId").asLong();
    String type = plan.get("eventType").asText();
    String occurredAt = plan.get("occurredAt").asText();
    Long classId = plan.hasNonNull("classId") ? plan.get("classId").asLong() : null;
    try {
      var dup = attendance.activeEventOn(j.getBusinessId(), studentId, type, LocalDateTime.parse(occurredAt).toLocalDate());
      if (dup.isPresent()) {
        j.fail("그사이 같은 출결이 이미 기록되어 새로 기록하지 않았습니다.", now);
        log(j, "SERVER", null, CoordJob.APPROVED, CoordJob.FAILED, j.getError());
        return;
      }
      EventView saved = attendance.record(j.getBusinessId(), new EasyAttendanceService.RecordForm(classId, studentId, type,
          occurredAt, note));
      boolean verified = attendance.studentHistory(j.getBusinessId(), studentId).stream()
          .anyMatch(e -> e.eventId().equals(saved.eventId()));
      if (!verified) throw new IllegalStateException("기록 후 확인에 실패했습니다.");
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("eventId", saved.eventId());
      result.put("eventType", saved.eventType());
      result.put("label", saved.label());
      result.put("at", saved.occurredAt().format(MINUTE));
      j.complete(JSON.writeValueAsString(result), now);
      log(j, "SERVER", null, CoordJob.APPROVED, CoordJob.COMPLETED, "출결 기록 완료 #" + saved.eventId());
    } catch (RuntimeException e) {
      j.fail(e.getMessage(), now);
      log(j, "SERVER", null, CoordJob.APPROVED, CoordJob.FAILED, e.getMessage());
    }
  }

  @Transactional
  public Map<String, Object> reject(AuthenticatedPrincipal p, String jobId, String baseUrl) {
    CoordJob j = own(p, jobId);
    LocalDateTime now = LocalDateTime.now(clock);
    if (expireIfNeeded(j, now)) return view(j, baseUrl, true);
    if (!CoordJob.REVIEW_REQUIRED.equals(j.getStatus())) throw new IllegalStateException("이미 처리된 요청입니다.");
    j.moveTo(CoordJob.REJECTED, now);
    log(j, "USER", p.loginId(), CoordJob.REVIEW_REQUIRED, CoordJob.REJECTED, "거절");
    return view(j, baseUrl, true);
  }

  /* ---------------- 내부 ---------------- */

  /** forHuman=false(AI 응답)면 실명·학교를 빼고 가린 이름만 넣는다. */
  Map<String, Object> view(CoordJob j, String baseUrl, boolean forHuman) {
    JsonNode plan = JSON.readTree(j.getPlanJson());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("jobId", j.getJobId());
    out.put("kind", j.getKind());
    out.put("status", j.getStatus());
    out.put("createdAt", j.getCreatedAt().format(MINUTE));
    out.put("expiresAt", j.getExpiresAt().format(MINUTE));
    out.put("approvalUrl", (baseUrl == null ? "" : baseUrl) + "/admin/coord/approvals?id=" + j.getJobId());
    Map<String, Object> summary = new LinkedHashMap<>();
    if (!KIND_ATTENDANCE.equals(j.getKind())) {
      summary.put("title", forHuman ? plan.path("title").asText("") : plan.path("aiTitle").asText(""));
      if (forHuman) summary.put("businessName", plan.path("businessName").asText(""));
      out.put("summary", summary);
      if (forHuman || CoordJob.REVIEW_REQUIRED.equals(j.getStatus())) out.put("planHash", j.getPlanHash());
      if (forHuman) { out.put("source", j.getSource()); out.put("clientName", j.getClientName()); }
      if (j.getResultJson() != null && !forHuman) {
        JsonNode r = JSON.readTree(j.getResultJson());
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("label", r.path("label").asText(""));
        // 실행 뒤 사람이 이어서 할 일(답지 사진 올리기 등)이 있는 화면 링크.
        if (r.hasNonNull("linkPath")) brief.put("linkUrl", (baseUrl == null ? "" : baseUrl) + r.get("linkPath").asText());
        out.put("result", brief);
      }
      if (j.getResultJson() != null && forHuman) out.put("result", JSON.readTree(j.getResultJson()));
      if (j.getError() != null) out.put("error", j.getError());
      if (!forHuman && CoordJob.REVIEW_REQUIRED.equals(j.getStatus())) {
        out.put("message", "아직 바꾸지 않았습니다. 대화에서 사용자에게 summary.title을 보여 주고 '이대로 할까요?'라고 물으세요."
            + " 사용자가 분명히 동의하면 f2r_confirm(jobId, planHash)을 부르세요. 사용자 답 없이 부르지 마세요.");
      }
      return out;
    }
    String name = plan.path("studentName").asText("");
    summary.put("student", forHuman ? name : maskName(name));
    if (forHuman) summary.put("school", plan.path("school").asText(""));
    summary.put("grade", plan.hasNonNull("grade") ? plan.get("grade").asInt() : null);
    summary.put("classes", JSON.convertValue(plan.path("classes"), List.class));
    summary.put("eventType", plan.path("eventType").asText());
    summary.put("occurredAt", plan.path("occurredAt").asText());
    if (forHuman) summary.put("businessName", plan.path("businessName").asText(""));
    out.put("summary", summary);
    if (forHuman || CoordJob.REVIEW_REQUIRED.equals(j.getStatus())) out.put("planHash", j.getPlanHash());
    if (forHuman) {
      out.put("source", j.getSource());
      out.put("clientName", j.getClientName());
    }
    if (j.getResultJson() != null) out.put("result", JSON.readTree(j.getResultJson()));
    if (j.getError() != null) out.put("error", j.getError());
    if (!forHuman && CoordJob.REVIEW_REQUIRED.equals(j.getStatus())) {
      out.put("message", "아직 기록하지 않았습니다. 대화에서 사용자에게 summary(가린 이름·학년·구분·시각)를 보여 주고"
          + " '기록할까요?'라고 물으세요. 사용자가 분명히 동의하면 f2r_confirm(jobId, planHash)을 부르세요."
          + " 사용자 답 없이 부르지 마세요. 승인 화면(approvalUrl)에서 승인해도 됩니다.");
    }
    return out;
  }

  private boolean expireIfNeeded(CoordJob j, LocalDateTime now) {
    if (!j.isExpired(now)) return false;
    j.moveTo(CoordJob.EXPIRED, now);
    log(j, "SERVER", null, CoordJob.REVIEW_REQUIRED, CoordJob.EXPIRED, "승인 시간 초과");
    return true;
  }

  private CoordJob own(AuthenticatedPrincipal p, String jobId) {
    requireAdmin(p);
    CoordJob j = jobs.findById(jobId == null ? "" : jobId).orElseThrow(() -> new IllegalArgumentException("요청을 찾을 수 없습니다."));
    if (!j.getUserId().equals(p.userId())) throw new AccessDeniedException("본인 요청만 볼 수 있습니다.");
    access.requireAllowedBusiness(p, j.getBusinessId());
    return j;
  }

  private Long business(AuthenticatedPrincipal p, Long businessId) {
    requireAdmin(p);
    if (businessId != null) {
      access.requireAllowedBusiness(p, businessId);
      return businessId;
    }
    List<EasyBusinessResponse> list = access.allowedBusinesses(p);
    if (list.isEmpty()) throw new IllegalArgumentException("사업체가 없습니다.");
    return list.get(0).businessId();
  }

  private String businessName(AuthenticatedPrincipal p, Long bid) {
    return access.allowedBusinesses(p).stream().filter(b -> b.businessId().equals(bid)).map(EasyBusinessResponse::name)
        .findFirst().orElse("");
  }

  private void log(CoordJob j, String actor, String loginId, String from, String to, String message) {
    events.save(new CoordJobEvent(j.getJobId(), LocalDateTime.now(clock), actor, loginId, from, to, message));
  }

  static void requireAdmin(AuthenticatedPrincipal p) {
    if (p == null || !"ADMIN".equals(p.role())) throw new AccessDeniedException("관리자만 쓸 수 있습니다.");
  }

  static String requireRequestId(String requestId) {
    String v = requestId == null ? "" : requestId.strip();
    if (!v.matches("[A-Za-z0-9-]{8,64}")) throw new IllegalArgumentException("requestId(8~64자 영문·숫자·-)가 필요합니다.");
    return v;
  }

  /** 김민수 → 김○수, 이수 → 이○, 남궁민수 → 남○○수. 한 글자는 ○. */
  static String maskName(String name) {
    if (name == null || name.isBlank()) return "";
    String n = name.strip();
    int len = n.codePointCount(0, n.length());
    if (len == 1) return "○";
    if (len == 2) return n.substring(0, n.offsetByCodePoints(0, 1)) + "○";
    int firstEnd = n.offsetByCodePoints(0, 1);
    int lastStart = n.offsetByCodePoints(0, len - 1);
    return n.substring(0, firstEnd) + "○".repeat(len - 2) + n.substring(lastStart);
  }

  static String sha256(String s) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String clip(String s, int max) {
    return s.length() > max ? s.substring(0, max) : s;
  }
}
