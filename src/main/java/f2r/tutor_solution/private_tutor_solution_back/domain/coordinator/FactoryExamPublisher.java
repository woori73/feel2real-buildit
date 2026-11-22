package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.coordinator.FactoryExamContract.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.coordinator.FactoryExamContract.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpQuestionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 제작 공장의 시험 묶음 제출·읽기와 학생 풀이 페이지 탑재({@link FactoryExamContract}).
 *
 * <p>순서: 제작 세션이 처리 중인 작업에 묶음을 제출(MCP {@code f2r_pc_task_exam_submit}) → 검수 세션 3곳이 같은 묶음을 읽고
 * ({@code f2r_pc_task_exam_read}) PASS → 작업 완료 → 원장이 제작 현황판에서 「게시 승인」하면 {@link #publish}가 {@code tp_exams}·
 * {@code tp_questions}에 status=PUBLISHED로 넣는다. 제출 뒤에 남은 PASS만 센다(제출을 바꾸면 검수를 다시 받아야 한다).
 * 게시 내림은 {@code tp_exams.status=ARCHIVED}(응시 기록이 있으므로 지우지 않는다).
 */
@Service
public class FactoryExamPublisher {

  static final String PUBLISHED = "PUBLISHED";
  static final String ARCHIVED = "ARCHIVED";
  /** 묶음 제출 기록의 머리(coord_job_events actor STAGE, 「EXAM · 시험 코드 · N문항 · 판」). */
  static final String EXAM_MARK = "EXAM";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final CoordJobRepository jobs;
  private final CoordJobEventRepository events;
  private final FactoryExamPackageRepository packages;
  private final TpExamRepository exams;
  private final TpQuestionRepository questions;
  private final Predicate<String> imageExists;
  private final Clock clock;

  @Autowired
  public FactoryExamPublisher(CoordJobRepository jobs, CoordJobEventRepository events, FactoryExamPackageRepository packages,
      TpExamRepository exams, TpQuestionRepository questions) {
    this(jobs, events, packages, exams, questions, path -> new ClassPathResource("static" + path).exists(),
        Clock.system(KstTime.ZONE));
  }

  FactoryExamPublisher(CoordJobRepository jobs, CoordJobEventRepository events, FactoryExamPackageRepository packages,
      TpExamRepository exams, TpQuestionRepository questions, Predicate<String> imageExists, Clock clock) {
    this.jobs = jobs;
    this.events = events;
    this.packages = packages;
    this.exams = exams;
    this.questions = questions;
    this.imageExists = imageExists;
    this.clock = clock;
  }

  /** (제작 세션) 처리 중 작업에 시험 묶음을 제출한다. 다시 내면 바뀌고, 그 앞의 검수 PASS는 게시에 세지 않는다. */
  @Transactional
  public Map<String, Object> submit(AuthenticatedPrincipal p, String jobId, JsonNode a) {
    CoordJob j = own(p, jobId);
    if (!CoordJob.RUNNING.equals(j.getStatus())) throw new IllegalStateException("처리 중인 작업에만 시험 묶음을 낼 수 있습니다(지금: " + j.getStatus() + ").");
    String track = track(j);
    JsonNode exam = a == null ? null : a.get("exam");
    Exam parsed = FactoryExamContract.parse(track, exam, imageExists);
    if (packages.existsByExamCodeAndJobIdNot(parsed.examCode(), j.getJobId())
        || exams.findByExamCode(parsed.examCode()).isPresent()) {
      throw new IllegalArgumentException("이미 쓰이는 시험 코드입니다: " + parsed.examCode() + ". 회차 번호를 바꿔 주세요.");
    }
    String json = JSON.writeValueAsString(parsed);
    String hash = CoordinatorService.sha256(json);
    FactoryExamPackage pkg = packages.findById(j.getJobId()).orElseGet(() -> new FactoryExamPackage(j.getJobId()));
    if (pkg.isPublished()) throw new IllegalStateException("이미 학생 페이지에 게시된 묶음입니다.");
    LocalDateTime now = LocalDateTime.now(clock);
    pkg.submit(track, parsed.examCode(), json, hash, parsed.questions().size(), p.loginId(), now);
    packages.save(pkg);
    events.save(new CoordJobEvent(j.getJobId(), now, FactoryStage.STAGE_ACTOR, p.loginId(), CoordJob.RUNNING, CoordJob.RUNNING,
        EXAM_MARK + " · " + parsed.examCode() + " · " + parsed.questions().size() + "문항 · " + hash.substring(0, 12)));
    Map<String, Object> out = summary(pkg, parsed);
    out.put("message", "시험 묶음을 받았습니다. 검수 세션은 f2r_pc_task_exam_read로 이 묶음(packageHash " + hash.substring(0, 12)
        + ")을 읽고 대조합니다. 묶음을 다시 내면 검수를 다시 받아야 게시할 수 있습니다.");
    return out;
  }

  /** (검수 세션·원장) 제출된 묶음 전체를 읽는다. */
  @Transactional(readOnly = true)
  public Map<String, Object> read(AuthenticatedPrincipal p, String jobId) {
    CoordJob j = own(p, jobId);
    FactoryExamPackage pkg = packages.findById(j.getJobId())
        .orElseThrow(() -> new IllegalArgumentException("이 작업에는 제출된 시험 묶음이 없습니다."));
    Map<String, Object> out = summary(pkg, exam(pkg));
    out.put("exam", JSON.readTree(pkg.getPackageJson()));
    return out;
  }

  /** 현황판 자세히 창에 보일 요약(묶음이 없으면 빈 값). */
  @Transactional(readOnly = true)
  public Optional<Map<String, Object>> summary(String jobId) {
    return packages.findById(jobId).map(pkg -> summary(pkg, exam(pkg)));
  }

  /**
   * 마지막 묶음 제출 기록(STAGE EXAM) 뒤에 남은 서로 다른 검수 PASS 수(그 뒤 FIX가 있으면 FIX 이후). 묶음이 없으면 -1.
   * 시각이 아니라 기록 순서로 가른다(같은 초에 남은 기록도 앞뒤가 정해진다).
   */
  @Transactional(readOnly = true)
  public int passesAfterSubmit(String jobId, List<CoordJobEvent> history) {
    if (!packages.existsById(jobId)) return -1;
    return passesAfterLastSubmit(history);
  }

  static int passesAfterLastSubmit(List<CoordJobEvent> history) {
    int last = -1;
    for (int i = 0; i < history.size(); i++) {
      CoordJobEvent e = history.get(i);
      if (FactoryStage.STAGE_ACTOR.equals(e.getActor()) && EXAM_MARK.equals(FactoryStage.head(e))) last = i;
    }
    return last < 0 ? 0 : CoordPcTaskService.passedReviewers(history.subList(last + 1, history.size())).size();
  }

  /**
   * 학생 풀이 페이지에 넣는다(원장 게시 승인 때 호출). 검수 3곳이 제출 뒤 판을 통과했어야 한다. 학생 페이지 주소를 돌려준다.
   * 시험 코드가 그사이 다른 곳에서 쓰였으면 거절한다.
   */
  @Transactional
  public String publish(String jobId, List<CoordJobEvent> history) {
    FactoryExamPackage pkg = packages.findById(jobId).orElseThrow(() -> new IllegalStateException("제출된 시험 묶음이 없습니다."));
    if (pkg.isPublished()) throw new IllegalStateException("이미 게시된 묶음입니다.");
    requirePublishable(jobId, history);
    Exam e = exam(pkg);
    TpExam row = TpExam.create(e.examCode(), "NAESIN", e.title(), e.subtitle(),
        "실제 시험처럼 풀이 중에는 해설이 없습니다. 제출하면 문항별 채점 결과와 해설을 볼 수 있습니다. (F2R 제작 공장 · 검수 "
            + CoordPcTaskService.REQUIRED_REVIEWS + "곳 통과)", e.timeLimitSec(), PUBLISHED);
    exams.save(row);
    List<TpQuestion> rows = new ArrayList<>();
    for (Question q : e.questions()) {
      rows.add(TpQuestion.create(row.getExamId(), q.section(), q.taskType(), q.prompt(), q.bodyText(),
          JSON.writeValueAsString(q.choices()), q.answerKey(), q.explanation(), q.no(), q.points()));
    }
    questions.saveAll(rows);
    pkg.published(row.getExamId(), LocalDateTime.now(clock));
    return FactoryExamContract.studentUrl(pkg.getTrack());
  }

  /** 게시하기 전 확인: 지금 판으로 검수 3곳, 시험 코드가 그사이 다른 곳에서 쓰이지 않았는지. 어긋나면 이유와 함께 거절. */
  @Transactional(readOnly = true)
  public void requirePublishable(String jobId, List<CoordJobEvent> history) {
    FactoryExamPackage pkg = packages.findById(jobId).orElseThrow(() -> new IllegalStateException("제출된 시험 묶음이 없습니다."));
    if (pkg.isPublished()) return;
    int passed = passesAfterLastSubmit(history);
    if (passed < CoordPcTaskService.REQUIRED_REVIEWS) {
      throw new IllegalStateException("시험 묶음을 낸 뒤의 검수 통과가 " + passed + "곳입니다. 지금 묶음으로 "
          + CoordPcTaskService.REQUIRED_REVIEWS + "곳의 검수를 받아야 게시할 수 있습니다.");
    }
    if (exams.findByExamCode(pkg.getExamCode()).isPresent()) throw new IllegalStateException("이미 쓰이는 시험 코드입니다: " + pkg.getExamCode());
  }

  /** 게시 내림: 학생 페이지에서 내린다(ARCHIVED). 묶음이 없거나 게시 전이면 아무것도 하지 않는다. */
  @Transactional
  public boolean withdraw(String jobId) {
    Optional<FactoryExamPackage> pkg = packages.findById(jobId);
    if (pkg.isEmpty() || !pkg.get().isPublished()) return false;
    exams.findById(pkg.get().getExamId()).ifPresent(x -> x.setStatus(ARCHIVED));
    return true;
  }

  /** 내렸던 묶음을 다시 올린다(같은 시험 행을 PUBLISHED로). */
  @Transactional
  public boolean republish(String jobId) {
    Optional<FactoryExamPackage> pkg = packages.findById(jobId);
    if (pkg.isEmpty() || !pkg.get().isPublished()) return false;
    exams.findById(pkg.get().getExamId()).ifPresent(x -> x.setStatus(PUBLISHED));
    return true;
  }

  boolean hasPackage(String jobId) {
    return packages.existsById(jobId);
  }

  boolean isPublished(String jobId) {
    return packages.findById(jobId).map(FactoryExamPackage::isPublished).orElse(false);
  }

  private static Map<String, Object> summary(FactoryExamPackage pkg, Exam e) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("jobId", pkg.getJobId());
    out.put("track", pkg.getTrack());
    out.put("examCode", pkg.getExamCode());
    out.put("title", e.title());
    out.put("subtitle", e.subtitle());
    out.put("questionCount", pkg.getQuestionCount());
    out.put("maxScore", e.maxScore());
    out.put("timeLimitSec", e.questions().size() * FactoryExamContract.SCREEN_SEC_PER_QUESTION);
    out.put("packageHash", pkg.getPackageHash().substring(0, 12));
    out.put("submittedBy", pkg.getSubmittedBy());
    out.put("submittedAt", String.valueOf(pkg.getSubmittedAt()));
    out.put("published", pkg.isPublished());
    out.put("studentUrl", FactoryExamContract.studentUrl(pkg.getTrack()));
    List<String> stems = new ArrayList<>();
    for (Question q : e.questions().subList(0, Math.min(5, e.questions().size()))) {
      String s = q.prompt().replaceAll("\\[\\[img:[^\\]]*\\]\\]", "[그림]");
      stems.add(q.no() + ". " + (s.length() > 80 ? s.substring(0, 80) + "…" : s));
    }
    out.put("firstQuestions", stems);
    return out;
  }

  private static Exam exam(FactoryExamPackage pkg) {
    return JSON.readValue(pkg.getPackageJson(), Exam.class);
  }

  private static String track(CoordJob j) {
    JsonNode plan = JSON.readTree(j.getPlanJson());
    String t = plan.path("factory").path("track").asText("");
    return t.isBlank() ? FactoryPlan.DEFAULT_TRACK : t;
  }

  private CoordJob own(AuthenticatedPrincipal p, String jobId) {
    CoordinatorService.requireAdmin(p);
    CoordJob j = jobs.findById(jobId == null ? "" : jobId).orElseThrow(() -> new IllegalArgumentException("작업을 찾을 수 없습니다."));
    if (!j.getUserId().equals(p.userId())) throw new AccessDeniedException("본인 작업만 다룰 수 있습니다.");
    if (!CoordPcTaskService.KIND.equals(j.getKind())) throw new IllegalArgumentException("제작 작업이 아닙니다.");
    return j;
  }
}
