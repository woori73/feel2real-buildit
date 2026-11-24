package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 「Claude와 함께 문제 제작」 제작 현황판(/dashboard/factory, 대표 지시 2026-11-22).
 *
 * <p>새 대기열을 만들지 않는다 — 기존 제작 작업 대기열({@link CoordPcTaskService}, {@code coord_jobs} kind=PC_TASK)을 화면으로 보여 주고,
 * 화면에서 요청을 올리고(같은 {@code request}), 공정 6단계({@link FactoryStage})와 검수 의견을 기록에서 읽어 낸다. Claude는 서버가 실행하지
 * 않는다: 화면은 Claude 세션에 붙여 넣을 문장을 건네고, 세션이 MCP({@code f2r_pc_task_*})로 처리한다.
 *
 * <p>게시: 완료된 작업을 원장이 승인한다 — 현황판 「게시 승인」 또는 대화(MCP {@code f2r_pc_task_publish}, {@link #publishFromChat}). 제작 세션이 디지털 모의고사 시험 묶음을 냈으면({@link FactoryExamPublisher}) 승인과 함께
 * 서버가 학생 풀이 페이지(tp_exams·tp_questions, status=PUBLISHED)에 바로 올리고 「게시됨」으로 표시한다. 묶음이 없으면(공인시험 세트 등)
 * 승인(APPROVED) 뒤 탑재 PR로 올리고 「게시됨으로 표시」한다 — docs/lesson-prep/FACTORY.md 「게시 절차」.
 * 게시 기록은 {@code coord_job_events}(actor = PUBLISH)에 쌓는다. 권한: 대기열과 같이 ADMIN, 본인 작업만.
 */
@Service
public class CoordFactoryService {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  private final CoordPcTaskService pcTasks;
  private final CoordJobRepository jobs;
  private final CoordJobEventRepository events;
  private final FactoryExamPublisher publisher;
  private final FactoryExamUploader uploader;
  private final Clock clock;

  @Autowired
  public CoordFactoryService(CoordPcTaskService pcTasks, CoordJobRepository jobs, CoordJobEventRepository events,
      FactoryExamPublisher publisher, FactoryExamUploader uploader) {
    this(pcTasks, jobs, events, publisher, uploader, Clock.system(KstTime.ZONE));
  }

  CoordFactoryService(CoordPcTaskService pcTasks, CoordJobRepository jobs, CoordJobEventRepository events,
      FactoryExamPublisher publisher, FactoryExamUploader uploader, Clock clock) {
    this.publisher = publisher;
    this.uploader = uploader;
    this.pcTasks = pcTasks;
    this.jobs = jobs;
    this.events = events;
    this.clock = clock;
  }

  /** 현황판 한 장: 공정 6단계(단계별 작업 수) + 작업 목록(최근 것부터) + 요청 화면의 고르기 값. */
  @Transactional(readOnly = true)
  public Map<String, Object> board(AuthenticatedPrincipal p) {
    CoordinatorService.requireAdmin(p);
    List<CoordJob> list = jobs.findTop100ByUserIdAndKindOrderByCreatedAtDesc(p.userId(), CoordPcTaskService.KIND);
    Map<String, List<CoordJobEvent>> byJob = list.isEmpty() ? Map.of()
        : events.findAllByJobIdInOrderByAtAscEventIdAsc(list.stream().map(CoordJob::getJobId).toList()).stream()
            .collect(Collectors.groupingBy(CoordJobEvent::getJobId));
    List<Map<String, Object>> rows = new ArrayList<>();
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (String s : FactoryStage.LABEL.keySet()) counts.put(s, 0);
    for (CoordJob j : list) {
      Map<String, Object> row = withPackage(row(j, byJob.getOrDefault(j.getJobId(), List.of())), j.getJobId(),
          byJob.getOrDefault(j.getJobId(), List.of()));
      rows.add(row);
      if (row.get("stage") != null) counts.merge((String) row.get("stage"), 1, Integer::sum);
    }
    List<Map<String, Object>> stages = new ArrayList<>();
    int no = 1;
    for (var e : FactoryStage.LABEL.entrySet()) {
      Map<String, Object> s = new LinkedHashMap<>();
      s.put("no", no++);
      s.put("code", e.getKey());
      s.put("label", e.getValue());
      s.put("count", counts.get(e.getKey()));
      stages.add(s);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("stages", stages);
    out.put("jobs", rows);
    out.put("options", options());
    out.put("principle", FactoryPlan.ORIGINALITY);
    return out;
  }

  /** 화면에서 제작 요청 올리기 — 기존 대기열에 그대로 들어간다. 과목·시험 구분은 꼭 고르고, 검수 없는 종류(OTHER)는 받지 않는다. */
  @Transactional
  public Map<String, Object> create(AuthenticatedPrincipal p, JsonNode a) {
    CoordinatorService.requireAdmin(p);
    if (a == null || !a.hasNonNull("track") || a.get("track").asText().isBlank()) {
      throw new IllegalArgumentException("과목·시험 구분을 골라 주세요.");
    }
    if (!CoordPcTaskService.REVIEWED_TYPES.contains(a.path("type").asText("").strip().toUpperCase())) {
      throw new IllegalArgumentException("자료 종류는 학습지·예상문제·직보·주간 시험지 중에서 고릅니다(모두 검수 "
          + CoordPcTaskService.REQUIRED_REVIEWS + "곳을 거칩니다).");
    }
    String jobId = (String) pcTasks.request(p, "WEB", "제작 현황판", a).get("jobId");
    return one(p, jobId);
  }

  /** 제작 지시서(md) — Claude 세션이 받는 것과 같은 글. */
  @Transactional(readOnly = true)
  public Map<String, Object> brief(AuthenticatedPrincipal p, String jobId) {
    return pcTasks.brief(p, jobId);
  }

  /** 대기 중인 작업 취소(기존 대기열 규칙 그대로: 누가 가져간 뒤에는 못 한다). */
  @Transactional
  public Map<String, Object> cancel(AuthenticatedPrincipal p, String jobId) {
    pcTasks.cancel(p, jobId);
    return one(p, jobId);
  }

  /**
   * 원장 게시 승인: 완료 + 검수 통과 수를 채운 작업만. 시험 묶음이 있으면 학생 풀이 페이지에 바로 올리고 「게시됨」이 된다
   * (묶음을 낸 뒤의 검수 통과가 3곳이어야 한다). 묶음이 없으면 「탑재 대기」가 된다.
   */
  @Transactional
  public Map<String, Object> approvePublish(AuthenticatedPrincipal p, String jobId) {
    CoordJob j = completed(p, jobId);
    List<CoordJobEvent> history = events.findAllByJobIdOrderByAtAscEventIdAsc(jobId);
    if (!"READY".equals(FactoryStage.publishState(history))) throw new IllegalStateException("이미 게시 승인했거나 게시된 작업입니다.");
    int passed = CoordPcTaskService.passedReviewers(history).size();
    if (passed < CoordPcTaskService.REQUIRED_REVIEWS) {
      throw new IllegalStateException("검수 통과가 " + CoordPcTaskService.REQUIRED_REVIEWS + "곳 필요합니다(이 작업은 " + passed
          + "곳). 다시 제작 요청을 올려 검수를 받아 주세요.");
    }
    if (publisher.hasPackage(jobId)) publisher.requirePublishable(jobId, history);
    log(j, p, "APPROVED");
    if (publisher.hasPackage(jobId)) {
      String url = publisher.isPublished(jobId) ? (publisher.republish(jobId) ? republishedUrl(jobId) : "")
          : publisher.publish(jobId, history);
      log(j, p, "PUBLISHED · " + url);
    }
    return one(p, jobId);
  }

  /** 대화 게시 승인 토큰의 유효 시간. */
  static final long CHAT_CONFIRM_SECONDS = 30 * 60;
  private final byte[] chatSecret = randomSecret();

  /**
   * 대화로 게시 승인(원장 지시 2026-11-24 「대화하면서 승인하면 업로드」, MCP {@code f2r_pc_task_publish}).
   * confirmToken 없이 부르면 올리지 않고, 지금 승인할 수 있는지 확인한 뒤 무엇을 올릴지(summary)와 토큰을 준다. 원장이 대화에서 분명히
   * 동의하면 같은 jobId + 토큰으로 다시 불러 화면의 「게시 승인」({@link #approvePublish})과 똑같이 올린다.
   * 토큰은 30분 동안, 그 원장·그 작업·그 시험 묶음 판에만 맞는다(그사이 묶음을 다시 내면 다시 확인받는다).
   */
  @Transactional
  public Map<String, Object> publishFromChat(AuthenticatedPrincipal p, String jobId, String confirmToken) {
    CoordJob j = completed(p, jobId);
    List<CoordJobEvent> history = events.findAllByJobIdOrderByAtAscEventIdAsc(jobId);
    if (!"READY".equals(FactoryStage.publishState(history))) throw new IllegalStateException("이미 게시 승인했거나 게시된 작업입니다.");
    int passed = CoordPcTaskService.passedReviewers(history).size();
    if (passed < CoordPcTaskService.REQUIRED_REVIEWS) {
      throw new IllegalStateException("검수 통과가 " + CoordPcTaskService.REQUIRED_REVIEWS + "곳 필요합니다(이 작업은 " + passed + "곳).");
    }
    Map<String, Object> pkg = publisher.summary(jobId).orElse(null);
    if (pkg != null) publisher.requirePublishable(jobId, history);
    String subject = p.userId() + "|" + jobId + "|" + (pkg == null ? "" : pkg.get("packageHash"));
    String token = confirmToken == null ? "" : confirmToken.strip();
    if (token.isEmpty()) {
      String title = JSON.readTree(j.getPlanJson()).path("title").asText().replaceFirst("^제작 작업 · ", "");
      String summary = pkg == null
          ? title + " — 시험 묶음이 없어 게시 승인만 합니다(학생 페이지 탑재는 따로)."
          : title + " — 「" + pkg.get("title") + "」 " + pkg.get("subtitle") + " · " + pkg.get("questionCount") + "문항 · 시험 코드 "
              + pkg.get("examCode") + "을 학생 풀이 페이지(" + pkg.get("studentUrl") + ")에 올립니다.";
      long expires = clock.instant().getEpochSecond() + CHAT_CONFIRM_SECONDS;
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("status", "needs_confirmation");
      out.put("jobId", jobId);
      out.put("summary", summary);
      out.put("confirm_token", expires + "." + sign(subject, expires));
      out.put("message", "아직 올리지 않았습니다. summary를 원장님께 보여 주고 분명히 동의하면 같은 jobId에 confirm_token만 더해 다시 부르세요."
          + " 원장님 답 없이 다시 부르지 마세요.");
      return out;
    }
    verifyToken(token, subject);
    Map<String, Object> out = approvePublish(p, jobId);
    out.put("message", "PUBLISHED".equals(out.get("state")) ? "학생 풀이 페이지에 올렸습니다." : "게시 승인했습니다(탑재 대기).");
    return out;
  }

  private String sign(String subject, long expires) {
    try {
      javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(chatSecret, "HmacSHA256"));
      return java.util.HexFormat.of().formatHex(mac.doFinal((subject + "|" + expires).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private void verifyToken(String token, String subject) {
    String[] parts = token.split("\\.", 2);
    long expires;
    try {
      expires = Long.parseLong(parts[0]);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("confirm_token 형식이 맞지 않습니다. 토큰 없이 다시 불러 원장님 확인을 받으세요.");
    }
    if (parts.length != 2) throw new IllegalArgumentException("confirm_token 형식이 맞지 않습니다. 토큰 없이 다시 불러 원장님 확인을 받으세요.");
    if (expires < clock.instant().getEpochSecond()) {
      throw new IllegalArgumentException("확인 시간(30분)이 지났습니다. 토큰 없이 다시 불러 원장님 확인을 받으세요.");
    }
    if (!java.security.MessageDigest.isEqual(parts[1].getBytes(java.nio.charset.StandardCharsets.UTF_8),
        sign(subject, expires).getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
      throw new IllegalArgumentException("확인한 내용과 올리려는 내용이 다릅니다. 토큰 없이 다시 불러 원장님 확인을 받으세요.");
    }
  }

  private static byte[] randomSecret() {
    byte[] b = new byte[32];
    new java.security.SecureRandom().nextBytes(b);
    return b;
  }

  private String republishedUrl(String jobId) {
    return publisher.summary(jobId).map(m -> (String) m.get("studentUrl")).orElse("");
  }

  /** 학생 풀이 페이지에 실제로 올린 뒤 「게시됨」으로 표시. url은 우리 사이트 안 주소(/로 시작)만 받는다. */
  @Transactional
  public Map<String, Object> markPublished(AuthenticatedPrincipal p, String jobId, String url) {
    CoordJob j = completed(p, jobId);
    if (!"APPROVED".equals(FactoryStage.publishState(events.findAllByJobIdOrderByAtAscEventIdAsc(jobId)))) {
      throw new IllegalStateException("게시 승인된 작업만 게시됨으로 표시할 수 있습니다.");
    }
    String v = url == null ? "" : url.strip();
    if (!v.matches("/[A-Za-z0-9/_\\-.?=&%]{1,199}") || v.startsWith("//")) {
      throw new IllegalArgumentException("학생 풀이 페이지 주소를 /로 시작하는 우리 사이트 주소로 적어 주세요(예: /naesintest/school/singu-m2).");
    }
    log(j, p, "PUBLISHED · " + v);
    return one(p, jobId);
  }

  /**
   * 화면에서 바로 탑재(대표 결정 2026-11-22): 게시 승인된 내신(영어) 작업의 「게시용 파일」을 학생 풀이 페이지에 넣고 곧바로 「게시됨」으로 만든다.
   * 검수받은 시험 묶음이 있는 작업은 「게시 승인」 때 그 묶음이 올라가므로 이 길을 쓰지 않는다({@link FactoryExamPublisher}).
   * 그림 문항·다른 구분은 PR로 올린 뒤 {@link #markPublished}를 쓴다.
   */
  @Transactional
  public Map<String, Object> uploadPublish(AuthenticatedPrincipal p, String jobId, JsonNode file) {
    CoordJob j = completed(p, jobId);
    if (!"APPROVED".equals(FactoryStage.publishState(events.findAllByJobIdOrderByAtAscEventIdAsc(jobId)))) {
      throw new IllegalStateException("게시 승인된 작업만 올릴 수 있습니다.");
    }
    if (publisher.hasPackage(jobId)) {
      throw new IllegalStateException("이 작업은 검수받은 시험 묶음이 있어 「게시 승인」 때 그 묶음이 올라갑니다. 다른 파일을 올리지 않습니다.");
    }
    if (!FactoryPlan.DEFAULT_TRACK.equals(FactoryPlan.track(JSON.readTree(j.getPlanJson())))) {
      throw new IllegalStateException("화면에서 바로 올리기는 지금 내신(영어)만 됩니다. 다른 구분은 탑재한 뒤 「게시됨으로 표시」를 써 주세요.");
    }
    FactoryExamUploader.Pack pack = uploader.upload(file, p.loginId());
    log(j, p, "PUBLISHED · " + pack.url() + " · " + pack.examCode());
    Map<String, Object> out = one(p, jobId);
    out.put("uploaded", Map.of("examCode", pack.examCode(), "questionCount", pack.questions().size(), "totalPoints", pack.totalPoints()));
    return out;
  }

  /**
   * 게시 내림: 승인·게시 표시를 되돌려 「게시 대기」로. 이 화면에서 올린 시험(검수받은 묶음 또는 게시용 파일)은 학생 페이지에서도
   * 내린다(ARCHIVED, 지우지 않음).
   * PR로 올린 자료를 실제로 내리는 일은 게시 절차 문서를 따른다.
   */
  @Transactional
  public Map<String, Object> withdrawPublish(AuthenticatedPrincipal p, String jobId, String reason) {
    CoordJob j = completed(p, jobId);
    List<CoordJobEvent> history = events.findAllByJobIdOrderByAtAscEventIdAsc(jobId);
    if ("READY".equals(FactoryStage.publishState(history))) {
      throw new IllegalStateException("게시 승인 전인 작업입니다.");
    }
    String examCode = uploadedExamCode(history);
    if (!examCode.isEmpty()) uploader.archive(examCode, p.loginId());
    String r = reason == null ? "" : reason.strip();
    publisher.withdraw(jobId);
    log(j, p, "WITHDRAWN" + (r.isEmpty() ? "" : " · " + (r.length() > 300 ? r.substring(0, 300) : r)));
    return one(p, jobId);
  }

  /* ---------------- 내부 ---------------- */

  /** 마지막 게시 기록이 화면 탑재(「PUBLISHED · 주소 · 시험 코드」)면 그 시험 코드, 아니면 빈 글. */
  static String uploadedExamCode(List<CoordJobEvent> history) {
    String code = "";
    for (CoordJobEvent e : history) {
      if (!FactoryStage.PUBLISH_ACTOR.equals(e.getActor())) continue;
      String[] parts = FactoryStage.tail(e).split(" · ", 2);
      code = "PUBLISHED".equals(FactoryStage.head(e)) && parts.length > 1 ? parts[1] : "";
    }
    return code;
  }

  private Map<String, Object> one(AuthenticatedPrincipal p, String jobId) {
    List<CoordJobEvent> history = events.findAllByJobIdOrderByAtAscEventIdAsc(jobId);
    return withPackage(row(own(p, jobId), history), jobId, history);
  }

  /** 시험 묶음이 있으면 요약을 붙이고, 묶음을 낸 뒤의 검수가 모자라면 게시 승인을 막는다. */
  private Map<String, Object> withPackage(Map<String, Object> out, String jobId, List<CoordJobEvent> history) {
    publisher.summary(jobId).ifPresent(pkg -> {
      int passed = publisher.passesAfterSubmit(jobId, history);
      pkg.put("passesAfterSubmit", passed);
      out.put("examPackage", pkg);
      out.put("canUpload", false);
      if (Boolean.TRUE.equals(out.get("canApprove")) && passed < CoordPcTaskService.REQUIRED_REVIEWS) {
        out.put("canApprove", false);
        out.put("publishBlock", "시험 묶음을 낸 뒤의 검수 통과가 " + passed + "곳입니다. 지금 묶음으로 " + CoordPcTaskService.REQUIRED_REVIEWS
            + "곳의 검수를 받아야 게시할 수 있습니다.");
      }
    });
    return out;
  }

  /** 작업 한 건을 현황판 한 줄로. */
  static Map<String, Object> row(CoordJob j, List<CoordJobEvent> history) {
    JsonNode plan = JSON.readTree(j.getPlanJson());
    String type = plan.path("type").asText();
    boolean reviewed = CoordPcTaskService.REVIEWED_TYPES.contains(type);
    FactoryStage.Result stage = FactoryStage.of(j.getStatus(), reviewed, history);
    String track = FactoryPlan.track(plan);
    JsonNode factory = plan.path("factory");
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("jobId", j.getJobId());
    out.put("title", plan.path("title").asText());
    out.put("status", j.getStatus());
    out.put("track", track);
    out.put("trackLabel", FactoryPlan.TRACK_LABEL.get(track));
    out.put("type", type);
    out.put("typeLabel", CoordPcTaskService.TYPE_LABEL.getOrDefault(type, type));
    out.put("exam", examLabel(plan.path("exam")));
    out.put("ranges", FactoryPlan.rangeLabel(factory.path("ranges")));
    out.put("pastExamRef", factory.path("pastExamRef").asText(""));
    out.put("scopeRef", factory.path("scopeRef").asText(""));
    out.put("itemLabels", FactoryPlan.itemLabels(plan));
    if (plan.hasNonNull("count")) out.put("count", plan.get("count").asInt());
    out.put("instructions", plan.path("instructions").asText(""));
    out.put("stage", stage.stage());
    out.put("stageNo", stage.no());
    out.put("stageLabel", stage.stage() == null ? "" : FactoryStage.LABEL.get(stage.stage()));
    out.put("state", stage.state());
    out.put("stateLabel", stage.label());
    List<String> passed = CoordPcTaskService.passedReviewers(history);
    out.put("reviewed", reviewed);
    out.put("reviewsPassed", passed.size());
    out.put("reviewsRequired", CoordPcTaskService.REQUIRED_REVIEWS);
    List<Map<String, Object>> reviews = new ArrayList<>();
    List<Map<String, Object>> progress = new ArrayList<>();
    String maker = "";
    String worker = "";
    Map<String, Object> publish = new LinkedHashMap<>();
    for (CoordJobEvent e : history) {
      String m = e.getMessage() == null ? "" : e.getMessage();
      if (CoordPcTaskService.REVIEW_ACTOR.equals(e.getActor())) {
        String[] parts = m.split(" · ", 3);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("at", e.getAt().format(MINUTE));
        r.put("verdict", parts[0]);
        r.put("reviewer", parts.length > 1 ? parts[1] : "");
        r.put("notes", parts.length > 2 ? parts[2] : "");
        reviews.add(r);
      } else if (FactoryStage.STAGE_ACTOR.equals(e.getActor())) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("at", e.getAt().format(MINUTE));
        s.put("stageLabel", FactoryExamPublisher.EXAM_MARK.equals(FactoryStage.head(e)) ? "시험 묶음 제출"
            : FactoryStage.LABEL.getOrDefault(FactoryStage.head(e), FactoryStage.head(e)));
        s.put("note", FactoryStage.tail(e));
        progress.add(s);
      } else if (FactoryStage.PUBLISH_ACTOR.equals(e.getActor())) {
        publish.put("at", e.getAt().format(MINUTE));
        publish.put("url", "PUBLISHED".equals(FactoryStage.head(e)) ? FactoryStage.tail(e).split(" · ", 2)[0] : "");
      } else if (CoordJob.RUNNING.equals(e.getToStatus()) && m.contains(CoordPcTaskService.MAKER_MARK)) {
        maker = m.substring(m.indexOf(CoordPcTaskService.MAKER_MARK) + CoordPcTaskService.MAKER_MARK.length()).strip();
        worker = m.split("가 가져감", 2)[0];
      }
    }
    // 마지막 FIX보다 앞선 PASS는 세지 않는다(고친 뒤 새로 검수받아야 한다) — 화면에서 흐리게 보인다.
    int lastFix = -1;
    for (int i = 0; i < reviews.size(); i++) if ("FIX".equals(reviews.get(i).get("verdict"))) lastFix = i;
    for (int i = 0; i < reviews.size(); i++) reviews.get(i).put("counted", i > lastFix && "PASS".equals(reviews.get(i).get("verdict")));
    out.put("reviews", reviews);
    out.put("progress", progress);
    out.put("makerSession", maker);
    out.put("worker", worker);
    out.put("preferredWorker", CoordPcTaskService.WORKER_LABEL.getOrDefault(plan.path("preferredWorker").asText("ACADEMY_PC"), ""));
    out.put("requestedAt", j.getCreatedAt().format(MINUTE));
    out.put("updatedAt", j.getUpdatedAt().format(MINUTE));
    if (j.getResultJson() != null) out.put("result", JSON.convertValue(JSON.readTree(j.getResultJson()), Map.class));
    if (j.getError() != null) out.put("error", j.getError());
    publish.put("state", CoordJob.COMPLETED.equals(j.getStatus()) ? FactoryStage.publishState(history) : "");
    publish.put("examCode", uploadedExamCode(history));
    out.put("publish", publish);
    out.put("prompts", prompts(j, plan, stage, reviews.size(), passed.size()));
    out.put("canCancel", CoordJob.QUEUED.equals(j.getStatus()));
    out.put("canApprove", "READY".equals(stage.state()) && passed.size() >= CoordPcTaskService.REQUIRED_REVIEWS);
    if ("READY".equals(stage.state()) && passed.size() < CoordPcTaskService.REQUIRED_REVIEWS) {
      out.put("publishBlock", "검수 통과가 " + passed.size() + "곳입니다. " + CoordPcTaskService.REQUIRED_REVIEWS
          + "곳을 채워야 게시할 수 있어, 다시 제작 요청을 올려 검수를 받아야 합니다.");
    }
    out.put("canMarkPublished", "APPROVED".equals(stage.state()));
    out.put("canUpload", "APPROVED".equals(stage.state()) && FactoryPlan.DEFAULT_TRACK.equals(track));
    out.put("canWithdraw", "APPROVED".equals(stage.state()) || "PUBLISHED".equals(stage.state()));
    return out;
  }

  /**
   * Claude 세션에 붙여 넣을 문장. 대기 중이면 제작 세션용 한 줄, 검수 단계면 남은 검수 수만큼 검수 세션용 문장(세션마다 다른 이름).
   * 그 밖의 단계에서는 붙여 넣을 것이 없다.
   */
  static List<Map<String, String>> prompts(CoordJob j, JsonNode plan, FactoryStage.Result stage, int reviewCount, int passed) {
    List<Map<String, String>> out = new ArrayList<>();
    if (CoordJob.QUEUED.equals(j.getStatus())) {
      String worker = plan.path("preferredWorker").asText("CLOUD");
      out.add(prompt("제작 세션 (" + CoordPcTaskService.WORKER_LABEL.getOrDefault(worker, worker) + ")",
          "F2R 대기 작업 처리해줘(worker=" + ("ANY".equals(worker) ? "CLOUD" : worker) + ", jobId=" + j.getJobId() + ")"));
    } else if ("REVIEW".equals(stage.stage()) && "WORKING".equals(stage.state())) {
      for (int i = 1; i <= CoordPcTaskService.REQUIRED_REVIEWS - passed; i++) {
        String name = "REVIEW-" + (reviewCount + i);
        out.add(prompt("검수 세션 " + name + " (새 세션에서)", "F2R 검수 jobId=" + j.getJobId() + " reviewer=" + name
            + " — f2r_pc_task_brief로 제작 지시서를 끝까지 읽고 결과물을 대조한 뒤 f2r_pc_task_review로 남겨줘"));
      }
    }
    return out;
  }

  private static Map<String, String> prompt(String label, String text) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("label", label);
    m.put("text", text);
    return m;
  }

  private static Map<String, Object> options() {
    Map<String, Object> o = new LinkedHashMap<>();
    o.put("tracks", pairs(FactoryPlan.TRACK_LABEL));
    o.put("schoolTracks", FactoryPlan.SCHOOL_TRACKS.stream().sorted().toList());
    Map<String, String> types = new LinkedHashMap<>();
    for (String t : List.of("EXPECTED", "WORKSHEET", "JIKBO", "WEEKLY")) types.put(t, CoordPcTaskService.TYPE_LABEL.get(t));
    o.put("types", pairs(types));
    o.put("itemLabels", pairs(FactoryPlan.ITEM_LABEL));
    Map<String, String> workers = new LinkedHashMap<>();
    for (String w : List.of("CLOUD", "ACADEMY_PC", "HOME_LAPTOP", "ANY")) workers.put(w, CoordPcTaskService.WORKER_LABEL.get(w));
    o.put("workers", pairs(workers));
    o.put("reviewsRequired", CoordPcTaskService.REQUIRED_REVIEWS);
    return o;
  }

  private static List<Map<String, String>> pairs(Map<String, String> labels) {
    List<Map<String, String>> out = new ArrayList<>();
    labels.forEach((k, v) -> out.add(Map.of("code", k, "label", v)));
    return out;
  }

  private static String examLabel(JsonNode e) {
    List<String> parts = new ArrayList<>();
    for (String k : List.of("school", "grade", "year", "semester", "exam")) {
      String v = e.path(k).asText("");
      if (!v.isBlank()) parts.add(k.equals("year") ? v + "년" : v);
    }
    return String.join(" ", parts);
  }

  private CoordJob completed(AuthenticatedPrincipal p, String jobId) {
    CoordJob j = own(p, jobId);
    if (!CoordJob.COMPLETED.equals(j.getStatus())) throw new IllegalStateException("완료된 작업만 게시할 수 있습니다(지금: " + j.getStatus() + ").");
    return j;
  }

  private CoordJob own(AuthenticatedPrincipal p, String jobId) {
    CoordinatorService.requireAdmin(p);
    CoordJob j = jobs.findById(jobId == null ? "" : jobId).orElseThrow(() -> new IllegalArgumentException("작업을 찾을 수 없습니다."));
    if (!j.getUserId().equals(p.userId())) throw new AccessDeniedException("본인 작업만 다룰 수 있습니다.");
    if (!CoordPcTaskService.KIND.equals(j.getKind())) throw new IllegalArgumentException("제작 작업이 아닙니다.");
    return j;
  }

  private void log(CoordJob j, AuthenticatedPrincipal p, String message) {
    events.save(new CoordJobEvent(j.getJobId(), LocalDateTime.now(clock), FactoryStage.PUBLISH_ACTOR, p.loginId(),
        CoordJob.COMPLETED, CoordJob.COMPLETED, message));
  }
}
