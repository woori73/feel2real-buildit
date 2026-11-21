package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.WrongItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentExamAssignment;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentExamResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentExamViewer;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository.StudentExamAssignmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository.StudentExamResultRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository.StudentExamViewerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppCodeService.CodeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpQuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.TestPrepService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재원생 앱 관리(원장 쪽): 시험 배정, 응시 기록 목록·점검, 응시 기록을 볼 사람 지정.
 * <ul>
 *   <li>배정·코드·볼 사람 지정은 원장(ADMIN)만 한다(컨트롤러가 /api/admin/** 아래에 있음).</li>
 *   <li>'새 응시 기록'은 원장과 원장이 지정한 사람만 본다({@link #requireViewer}).</li>
 *   <li>응시 기록은 지우지 않는다. 학생이 그만두어도 남는다.</li>
 * </ul>
 */
@Service
@Transactional
public class StudentExamAdminService {

  private static final TypeReference<List<WrongItem>> WRONG = new TypeReference<>() {};

  private final StudentExamAssignmentRepository assignments;
  private final StudentExamResultRepository results;
  private final StudentExamViewerRepository viewers;
  private final StudentAppCodeService codes;
  private final EasyStudentRepository students;
  private final TpExamRepository exams;
  private final TpQuestionRepository questions;
  private final UserRepository users;
  private final ObjectMapper objectMapper;

  public StudentExamAdminService(StudentExamAssignmentRepository assignments, StudentExamResultRepository results,
      StudentExamViewerRepository viewers, StudentAppCodeService codes, EasyStudentRepository students,
      TpExamRepository exams, TpQuestionRepository questions, UserRepository users, ObjectMapper objectMapper) {
    this.assignments = assignments;
    this.results = results;
    this.viewers = viewers;
    this.codes = codes;
    this.students = students;
    this.exams = exams;
    this.questions = questions;
    this.users = users;
    this.objectMapper = objectMapper;
  }

  public record ExamOption(String examCode, String title, long questionCount) {}

  public record AssignedExam(String examCode, String title, String assignedAt) {}

  public record StudentRow(Long studentId, String name, String school, Integer grade, CodeStatus code, int assignedCount) {}

  public record StudentPanel(Long studentId, String name, String status, CodeStatus code, List<AssignedExam> assignments,
      List<ResultView> results) {}

  public record ResultView(Long resultId, Long studentId, String studentName, String examCode, String examTitle,
      int attemptNo, String submittedAt, int durationSec, Integer score, Integer maxScore, int totalCount, int correctCount,
      List<WrongItem> wrongItems, String checkedAt, Long gradingId) {}

  public record ViewerView(Long userId, String loginId, String name, String role, String grantedAt) {}

  /* ---------------- 원장: 학생·코드·배정 ---------------- */

  @Transactional(readOnly = true)
  public List<StudentRow> students(Long businessId) {
    return students.findAllByStatus(EasyStudentStatus.ENROLLED).stream()
        .sorted((a, b) -> String.valueOf(a.getName()).compareTo(String.valueOf(b.getName())))
        .map(s -> new StudentRow(s.getStudentId(), s.getName(), s.getSchool(), s.getGrade() == null ? null : s.getGrade().intValue(),
            codes.status(businessId, s.getStudentId()),
            assignments.findByBusinessIdAndStudentIdAndRemovedAtIsNullOrderByAssignedAtDesc(businessId, s.getStudentId()).size()))
        .toList();
  }

  @Transactional(readOnly = true)
  public StudentPanel panel(Long businessId, Long studentId) {
    EasyStudent s = student(studentId);
    Map<String, TpExam> byCode = examMap();
    List<AssignedExam> list = assignments.findByBusinessIdAndStudentIdAndRemovedAtIsNullOrderByAssignedAtDesc(businessId, studentId)
        .stream().map(a -> new AssignedExam(a.getExamCode(), titleOf(byCode, a.getExamCode()), str(a.getAssignedAt()))).toList();
    List<ResultView> done = results.findByBusinessIdAndStudentIdOrderBySubmittedAtDesc(businessId, studentId).stream()
        .map(r -> view(r, s.getName())).toList();
    return new StudentPanel(s.getStudentId(), s.getName(), s.getStatus().name(), codes.status(businessId, studentId), list, done);
  }

  /** 배정할 수 있는 시험: 공개된 내신 디지털 모의고사. */
  @Transactional(readOnly = true)
  public List<ExamOption> exams() {
    return exams.findByKindAndStatusOrderByExamIdAsc(TestPrepService.KIND_NAESIN, "PUBLISHED").stream()
        .map(e -> new ExamOption(e.getExamCode(), e.getTitle(), questions.countByExamId(e.getExamId()))).toList();
  }

  public StudentPanel assign(Long businessId, Long studentId, String examCode, Long by) {
    EasyStudent s = student(studentId);
    if (s.getStatus() != EasyStudentStatus.ENROLLED) throw new IllegalArgumentException("재원 중인 학생에게만 시험을 배정합니다.");
    TpExam exam = exams.findByExamCodeAndStatus(examCode, "PUBLISHED")
        .filter(e -> TestPrepService.KIND_NAESIN.equals(e.getKind()))
        .orElseThrow(() -> new IllegalArgumentException("배정할 수 있는 시험이 아닙니다."));
    LocalDateTime now = LocalDateTime.now();
    assignments.findByBusinessIdAndStudentIdAndExamCode(businessId, studentId, exam.getExamCode())
        .ifPresentOrElse(a -> { if (!a.isActive()) a.restore(by, now); },
            () -> assignments.save(new StudentExamAssignment(businessId, studentId, exam.getExamCode(), by, now)));
    return panel(businessId, studentId);
  }

  /** 배정을 뺀다. 이미 본 응시 기록은 그대로 남는다. */
  public StudentPanel unassign(Long businessId, Long studentId, String examCode) {
    assignments.findByBusinessIdAndStudentIdAndExamCode(businessId, studentId, examCode)
        .ifPresent(a -> a.remove(LocalDateTime.now()));
    return panel(businessId, studentId);
  }

  /* ---------------- 원장·지정한 사람: 응시 기록 ---------------- */

  /** 원장(ADMIN)이거나, 원장이 이 사업체에 지정한 사람이어야 한다. */
  @Transactional(readOnly = true)
  public void requireViewer(AuthenticatedPrincipal p, Long businessId) {
    if (!canView(p, businessId)) throw new AccessDeniedException("원장님이 지정한 사람만 응시 기록을 볼 수 있습니다.");
  }

  @Transactional(readOnly = true)
  public boolean canView(AuthenticatedPrincipal p, Long businessId) {
    if (p == null || p.userId() == null || businessId == null) return false;
    if ("ADMIN".equals(p.role())) return true;
    return viewers.findByBusinessIdAndUserId(businessId, p.userId()).filter(StudentExamViewer::isActive).isPresent();
  }

  /** 지정받은 사업체 목록(원장이 아닌 사람이 응시 기록 화면을 열 때). */
  @Transactional(readOnly = true)
  public List<Long> viewerBusinesses(AuthenticatedPrincipal p) {
    if (p == null || p.userId() == null) return List.of();
    return viewers.findByUserIdAndRevokedAtIsNull(p.userId()).stream().map(StudentExamViewer::getBusinessId).toList();
  }

  @Transactional(readOnly = true)
  public List<ResultView> results(Long businessId, boolean onlyNew) {
    List<StudentExamResult> list = onlyNew ? results.findByBusinessIdAndCheckedAtIsNullOrderBySubmittedAtDesc(businessId)
        : results.findTop200ByBusinessIdOrderBySubmittedAtDesc(businessId);
    Map<Long, String> names = students.findAllById(list.stream().map(StudentExamResult::getStudentId).distinct().toList())
        .stream().collect(Collectors.toMap(EasyStudent::getStudentId, s -> String.valueOf(s.getName())));
    return list.stream().map(r -> view(r, names.getOrDefault(r.getStudentId(), ""))).toList();
  }

  @Transactional(readOnly = true)
  public long newCount(Long businessId) {
    return results.countByBusinessIdAndCheckedAtIsNull(businessId);
  }

  /** 점검 표시: '새 응시 기록'에서 빠진다. 기록 내용은 바꾸지 않는다. */
  public ResultView check(Long businessId, Long resultId, Long by) {
    StudentExamResult r = results.findById(resultId).filter(x -> x.getBusinessId().equals(businessId))
        .orElseThrow(() -> new IllegalArgumentException("응시 기록을 찾을 수 없습니다."));
    r.check(by, LocalDateTime.now());
    return view(r, students.findById(r.getStudentId()).map(EasyStudent::getName).orElse(""));
  }

  /* ---------------- 원장: 볼 사람 지정 ---------------- */

  @Transactional(readOnly = true)
  public List<ViewerView> viewers(Long businessId) {
    return viewers.findByBusinessIdAndRevokedAtIsNullOrderByGrantedAtAsc(businessId).stream()
        .map(v -> users.findById(v.getUserId()).map(u -> viewerView(u, v)).orElse(null))
        .filter(v -> v != null).toList();
  }

  public List<ViewerView> addViewer(Long businessId, String loginId, Long by) {
    User u = users.findByLoginId(loginId == null ? "" : loginId.strip())
        .orElseThrow(() -> new IllegalArgumentException("그 아이디의 회원을 찾을 수 없습니다."));
    if (!"ADMIN".equals(u.getRole().name()) && !"TUTOR".equals(u.getRole().name())) {
      throw new IllegalArgumentException("강사(TUTOR) 계정만 지정할 수 있습니다.");
    }
    LocalDateTime now = LocalDateTime.now();
    viewers.findByBusinessIdAndUserId(businessId, u.getUserId())
        .ifPresentOrElse(v -> { if (!v.isActive()) v.restore(by, now); },
            () -> viewers.save(new StudentExamViewer(businessId, u.getUserId(), by, now)));
    return viewers(businessId);
  }

  public List<ViewerView> removeViewer(Long businessId, Long userId) {
    viewers.findByBusinessIdAndUserId(businessId, userId).ifPresent(v -> v.revoke(LocalDateTime.now()));
    return viewers(businessId);
  }

  /* ---------------- 내부 ---------------- */

  private ViewerView viewerView(User u, StudentExamViewer v) {
    return new ViewerView(u.getUserId(), u.getLoginId(), u.getName(), u.getRole().name(), str(v.getGrantedAt()));
  }

  ResultView view(StudentExamResult r, String name) {
    List<WrongItem> wrong;
    try {
      wrong = r.getWrongItems() == null ? List.of() : objectMapper.readValue(r.getWrongItems(), WRONG);
    } catch (Exception e) {
      wrong = List.of();
    }
    return new ResultView(r.getResultId(), r.getStudentId(), name, r.getExamCode(), r.getExamTitle(), r.getAttemptNo(),
        str(r.getSubmittedAt()), r.getDurationSec(), r.getScore(), r.getMaxScore(), r.getTotalCount(), r.getCorrectCount(),
        wrong, str(r.getCheckedAt()), r.getGradingId());
  }

  private Map<String, TpExam> examMap() {
    return exams.findByKindAndStatusOrderByExamIdAsc(TestPrepService.KIND_NAESIN, "PUBLISHED").stream()
        .collect(Collectors.toMap(TpExam::getExamCode, Function.identity(), (a, b) -> a));
  }

  private static String titleOf(Map<String, TpExam> byCode, String code) {
    TpExam e = byCode.get(code);
    return e == null ? code + "(내려간 시험)" : e.getTitle();
  }

  private EasyStudent student(Long studentId) {
    return students.findById(studentId).orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
  }

  private static String str(LocalDateTime t) {
    return t == null ? null : t.withNano(0).toString();
  }
}
