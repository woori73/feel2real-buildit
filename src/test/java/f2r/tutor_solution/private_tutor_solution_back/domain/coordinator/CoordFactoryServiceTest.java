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
    service = new CoordFactoryService(pcTasks, jobs, events, clock);
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
}
