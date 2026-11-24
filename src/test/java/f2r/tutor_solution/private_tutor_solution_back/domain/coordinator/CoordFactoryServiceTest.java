package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@SuppressWarnings("unchecked")
@DisplayName("CoordFactoryService — 제작 현황판(기존 대기열 위의 화면: 요청·공정 6단계·검수 3곳·게시)")
class CoordFactoryServiceTest {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(1L, "admin", "ADMIN");
  static final AuthenticatedPrincipal OTHER_ADMIN = new AuthenticatedPrincipal(9L, "other", "ADMIN");
  static final AuthenticatedPrincipal TUTOR = new AuthenticatedPrincipal(2L, "t", "TUTOR");
  final Map<String, CoordJob> store = new LinkedHashMap<>();
  final List<CoordJobEvent> history = new ArrayList<>();
  CoordPcTaskService pcTasks;
  CoordFactoryService service;
  FactoryExamPublisher publisher;
  final Map<String, FactoryExamPackage> packageStore = new LinkedHashMap<>();
  final Map<String, f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam> examStore = new LinkedHashMap<>();
  final List<f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpQuestion> questionStore = new ArrayList<>();
  FactoryExamUploader uploader;

  @BeforeEach
  void setUp() {
    CoordJobRepository jobs = mock(CoordJobRepository.class);
    CoordJobEventRepository events = mock(CoordJobEventRepository.class);
    when(jobs.save(any())).thenAnswer(i -> { CoordJob j = i.getArgument(0); store.put(j.getJobId(), j); return j; });
    when(jobs.findById(anyString())).thenAnswer(i -> Optional.ofNullable(store.get(i.<String>getArgument(0))));
    when(jobs.findByUserIdAndRequestId(anyLong(), anyString())).thenAnswer(i -> store.values().stream()
        .filter(j -> j.getRequestId().equals(i.getArgument(1))).findFirst());
    when(jobs.findTop50ByUserIdAndKindOrderByCreatedAtAsc(anyLong(), anyString())).thenAnswer(i -> new ArrayList<>(store.values()));
    when(jobs.findTop100ByUserIdAndKindOrderByCreatedAtDesc(anyLong(), anyString())).thenAnswer(i -> store.values().stream()
        .filter(j -> j.getUserId().equals(i.getArgument(0))).toList());
    when(events.save(any())).thenAnswer(i -> { history.add(i.getArgument(0)); return i.getArgument(0); });
    when(events.findAllByJobIdOrderByAtAscEventIdAsc(anyString())).thenAnswer(i -> history.stream()
        .filter(e -> e.getJobId().equals(i.getArgument(0))).toList());
    when(events.findAllByJobIdInOrderByAtAscEventIdAsc(anyCollection())).thenAnswer(i -> history.stream()
        .filter(e -> i.<Collection<String>>getArgument(0).contains(e.getJobId())).toList());
    Clock clock = Clock.fixed(LocalDateTime.of(2026, 11, 22, 10, 0).atZone(KstTime.ZONE).toInstant(), KstTime.ZONE);
    pcTasks = new CoordPcTaskService(jobs, events, clock);
    FactoryExamPackageRepository packages = mock(FactoryExamPackageRepository.class);
    when(packages.findById(anyString())).thenAnswer(i -> Optional.ofNullable(packageStore.get(i.<String>getArgument(0))));
    when(packages.existsById(anyString())).thenAnswer(i -> packageStore.containsKey(i.<String>getArgument(0)));
    when(packages.save(any())).thenAnswer(i -> { FactoryExamPackage x = i.getArgument(0); packageStore.put(x.getJobId(), x); return x; });
    var exams = mock(f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository.class);
    when(exams.findByExamCode(anyString())).thenAnswer(i -> Optional.ofNullable(examStore.get(i.<String>getArgument(0))));
    when(exams.findById(any())).thenAnswer(i -> examStore.values().stream().filter(x -> x.getExamId().equals(i.getArgument(0))).findFirst());
    when(exams.save(any())).thenAnswer(i -> {
      var x = (f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam) i.getArgument(0);
      org.springframework.test.util.ReflectionTestUtils.setField(x, "examId", 500L + examStore.size());
      examStore.put(x.getExamCode(), x);
      return x;
    });
    var questions = mock(f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpQuestionRepository.class);
    when(questions.saveAll(any())).thenAnswer(i -> { i.<Iterable<f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpQuestion>>getArgument(0).forEach(questionStore::add); return List.of(); });
    publisher = new FactoryExamPublisher(jobs, events, packages, exams, questions, "/naesin/singu-m2-2026-2/r01/q01.webp"::equals, clock);
    uploader = mock(FactoryExamUploader.class);
    service = new CoordFactoryService(pcTasks, jobs, events, publisher, uploader, clock);
  }

  ObjectNode naesin(String requestId) {
    ObjectNode a = JSON.createObjectNode().put("requestId", requestId).put("track", "NAESIN").put("type", "EXPECTED")
        .put("school", "신구중").put("grade", "중2").put("year", 2026).put("semester", "2학기").put("exam", "기말").put("count", 40)
        .put("pastExamRef", "내신위키 2025 2학기 기말 기출").put("scopeRef", "교과서 Lesson 7~8").put("instructions", "서술형 5문항 포함");
    a.putArray("ranges").add("12~45").add("72~83").add("92~115");
    a.putArray("itemLabels").add("PAST_ELEMENT");
    return a;
  }

  String created() {
    return (String) service.create(ADMIN, naesin("web-req-0001")).get("jobId");
  }

  void pass(String jobId, String reviewer) {
    ObjectNode a = JSON.createObjectNode().put("briefHash", (String) pcTasks.brief(ADMIN, jobId).get("briefHash"))
        .put("reviewer", reviewer).put("verdict", "PASS").put("notes", "기출 대조 끝");
    ObjectNode c = a.putObject("checks");
    for (String k : CoordPcTaskService.REVIEW_CHECKS) c.put(k, true);
    pcTasks.review(ADMIN, jobId, a);
  }

  /** 가져가기 → 검수 3곳 통과 → 완료까지. */
  String completed() {
    String id = created();
    pcTasks.claim(ADMIN, id, "CLOUD", "MAKER-1");
    for (String r : List.of("REVIEW-1", "REVIEW-2", "REVIEW-3")) pass(id, r);
    pcTasks.finish(ADMIN, id, JSON.createObjectNode().put("summary", "Wiki FINAL 저장").put("wikiRef", "final #12"));
    return id;
  }

  Map<String, Object> rowOf(String jobId) {
    return ((List<Map<String, Object>>) service.board(ADMIN).get("jobs")).stream().filter(r -> jobId.equals(r.get("jobId")))
        .findFirst().orElseThrow();
  }

  @Test
  @DisplayName("given 화면에서 내신 제작 요청 when create then 기존 대기열(PC_TASK, queued)에 들어가고 현황판 한 줄에 구분·범위·자료·붙여 넣을 문장이 보인다")
  void createGoesIntoExistingQueue() {
    // given & when
    Map<String, Object> row = service.create(ADMIN, naesin("web-req-0001"));

    // then
    CoordJob job = store.get((String) row.get("jobId"));
    assertThat(job.getKind()).isEqualTo(CoordPcTaskService.KIND);
    assertThat(job.getSource()).isEqualTo("WEB");
    assertThat(row).containsEntry("status", CoordJob.QUEUED).containsEntry("trackLabel", "내신(영어)").containsEntry("stage", "ANALYSIS")
        .containsEntry("stageNo", 1).containsEntry("ranges", "12~45쪽, 72~83쪽, 92~115쪽").containsEntry("exam", "신구중 2학년 2026년 2학기 기말")
        .containsEntry("pastExamRef", "내신위키 2025 2학기 기말 기출").containsEntry("itemLabels", List.of("기출 평가 요소 기반"))
        .containsEntry("reviewsRequired", 3).containsEntry("canCancel", true).containsEntry("canApprove", false);
    List<Map<String, String>> prompts = (List<Map<String, String>>) row.get("prompts");
    assertThat(prompts).hasSize(1);
    assertThat(prompts.get(0).get("text")).contains("F2R 대기 작업 처리해줘").contains("worker=CLOUD").contains(job.getJobId());
    assertThat((List<Map<String, Object>>) pcTasks.list(ADMIN, false, "CLOUD").get("open")).hasSize(1);
  }

  @Test
  @DisplayName("given 구분 없음·검수 없는 종류(OTHER)·원장이 아닌 계정 when create then 거절")
  void createValidates() {
    // given
    ObjectNode noTrack = naesin("web-req-0002");
    noTrack.remove("track");

    // when & then
    assertThatThrownBy(() -> service.create(ADMIN, noTrack)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("구분");
    assertThatThrownBy(() -> service.create(ADMIN, naesin("web-req-0003").put("type", "OTHER")))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("3곳");
    assertThatThrownBy(() -> service.create(TUTOR, naesin("web-req-0004"))).isInstanceOf(AccessDeniedException.class);
    assertThat(store).isEmpty();
  }

  @Test
  @DisplayName("given 여러 단계의 작업 when board then 공정 6단계마다 작업 수를 세고 취소한 작업은 세지 않는다")
  void boardCountsStages() {
    // given
    String waiting = created();
    String drafting = (String) service.create(ADMIN, naesin("web-req-0011")).get("jobId");
    pcTasks.claim(ADMIN, drafting, "CLOUD", "MAKER-2");
    pcTasks.stage(ADMIN, drafting, JSON.createObjectNode().put("stage", "DRAFT").put("note", "20문항째"));
    String cancelled = (String) service.create(ADMIN, naesin("web-req-0012")).get("jobId");
    service.cancel(ADMIN, cancelled);

    // when
    Map<String, Object> board = service.board(ADMIN);

    // then
    List<Map<String, Object>> stages = (List<Map<String, Object>>) board.get("stages");
    assertThat(stages).extracting(s -> s.get("code")).containsExactly("ANALYSIS", "SCOPE", "DRAFT", "REVIEW", "REVISE", "PUBLISH");
    assertThat(stages).extracting(s -> s.get("count")).containsExactly(1, 0, 1, 0, 0, 0);
    assertThat(rowOf(waiting)).containsEntry("stateLabel", "대기 중 — 아직 맡은 세션이 없습니다");
    assertThat(rowOf(drafting)).containsEntry("stage", "DRAFT").containsEntry("makerSession", "MAKER-2").containsEntry("worker", "클라우드");
    assertThat((List<Map<String, Object>>) rowOf(drafting).get("progress")).singleElement()
        .satisfies(p -> assertThat(p).containsEntry("stageLabel", "변형·유사 문제 제작").containsEntry("note", "20문항째"));
    assertThat(rowOf(cancelled)).containsEntry("stage", null).containsEntry("state", "CLOSED").containsEntry("canCancel", false);
    assertThat(board).containsEntry("principle", FactoryPlan.ORIGINALITY);
    assertThat((Map<String, Object>) board.get("options")).containsKeys("tracks", "types", "itemLabels", "workers", "schoolTracks");
    assertThat((List<Map<String, String>>) ((Map<String, Object>) board.get("options")).get("tracks")).hasSize(9);
  }

  @Test
  @DisplayName("given 검수 단계(통과 1곳) when board then 검수 1/3과 검수 의견이 보이고, 남은 2곳만큼 새 이름의 검수 세션 문장을 건넨다")
  void reviewStageShowsOpinionsAndPrompts() {
    // given
    String id = created();
    pcTasks.claim(ADMIN, id, "CLOUD", "MAKER-1");
    pcTasks.stage(ADMIN, id, JSON.createObjectNode().put("stage", "REVIEW"));
    pass(id, "REVIEW-1");

    // when
    Map<String, Object> row = rowOf(id);

    // then
    assertThat(row).containsEntry("stage", "REVIEW").containsEntry("stateLabel", "검수 1/3").containsEntry("reviewsPassed", 1);
    assertThat((List<Map<String, Object>>) row.get("reviews")).singleElement().satisfies(r -> assertThat(r)
        .containsEntry("verdict", "PASS").containsEntry("reviewer", "REVIEW-1").containsEntry("notes", "기출 대조 끝").containsEntry("counted", true));
    List<Map<String, String>> prompts = (List<Map<String, String>>) row.get("prompts");
    assertThat(prompts).extracting(p -> p.get("label")).containsExactly("검수 세션 REVIEW-2 (새 세션에서)", "검수 세션 REVIEW-3 (새 세션에서)");
    assertThat(prompts.get(0).get("text")).contains("F2R 검수 jobId=" + id).contains("reviewer=REVIEW-2").contains("f2r_pc_task_brief");
  }

  @Test
  @DisplayName("given 통과 뒤 FIX when board then ⑤ 검수 후 수정이고 FIX 앞의 통과는 세지 않는다(counted=false)")
  void fixMovesToReviseAndDimsOldPasses() {
    // given
    String id = created();
    pcTasks.claim(ADMIN, id, "CLOUD", "MAKER-1");
    pass(id, "REVIEW-1");
    ObjectNode fix = JSON.createObjectNode().put("briefHash", (String) pcTasks.brief(ADMIN, id).get("briefHash"))
        .put("reviewer", "REVIEW-2").put("verdict", "FIX").put("notes", "7번 정답 오류");
    pcTasks.review(ADMIN, id, fix);

    // when
    Map<String, Object> row = rowOf(id);

    // then
    assertThat(row).containsEntry("stage", "REVISE").containsEntry("stageNo", 5).containsEntry("reviewsPassed", 0);
    assertThat((List<Map<String, Object>>) row.get("reviews")).extracting(r -> r.get("counted")).containsExactly(false, false);
    assertThat((List<?>) row.get("prompts")).isEmpty();
  }

  @Test
  @DisplayName("given 검수 3곳 통과 뒤 완료 when 게시 승인 → 게시됨 표시 → 게시 내림 then 게시 대기 → 탑재 대기 → 게시됨(주소) → 다시 게시 대기")
  void publishFlow() {
    // given
    String id = completed();
    assertThat(rowOf(id)).containsEntry("stage", "PUBLISH").containsEntry("state", "READY").containsEntry("canApprove", true);

    // when & then
    assertThatThrownBy(() -> service.markPublished(ADMIN, id, "/naesintest")).isInstanceOf(IllegalStateException.class);
    Map<String, Object> approved = service.approvePublish(ADMIN, id);
    assertThat(approved).containsEntry("state", "APPROVED").containsEntry("canApprove", false).containsEntry("canMarkPublished", true);
    assertThatThrownBy(() -> service.approvePublish(ADMIN, id)).isInstanceOf(IllegalStateException.class);
    Map<String, Object> published = service.markPublished(ADMIN, id, " /naesintest/school/singu-m2 ");
    assertThat(published).containsEntry("state", "PUBLISHED").containsEntry("canWithdraw", true);
    assertThat((Map<String, Object>) published.get("publish")).containsEntry("state", "PUBLISHED")
        .containsEntry("url", "/naesintest/school/singu-m2");
    assertThat((Map<String, Object>) published.get("result")).containsEntry("wikiRef", "final #12");
    assertThat(history.get(history.size() - 1).getActor()).isEqualTo(FactoryStage.PUBLISH_ACTOR);
    Map<String, Object> withdrawn = service.withdrawPublish(ADMIN, id, "오탈자");
    assertThat(withdrawn).containsEntry("state", "READY").containsEntry("canApprove", true);
    assertThat((Map<String, Object>) withdrawn.get("publish")).containsEntry("url", "");
    assertThatThrownBy(() -> service.withdrawPublish(ADMIN, id, null)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("given 승인된 작업 when 바깥 주소·스크립트 주소로 게시됨 표시 then 거절(우리 사이트 안 주소만)")
  void markPublishedRejectsOutsideUrls() {
    // given
    String id = completed();
    service.approvePublish(ADMIN, id);

    // when & then
    for (String bad : new String[] {"https://example.com/x", "//example.com", "javascript:alert(1)", "", "/a b"}) {
      assertThatThrownBy(() -> service.markPublished(ADMIN, id, bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> service.markPublished(ADMIN, id, null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 아직 처리 중·검수 2곳으로 끝난 예전 작업 when 게시 승인 then 거절(완료 + 검수 3곳이어야 게시)")
  void approveNeedsCompletedAndThreeReviews() {
    // given
    String running = created();
    pcTasks.claim(ADMIN, running, "CLOUD", "MAKER-1");
    String old = (String) service.create(ADMIN, naesin("web-req-0021")).get("jobId");
    pcTasks.claim(ADMIN, old, "CLOUD", "MAKER-1");
    pass(old, "REVIEW-1");
    pass(old, "REVIEW-2");
    store.get(old).complete("{\"summary\":\"예전 기준(2곳)으로 끝난 작업\"}", LocalDateTime.of(2026, 11, 20, 9, 0));

    // when & then
    assertThatThrownBy(() -> service.approvePublish(ADMIN, running)).isInstanceOf(IllegalStateException.class).hasMessageContaining("완료된");
    assertThatThrownBy(() -> service.approvePublish(ADMIN, old)).isInstanceOf(IllegalStateException.class).hasMessageContaining("3곳");
    assertThat(rowOf(old)).containsEntry("canApprove", false).containsKey("publishBlock");
  }

  @Test
  @DisplayName("given 다른 원장의 작업 when 보기·취소·게시 then 내 현황판에 안 보이고 건드릴 수 없다")
  void ownJobsOnly() {
    // given
    String id = completed();

    // when & then
    assertThat((List<?>) service.board(OTHER_ADMIN).get("jobs")).isEmpty();
    assertThatThrownBy(() -> service.approvePublish(OTHER_ADMIN, id)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.cancel(OTHER_ADMIN, id)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.board(TUTOR)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.brief(TUTOR, id)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given 누가 가져간 작업 when 화면에서 취소 then 거절, 지시서는 Claude 세션이 받는 것과 같은 글")
  void cancelOnlyQueuedAndBriefMatches() {
    // given
    String id = created();
    Map<String, Object> brief = service.brief(ADMIN, id);
    pcTasks.claim(ADMIN, id, "CLOUD", "MAKER-1");

    // when & then
    assertThatThrownBy(() -> service.cancel(ADMIN, id)).isInstanceOf(IllegalStateException.class);
    assertThat(brief.get("briefMarkdown")).isEqualTo(pcTasks.brief(ADMIN, id).get("briefMarkdown"));
    assertThat((String) brief.get("briefMarkdown")).contains("12~45쪽, 72~83쪽, 92~115쪽").contains(FactoryPlan.ORIGINALITY);
  }

  /* ---------------- 시험 묶음 → 학생 풀이 페이지 게시 ---------------- */

  static ObjectNode examPackage(String code) {
    ObjectNode a = JSON.createObjectNode();
    ObjectNode e = a.putObject("exam").put("examCode", code).put("title", "신구중 2학년 2학기 기말 13회")
        .put("subtitle", "Lesson 7·8 12~45쪽, 72~83쪽 · 2문항 · 7점").put("timeLimitSec", 1200);
    var qs = e.putArray("questions");
    ObjectNode q1 = qs.addObject().put("no", 1).put("prompt", "[[img:/naesin/singu-m2-2026-2/r01/q01.webp]]")
        .put("answerKey", "C").put("points", 3).put("explanation", "정답 ③ · 어법 (3점)\n근거");
    q1.putArray("choices").add("①").add("②").add("③").add("④").add("⑤");
    ObjectNode q2 = qs.addObject().put("no", 2).put("taskType", "NAESIN_MC5_MULTI").put("prompt", "옳은 것을 모두 고르면?")
        .put("bodyText", "The cat sleeps.").put("answerKey", "b, d").put("points", 4).put("explanation", "정답 ②, ④ · 어법 (4점)");
    q2.putArray("choices").add("a").add("b").add("c").add("d").add("e");
    return a;
  }

  String claimed() {
    String id = created();
    pcTasks.claim(ADMIN, id, "CLOUD", "MAKER-1");
    return id;
  }

  String claimed(String requestId) {
    String id = (String) service.create(ADMIN, naesin(requestId)).get("jobId");
    pcTasks.claim(ADMIN, id, "CLOUD", "MAKER-1");
    return id;
  }

  void finish(String id) {
    pcTasks.finish(ADMIN, id, JSON.createObjectNode().put("summary", "13회 40문항 제작·검수 끝").put("wikiRef", "final#13"));
  }

  @Test
  @DisplayName("given 시험 묶음을 내고 그 판으로 검수 3곳 통과·완료 when 게시 승인 then 학생 풀이 페이지에 PUBLISHED로 올라가고 게시됨이 된다")
  void approvePublishesExamPackage() {
    // given
    String id = claimed();
    publisher.submit(ADMIN, id, examPackage("TP-NAESIN-SGM2-2602-R13"));
    pass(id, "REVIEW-1"); pass(id, "REVIEW-2"); pass(id, "REVIEW-3");
    finish(id);

    // when
    Map<String, Object> out = service.approvePublish(ADMIN, id);

    // then
    assertThat(out).containsEntry("state", "PUBLISHED");
    assertThat((Map<String, Object>) out.get("publish")).containsEntry("url", "/naesintest");
    var exam = examStore.get("TP-NAESIN-SGM2-2602-R13");
    assertThat(exam.getKind()).isEqualTo("NAESIN");
    assertThat(exam.getStatus()).isEqualTo("PUBLISHED");
    assertThat(questionStore).hasSize(2);
    assertThat(questionStore.get(0).getExamId()).isEqualTo(exam.getExamId());
    assertThat(questionStore.get(0).getSection()).isEqualTo("EN");
    assertThat(questionStore.get(0).getAudioMode()).isEqualTo("NONE");
    assertThat(questionStore.get(1).getAnswerKey()).isEqualTo("B,D");
    assertThat(questionStore.get(1).getTaskType()).isEqualTo("NAESIN_MC5_MULTI");
    assertThat((Map<String, Object>) out.get("examPackage")).containsEntry("published", true).containsEntry("passesAfterSubmit", 3);
  }

  @Test
  @DisplayName("given 게시된 시험 when 게시 내림 → 다시 승인 then 학생 페이지에서 ARCHIVED로 내려갔다가 같은 시험이 다시 PUBLISHED가 된다")
  void withdrawArchivesAndReapproveRepublishes() {
    // given
    String id = claimed();
    publisher.submit(ADMIN, id, examPackage("TP-NAESIN-SGM2-2602-R13"));
    pass(id, "REVIEW-1"); pass(id, "REVIEW-2"); pass(id, "REVIEW-3");
    finish(id);
    service.approvePublish(ADMIN, id);

    // when & then
    assertThat(service.withdrawPublish(ADMIN, id, "오탈자")).containsEntry("state", "READY");
    assertThat(examStore.get("TP-NAESIN-SGM2-2602-R13").getStatus()).isEqualTo("ARCHIVED");
    assertThat(service.approvePublish(ADMIN, id)).containsEntry("state", "PUBLISHED");
    assertThat(examStore.get("TP-NAESIN-SGM2-2602-R13").getStatus()).isEqualTo("PUBLISHED");
    assertThat(examStore).hasSize(1);
    assertThat(questionStore).hasSize(2);
  }

  @Test
  @DisplayName("given 검수 3곳 뒤 묶음을 다시 냄 when 게시 승인 then 지금 판의 검수가 0곳이라 거절하고 승인 기록도 남기지 않는다")
  void resubmittedPackageNeedsNewReviews() {
    // given
    String id = claimed();
    publisher.submit(ADMIN, id, examPackage("TP-NAESIN-SGM2-2602-R13"));
    pass(id, "REVIEW-1"); pass(id, "REVIEW-2"); pass(id, "REVIEW-3");
    publisher.submit(ADMIN, id, examPackage("TP-NAESIN-SGM2-2602-R13"));
    finish(id);
    int before = history.size();

    // when & then
    Map<String, Object> view = (Map<String, Object>) ((List<Object>) service.board(ADMIN).get("jobs")).get(0);
    assertThat(view).containsEntry("canApprove", false);
    assertThat((String) view.get("publishBlock")).contains("0곳");
    assertThatThrownBy(() -> service.approvePublish(ADMIN, id)).isInstanceOf(IllegalStateException.class).hasMessageContaining("0곳");
    assertThat(history).hasSize(before);
    assertThat(examStore).isEmpty();
  }

  @Test
  @DisplayName("given 묶음 없는 작업 when 게시 승인 then 예전처럼 탑재 대기(APPROVED)가 되고 학생 페이지에는 아무것도 넣지 않는다")
  void approveWithoutPackageKeepsManualFlow() {
    // given
    String id = claimed();
    pass(id, "REVIEW-1"); pass(id, "REVIEW-2"); pass(id, "REVIEW-3");
    finish(id);

    // when
    Map<String, Object> out = service.approvePublish(ADMIN, id);

    // then
    assertThat(out).containsEntry("state", "APPROVED").containsEntry("canMarkPublished", true).doesNotContainKey("examPackage");
    assertThat(examStore).isEmpty();
  }

  @Test
  @DisplayName("given 처리 중이 아닌 작업·이미 쓰이는 시험 코드·남의 작업 when 묶음 제출 then 거절")
  void submitRejects() {
    // given
    String id = claimed();
    String queued = created2();
    examStore.put("TP-NAESIN-SGM2-2602-R12", f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam
        .create("TP-NAESIN-SGM2-2602-R12", "NAESIN", "12회", null, null, 1200, "PUBLISHED"));

    // when & then
    assertThatThrownBy(() -> publisher.submit(ADMIN, queued, examPackage("TP-NAESIN-SGM2-2602-R13")))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("처리 중");
    assertThatThrownBy(() -> publisher.submit(ADMIN, id, examPackage("TP-NAESIN-SGM2-2602-R12")))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("이미 쓰이는");
    assertThatThrownBy(() -> publisher.submit(OTHER_ADMIN, id, examPackage("TP-NAESIN-SGM2-2602-R13")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given 낸 묶음 when 읽기 then 검수 세션이 받을 문항 전체·판·요약을 주고, 묶음 없는 작업은 거절")
  void readReturnsWholePackage() {
    // given
    String id = claimed();
    publisher.submit(ADMIN, id, examPackage("TP-NAESIN-SGM2-2602-R13"));

    // when
    Map<String, Object> out = publisher.read(ADMIN, id);

    // then
    assertThat(out).containsEntry("examCode", "TP-NAESIN-SGM2-2602-R13").containsEntry("questionCount", 2)
        .containsEntry("maxScore", 7).containsEntry("studentUrl", "/naesintest");
    assertThat(out.get("exam").toString()).contains("The cat sleeps.").contains("B,D");
    assertThat((List<String>) out.get("firstQuestions")).first().asString().isEqualTo("1. [그림]");
    assertThat(history).anyMatch(e -> e.getMessage().startsWith("EXAM · TP-NAESIN-SGM2-2602-R13 · 2문항"));
    assertThatThrownBy(() -> publisher.read(ADMIN, created2())).isInstanceOf(IllegalArgumentException.class);
  }

  String created2() {
    return (String) service.create(ADMIN, naesin("web-req-0002")).get("jobId");
  }

  @Test
  @DisplayName("given 게시 승인된 내신 작업 when 화면에서 게시용 파일 올리기 then 학생 페이지에 넣고 바로 게시됨(주소·시험 코드), 내리면 그 회차도 내려간다")
  void uploadPublishesDirectly() {
    // given
    String id = completed();
    ObjectNode file = JSON.createObjectNode().put("examCode", "TP-NAESIN-SGM2-2602-R12");
    var school = f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller.NaesinTestPageController.SCHOOLS.get(1);
    var pack = new FactoryExamUploader.Pack("TP-NAESIN-SGM2-2602-R12", school, "신구중 12회", null, null, 7200,
        List.of(new FactoryExamUploader.Question("NAESIN_MC5", "발문", null, "[]", "A", 2, "해설")));
    when(uploader.upload(any(), anyString())).thenReturn(pack);
    assertThatThrownBy(() -> service.uploadPublish(ADMIN, id, file)).isInstanceOf(IllegalStateException.class).hasMessageContaining("승인");
    assertThat(service.approvePublish(ADMIN, id)).containsEntry("canUpload", true);

    // when
    Map<String, Object> out = service.uploadPublish(ADMIN, id, file);

    // then
    assertThat(out).containsEntry("state", "PUBLISHED").containsEntry("canUpload", false);
    assertThat((Map<String, Object>) out.get("publish")).containsEntry("url", "/naesintest/school/singu-m2")
        .containsEntry("examCode", "TP-NAESIN-SGM2-2602-R12");
    assertThat((Map<String, Object>) out.get("uploaded")).containsEntry("questionCount", 1).containsEntry("totalPoints", 2);
    service.withdrawPublish(ADMIN, id, "오탈자");
    org.mockito.Mockito.verify(uploader).archive("TP-NAESIN-SGM2-2602-R12", "admin");
  }

  @Test
  @DisplayName("given 내신(영어)이 아닌 구분·PR로 게시한 작업 when 화면 탑재·내림 then 탑재는 거절하고, 내려도 학생 페이지 회차는 건드리지 않는다")
  void uploadOnlyForNaesinAndWithdrawOnlyArchivesUploaded() {
    // given
    String id = completed();
    service.approvePublish(ADMIN, id);
    service.markPublished(ADMIN, id, "/naesintest/school/singu-m2");
    ObjectNode korean = naesin("web-req-0031").put("track", "KOREAN");
    String other = (String) service.create(ADMIN, korean).get("jobId");
    pcTasks.claim(ADMIN, other, "CLOUD", "MAKER-1");
    for (String r : List.of("REVIEW-1", "REVIEW-2", "REVIEW-3")) pass(other, r);
    pcTasks.finish(ADMIN, other, JSON.createObjectNode().put("summary", "저장"));
    assertThat(service.approvePublish(ADMIN, other)).containsEntry("canUpload", false).containsEntry("canMarkPublished", true);

    // when & then
    assertThatThrownBy(() -> service.uploadPublish(ADMIN, other, JSON.createObjectNode())).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("내신(영어)만");
    service.withdrawPublish(ADMIN, id, null);
    org.mockito.Mockito.verifyNoInteractions(uploader);
  }

  @Test
  @DisplayName("given 묶음을 낸 뒤 검수 3곳 통과·완료 when 자동 게시 then 승인 없이 학생 풀이 페이지에 올라가고 기록은 AUTO로 남는다")
  void autoPublishesWithoutApproval() {
    // given
    String id = claimed();
    publisher.submit(ADMIN, id, examPackage("TP-NAESIN-SGM2-2602-R13"));
    pass(id, "REVIEW-1"); pass(id, "REVIEW-2"); pass(id, "REVIEW-3");
    finish(id);

    // when
    String url = service.autoPublish(id);

    // then
    assertThat(url).isEqualTo("/naesintest");
    assertThat(examStore.get("TP-NAESIN-SGM2-2602-R13").getStatus()).isEqualTo("PUBLISHED");
    assertThat(FactoryStage.publishState(history)).isEqualTo("PUBLISHED");
    assertThat(history.stream().filter(e -> FactoryStage.PUBLISH_ACTOR.equals(e.getActor())).map(CoordJobEvent::getActorLoginId))
        .containsOnly(CoordFactoryService.AUTO_LOGIN);
    assertThat(service.autoPublish(id)).isEmpty();
    assertThat(examStore).hasSize(1);
  }

  @Test
  @DisplayName("given 원장이 게시를 내린 작업 when 자동 게시 then 다시 올리지 않는다")
  void autoPublishSkipsWithdrawnJob() {
    // given
    String id = claimed();
    publisher.submit(ADMIN, id, examPackage("TP-NAESIN-SGM2-2602-R13"));
    pass(id, "REVIEW-1"); pass(id, "REVIEW-2"); pass(id, "REVIEW-3");
    finish(id);
    service.autoPublish(id);
    service.withdrawPublish(ADMIN, id, "오탈자");

    // when
    String url = service.autoPublish(id);

    // then
    assertThat(url).isEmpty();
    assertThat(examStore.get("TP-NAESIN-SGM2-2602-R13").getStatus()).isEqualTo("ARCHIVED");
  }

  @Test
  @DisplayName("given 처리 중 작업·묶음 제출 뒤 검수가 모자란 작업·묶음 없는 작업 when 자동 게시 then 아무것도 올리지 않는다")
  void autoPublishSkipsNotReadyJobs() {
    // given
    String running = claimed("web-auto-1");
    publisher.submit(ADMIN, running, examPackage("TP-NAESIN-SGM2-2602-R13"));
    pass(running, "REVIEW-1"); pass(running, "REVIEW-2"); pass(running, "REVIEW-3");
    String stale = claimed("web-auto-2");
    pass(stale, "REVIEW-1"); pass(stale, "REVIEW-2"); pass(stale, "REVIEW-3");
    publisher.submit(ADMIN, stale, examPackage("TP-NAESIN-SGM2-2602-R14"));
    finish(stale);
    String noPackage = claimed("web-auto-3");
    pass(noPackage, "REVIEW-1"); pass(noPackage, "REVIEW-2"); pass(noPackage, "REVIEW-3");
    finish(noPackage);

    // when & then
    assertThat(service.autoPublish(running)).isEmpty();
    assertThat(service.autoPublish(stale)).isEmpty();
    assertThat(service.autoPublish(noPackage)).isEmpty();
    assertThat(service.autoPublish("없는-작업")).isEmpty();
    assertThat(examStore).isEmpty();
  }
}
