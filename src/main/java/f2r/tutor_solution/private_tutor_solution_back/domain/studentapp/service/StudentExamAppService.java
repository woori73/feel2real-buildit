package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.GradingForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.WrongItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentAppCode;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentExamAssignment;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentExamResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository.StudentExamAssignmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository.StudentExamResultRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.AttemptSummary;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.AttemptView;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.ReviewItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.ReviewView;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.SaveAnswerRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.Scorecard;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.StartAttemptRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.SubmitRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpAttempt;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpAttemptRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpQuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.TestPrepService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 재원생 앱(/naesintest/app)의 학생 쪽 기능.
 * <ul>
 *   <li>첫 화면: 원장이 배정한 시험과 내 응시 기록만 보인다(다른 학생 결과는 볼 수 없음).</li>
 *   <li>응시·답 저장·채점·해설은 기존 {@link TestPrepService}를 그대로 쓴다. 배정된 시험만 시작할 수 있다.</li>
 *   <li>제출하면 응시 기록(회차·응시 일시·점수·문항별 정오·오답·걸린 시간·몇 번째 응시)을 남기고,
 *       학생부 채점 기록(source=APP)에도 한 줄 넣는다. 원장은 '새 응시 기록'에서 점검한다.</li>
 * </ul>
 */
@Service
@Transactional
public class StudentExamAppService {

  public static final String SOURCE = "APP";
  static final String SUBJECT = "디지털 모의고사";
  private static final String[] CIRC = {"①", "②", "③", "④", "⑤"};
  private static final Pattern POINT = Pattern.compile("^어법 포인트\\s*:\\s*(.+)$", Pattern.MULTILINE);
  // 해설 첫 줄 "정답 ② · 발음부호 (3점)" → "발음부호"
  private static final Pattern HEAD = Pattern.compile("^정답[^·\\n]*·\\s*([^\\n]+?)\\s*(?:\\(\\d+점\\))?\\s*$", Pattern.MULTILINE);

  private final StudentAppCodeService codes;
  private final StudentExamAssignmentRepository assignments;
  private final StudentExamResultRepository results;
  private final TestPrepService testPrep;
  private final TpExamRepository exams;
  private final TpQuestionRepository questions;
  private final TpAttemptRepository attempts;
  private final EasyLessonRecordService lessonRecords;
  private final EasyStudentRepository students;
  private final ObjectMapper objectMapper;

  public StudentExamAppService(StudentAppCodeService codes, StudentExamAssignmentRepository assignments,
      StudentExamResultRepository results, TestPrepService testPrep, TpExamRepository exams, TpQuestionRepository questions,
      TpAttemptRepository attempts, EasyLessonRecordService lessonRecords, EasyStudentRepository students, ObjectMapper objectMapper) {
    this.codes = codes;
    this.assignments = assignments;
    this.results = results;
    this.testPrep = testPrep;
    this.exams = exams;
    this.questions = questions;
    this.attempts = attempts;
    this.lessonRecords = lessonRecords;
    this.students = students;
    this.objectMapper = objectMapper;
  }

  /** kind: 시험 종류(NAESIN·TOEIC·PRACTICE·VOCAB·SAT). 앱이 종류에 맞는 시험·해설 화면을 고르는 데 쓴다. */
  public record AppExam(String examCode, String title, long questionCount, long attempts, Integer lastScore, Integer lastMax,
      String lastSubmittedAt, Long inProgressAttemptId, String kind) {}

  public record AppResult(Long attemptId, String examCode, String examTitle, int attemptNo, String submittedAt,
      int durationSec, Integer score, Integer maxScore, int totalCount, int correctCount, String kind) {}

  public record AppMe(String studentName, List<AppExam> exams, List<AppResult> results) {}

  @Transactional(readOnly = true)
  public AppMe me(AuthenticatedPrincipal p) {
    StudentAppCode code = session(p);
    List<StudentExamResult> mine = results.findByBusinessIdAndStudentIdOrderBySubmittedAtDesc(code.getBusinessId(), code.getStudentId());
    List<AttemptSummary> history = testPrep.history(p);
    List<AppExam> list = new ArrayList<>();
    for (StudentExamAssignment a : assignments.findByBusinessIdAndStudentIdAndRemovedAtIsNullOrderByAssignedAtDesc(
        code.getBusinessId(), code.getStudentId())) {
      Optional<TpExam> exam = exams.findByExamCodeAndStatus(a.getExamCode(), "PUBLISHED");
      if (exam.isEmpty()) continue; // 내려간 시험은 보이지 않는다
      List<StudentExamResult> done = mine.stream().filter(r -> r.getExamCode().equals(a.getExamCode())).toList();
      StudentExamResult last = done.isEmpty() ? null : done.get(0);
      Long inProgress = history.stream().filter(h -> a.getExamCode().equals(h.examCode()) && "IN_PROGRESS".equals(h.status()))
          .map(AttemptSummary::attemptId).findFirst().orElse(null);
      list.add(new AppExam(a.getExamCode(), exam.get().getTitle(), questions.countByExamId(exam.get().getExamId()), done.size(),
          last == null ? null : last.getScore(), last == null ? null : last.getMaxScore(),
          last == null ? null : str(last.getSubmittedAt()), inProgress, exam.get().getKind()));
    }
    String name = students.findById(code.getStudentId()).map(s -> s.getName()).orElse("");
    Map<String, String> kinds = new HashMap<>();
    for (StudentExamResult r : mine) {
      kinds.computeIfAbsent(r.getExamCode(), c -> exams.findByExamCode(c).map(TpExam::getKind).orElse(TestPrepService.KIND_NAESIN));
    }
    return new AppMe(name, list, mine.stream().map(r -> view(r, kinds.get(r.getExamCode()))).toList());
  }

  /** 배정된 시험만 시작한다. 풀던 응시가 있으면 이어서 푼다. */
  public AttemptView start(AuthenticatedPrincipal p, String examCode) {
    StudentAppCode code = session(p);
    StudentExamAssignment a = assignments.findByBusinessIdAndStudentIdAndExamCode(code.getBusinessId(), code.getStudentId(), examCode)
        .filter(StudentExamAssignment::isActive)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "원장님이 배정한 시험만 볼 수 있습니다."));
    Optional<Long> inProgress = testPrep.history(p).stream()
        .filter(h -> a.getExamCode().equals(h.examCode()) && "IN_PROGRESS".equals(h.status()))
        .map(AttemptSummary::attemptId).findFirst();
    if (inProgress.isPresent()) return testPrep.resume(p, inProgress.get());
    return testPrep.start(p, new StartAttemptRequest(a.getExamCode(), null));
  }

  @Transactional(readOnly = true)
  public AttemptView resume(AuthenticatedPrincipal p, Long attemptId) {
    session(p);
    return testPrep.resume(p, attemptId);
  }

  public void saveAnswer(AuthenticatedPrincipal p, Long attemptId, SaveAnswerRequest req) {
    session(p);
    testPrep.saveAnswer(p, attemptId, req);
  }

  /** 채점하고 바로 응시 기록·학생부 채점 기록을 남긴다(같은 응시를 두 번 제출해도 기록은 한 번). */
  public Scorecard submit(AuthenticatedPrincipal p, Long attemptId, SubmitRequest req) {
    session(p);
    Scorecard card = testPrep.submit(p, attemptId, req);
    record(p, attemptId);
    return card;
  }

  @Transactional(readOnly = true)
  public ReviewView review(AuthenticatedPrincipal p, Long attemptId) {
    session(p);
    return testPrep.review(p, attemptId);
  }

  /** 내 응시 목록(해설 화면의 '목록으로' 주소를 찾을 때 쓴다). */
  @Transactional(readOnly = true)
  public List<AttemptSummary> history(AuthenticatedPrincipal p) {
    session(p);
    return testPrep.history(p);
  }

  StudentExamResult record(AuthenticatedPrincipal p, Long attemptId) {
    Optional<StudentExamResult> existing = results.findByAttemptId(attemptId);
    if (existing.isPresent()) return existing.get();
    StudentAppCode code = session(p);
    TpAttempt attempt = attempts.findById(attemptId)
        .filter(a -> a.getUserId().equals(p.userId()) && "SUBMITTED".equals(a.getStatus()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "응시를 찾을 수 없습니다."));
    TpExam exam = exams.findById(attempt.getExamId()).orElseThrow();
    ReviewView review = testPrep.review(p, attemptId);
    int total = review.items().size();
    int correct = (int) review.items().stream().filter(i -> Boolean.TRUE.equals(i.isCorrect())).count();
    List<WrongItem> wrong = review.items().stream().filter(i -> !Boolean.TRUE.equals(i.isCorrect()))
        .map(StudentExamAppService::wrongItem).toList();
    int attemptNo = (int) results.countByBusinessIdAndStudentIdAndExamCode(code.getBusinessId(), code.getStudentId(), exam.getExamCode()) + 1;
    LocalDateTime submittedAt = attempt.getSubmittedAt();
    int durationSec = (int) Math.max(0, Duration.between(attempt.getStartedAt(), submittedAt).getSeconds());
    String title = clip(exam.getTitle(), 150);
    String feedback = "재원생 앱 자동 기록 · " + attemptNo + "번째 응시"
        + (review.totalScaled() == null ? "" : " · 점수 " + review.totalScaled() + "/" + review.totalMax())
        + " · 걸린 시간 " + minSec(durationSec);
    var grading = lessonRecords.createGrading(code.getBusinessId(), code.getStudentId(), new GradingForm(null,
        submittedAt.toLocalDate().toString(), SUBJECT, title + " · " + attemptNo + "번째 응시", total, correct, wrong, feedback,
        "GRADED"), SOURCE);
    return results.save(StudentExamResult.builder().businessId(code.getBusinessId()).studentId(code.getStudentId())
        .attemptId(attemptId).gradingId(grading.gradingId()).examCode(exam.getExamCode()).examTitle(clip(exam.getTitle(), 200))
        .attemptNo(attemptNo).startedAt(attempt.getStartedAt()).submittedAt(submittedAt).durationSec(durationSec)
        .score(review.totalScaled()).maxScore(review.totalMax()).totalCount(total).correctCount(correct)
        .wrongItems(json(wrong)).createdAt(LocalDateTime.now()).build());
  }

  /** 요청마다 코드가 아직 쓰이는지 확인한다(폐기·재발급·퇴원이면 401). */
  StudentAppCode session(AuthenticatedPrincipal p) {
    return codes.activeSession(StudentAppPrincipals.codeId(p), StudentAppPrincipals.studentId(p))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "접속 코드로 다시 로그인해 주세요."));
  }

  static WrongItem wrongItem(ReviewItem i) {
    String reason = "내 답 " + circ(i.yourAnswer()) + " · 정답 " + circ(i.correctAnswer());
    return new WrongItem(String.valueOf(i.orderNo()), topic(i.explanation()), reason);
  }

  /** 해설의 '어법 포인트:' 줄을 오답 유형으로 쓰고, 없으면 첫 줄 '정답 ② · 유형 (3점)'의 유형을 쓴다. 둘 다 없으면 빈 값. */
  static String topic(String explanation) {
    if (explanation == null) return "";
    Matcher m = POINT.matcher(explanation);
    if (m.find()) return clip(m.group(1).strip(), 100);
    Matcher h = HEAD.matcher(explanation);
    return h.find() ? clip(h.group(1).strip(), 100) : "";
  }

  static String circ(String letters) {
    if (letters == null || letters.isBlank()) return "없음";
    StringBuilder sb = new StringBuilder();
    for (String l : letters.split(",")) {
      int k = "ABCDE".indexOf(l.strip());
      if (sb.length() > 0) sb.append(", ");
      sb.append(k >= 0 && l.strip().length() == 1 ? CIRC[k] : l.strip());
    }
    return sb.toString();
  }

  static String minSec(int sec) {
    return (sec / 60) + "분 " + (sec % 60) + "초";
  }

  static AppResult view(StudentExamResult r, String kind) {
    return new AppResult(r.getAttemptId(), r.getExamCode(), r.getExamTitle(), r.getAttemptNo(), str(r.getSubmittedAt()),
        r.getDurationSec(), r.getScore(), r.getMaxScore(), r.getTotalCount(), r.getCorrectCount(), kind);
  }

  private String json(List<WrongItem> items) {
    try {
      return objectMapper.writeValueAsString(items);
    } catch (Exception e) {
      return "[]";
    }
  }

  private static String clip(String v, int max) {
    if (v == null) return "";
    return v.length() <= max ? v : v.substring(0, max);
  }

  static String str(LocalDateTime t) {
    return t == null ? null : t.withNano(0).toString();
  }
}
