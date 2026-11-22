package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
@DisplayName("CoordPcTaskService — 학원 PC 작업 대기열(우선 기기·대기 시간·다른 세션 3회 검수·공장 정보·단계 보고)")
class CoordPcTaskServiceTest {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(1L, "admin", "ADMIN");
  final Map<String, CoordJob> store = new LinkedHashMap<>();
  final List<CoordJobEvent> history = new ArrayList<>();
  final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 11, 19, 15, 0).atZone(KstTime.ZONE).toInstant());
  CoordPcTaskService service;

  @BeforeEach
  void setUp() {
    CoordJobRepository jobs = mock(CoordJobRepository.class);
    CoordJobEventRepository events = mock(CoordJobEventRepository.class);
    when(jobs.save(any())).thenAnswer(i -> { CoordJob j = i.getArgument(0); store.put(j.getJobId(), j); return j; });
    when(jobs.findById(anyString())).thenAnswer(i -> Optional.ofNullable(store.get(i.<String>getArgument(0))));
    when(jobs.findByUserIdAndRequestId(anyLong(), anyString())).thenAnswer(i -> store.values().stream()
        .filter(j -> j.getRequestId().equals(i.getArgument(1))).findFirst());
    when(jobs.findTop50ByUserIdAndKindOrderByCreatedAtAsc(anyLong(), anyString())).thenAnswer(i -> new ArrayList<>(store.values()));
    when(events.save(any())).thenAnswer(i -> { history.add(i.getArgument(0)); return i.getArgument(0); });
    when(events.findAllByJobIdOrderByAtAscEventIdAsc(anyString())).thenAnswer(i -> history.stream()
        .filter(e -> e.getJobId().equals(i.getArgument(0))).toList());
    service = new CoordPcTaskService(jobs, events, clock);
  }

  ObjectNode expected(String requestId) {
    return JSON.createObjectNode().put("requestId", requestId).put("type", "EXPECTED").put("school", "신구중")
        .put("grade", "중2").put("year", 2026).put("semester", "2학기").put("exam", "기말").put("count", 40);
  }

  String queued() {
    return (String) service.request(ADMIN, "Claude", expected("req-pc-0001")).get("jobId");
  }

  ObjectNode review(String jobId, String reviewer, String verdict, boolean allChecks) {
    ObjectNode a = JSON.createObjectNode().put("briefHash", (String) service.brief(ADMIN, jobId).get("briefHash"))
        .put("reviewer", reviewer).put("verdict", verdict).put("notes", "3번 선택지 부호 확인");
    ObjectNode c = a.putObject("checks");
    for (String k : CoordPcTaskService.REVIEW_CHECKS) c.put(k, allChecks);
    return a;
  }

  ObjectNode done() {
    return JSON.createObjectNode().put("outcome", "DONE").put("summary", "Wiki FINAL 저장").put("wikiRef", "final #12");
  }

  @Test
  @DisplayName("given 맡길 기기 없이 예상문제 요청 when request then 대기 중, 기본 클라우드, 같은 requestId는 같은 작업")
  void requestQueuesIdempotently() {
    // given & when
    Map<String, Object> first = service.request(ADMIN, "Claude", expected("req-pc-0001"));
    Map<String, Object> again = service.request(ADMIN, "Claude", expected("req-pc-0001"));

    // then
    assertThat(first).containsEntry("status", CoordJob.QUEUED).containsEntry("preferredWorker", "CLOUD");
    assertThat((String) first.get("title")).contains("신구중 2학년 2026년 2학기 기말").contains("40문항");
    assertThat(again.get("jobId")).isEqualTo(first.get("jobId"));
    assertThat(store).hasSize(1);
  }

  @Test
  @DisplayName("given 중2·고1·2·중학교 2학년 when wikiGrade then 내신 Wiki 키 형식 「N학년」, 모르는 형식은 그대로")
  void wikiGradeKey() {
    // given & when & then
    assertThat(CoordPcTaskService.wikiGrade("중2")).isEqualTo("2학년");
    assertThat(CoordPcTaskService.wikiGrade("고1")).isEqualTo("1학년");
    assertThat(CoordPcTaskService.wikiGrade("2")).isEqualTo("2학년");
    assertThat(CoordPcTaskService.wikiGrade("중학교 2학년")).isEqualTo("2학년");
    assertThat(CoordPcTaskService.wikiGrade("2학년")).isEqualTo("2학년");
    assertThat(CoordPcTaskService.wikiGrade("예비고1반")).isEqualTo("예비고1반");
  }

  @Test
  @DisplayName("given 관리자가 아님·잘못된 type when request then 거절")
  void requestValidates() {
    // given
    AuthenticatedPrincipal tutor = new AuthenticatedPrincipal(2L, "t", "TUTOR");

    // when & then
    assertThatThrownBy(() -> service.request(tutor, "c", expected("req-pc-0002"))).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.request(ADMIN, "c", expected("req-pc-0003").put("type", "MOVIE")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.request(ADMIN, "c", expected("req-pc-0004").put("preferredWorker", "PHONE")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 학원 PC에 맡긴 작업 when 집 노트북이 가져가려 함 then 학원 PC 우선이라 거절, 30분 뒤에는 가능")
  void fallbackAfterWait() {
    // given
    String id = (String) service.request(ADMIN, "c", expected("req-pc-0007").put("preferredWorker", "ACADEMY_PC")).get("jobId");

    // when & then
    assertThat(service.list(ADMIN, false, "HOME_LAPTOP").get("open")).asList().first()
        .satisfies(v -> assertThat((Map<String, Object>) v).containsEntry("claimable", false));
    assertThatThrownBy(() -> service.claim(ADMIN, id, "HOME_LAPTOP", null))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("30분 뒤");
    clock.plusMinutes(30);
    assertThat(service.claim(ADMIN, id, "HOME_LAPTOP", null)).containsEntry("status", CoordJob.RUNNING);
  }

  @Test
  @DisplayName("given 기본(클라우드) 작업 when 클라우드가 claim then 바로 처리 중, 지시문에 기출 형식·F2R 로고·검수·Wiki 저장 순서")
  void claimGivesInstructions() {
    // given
    String id = queued();

    // when
    Map<String, Object> out = service.claim(ADMIN, id, "CLOUD", "CLOUD-MAKER");

    // then
    String w = (String) out.get("workInstructions");
    assertThat(out).containsEntry("status", CoordJob.RUNNING).containsEntry("makerSession", "CLOUD-MAKER");
    assertThat(w).contains("past_exams").contains("발문").contains("부호").contains(CoordPcTaskService.LOGO_URL)
        .contains("claude -p").contains("f2r_pc_task_review").contains("save_final").contains("디지털 모의고사 탑재는 원장님이 따로 지시할 때만");
    assertThat(w.indexOf("f2r_pc_task_review")).isLessThan(w.indexOf("save_final"));
  }

  @Test
  @DisplayName("given 디지털 모의고사 탑재 지시 when claim then 탑재 준비 단계가 지시문에 들어감")
  void publishDigitalStep() {
    // given
    String id = (String) service.request(ADMIN, "c", expected("req-pc-0005").put("publishDigital", true)).get("jobId");

    // when
    String w = (String) service.claim(ADMIN, id, "CLOUD", null).get("workInstructions");

    // then
    assertThat(w).contains("디지털 모의고사").contains("/digital/").contains("배포 승인");
  }

  @Test
  @DisplayName("given 검수 없음·1곳·2곳만 PASS when finish DONE then 거절, 서로 다른 3곳 PASS 뒤에는 완료(대표 지시 2026-11-22)")
  void finishNeedsThreeReviews() {
    // given
    String id = queued();
    service.claim(ADMIN, id, "CLOUD", "MAKER-1");

    // when & then
    assertThatThrownBy(() -> service.finish(ADMIN, id, done())).hasMessageContaining("0곳");
    service.review(ADMIN, id, review(id, "REVIEW-1", "PASS", true));
    service.review(ADMIN, id, review(id, "REVIEW-1", "PASS", true));
    assertThatThrownBy(() -> service.finish(ADMIN, id, done())).hasMessageContaining("1곳");
    assertThat((String) service.review(ADMIN, id, review(id, "REVIEW-2", "PASS", true)).get("message"))
        .contains("검수 통과 2/3").contains("1곳 더");
    assertThatThrownBy(() -> service.finish(ADMIN, id, done())).hasMessageContaining("3곳 필요").hasMessageContaining("2곳");
    assertThat((String) service.review(ADMIN, id, review(id, "REVIEW-3", "PASS", true)).get("message")).contains("3/3");
    Map<String, Object> out = service.finish(ADMIN, id, done());
    assertThat(out).containsEntry("status", CoordJob.COMPLETED);
    assertThat((Map<String, Object>) out.get("result")).containsEntry("reviewedBy", List.of("REVIEW-1", "REVIEW-2", "REVIEW-3"));
  }

  @Test
  @DisplayName("given PASS 3곳 뒤 FIX when finish then 다시 3곳 필요")
  void fixResetsReviews() {
    // given
    String id = queued();
    service.claim(ADMIN, id, "CLOUD", "MAKER-1");
    service.review(ADMIN, id, review(id, "REVIEW-1", "PASS", true));
    service.review(ADMIN, id, review(id, "REVIEW-2", "PASS", true));
    service.review(ADMIN, id, review(id, "REVIEW-3", "PASS", true));

    // when
    Map<String, Object> fix = service.review(ADMIN, id, review(id, "REVIEW-4", "FIX", false));

    // then
    assertThat((String) fix.get("message")).contains("다른 세션 3곳");
    assertThatThrownBy(() -> service.finish(ADMIN, id, done())).hasMessageContaining("0곳");
    assertThat(service.list(ADMIN, false, null).get("open")).asList().first()
        .satisfies(v -> assertThat((Map<String, Object>) v).containsEntry("reviewsPassed", 0).containsEntry("reviewsRequired", 3));
  }

  @Test
  @DisplayName("given 제작 세션 이름·체크 누락 when review then 자기 검수와 불완전 PASS는 거절")
  void reviewRules() {
    // given
    String id = queued();
    service.claim(ADMIN, id, "CLOUD", "MAKER-1");

    // when & then
    assertThatThrownBy(() -> service.review(ADMIN, id, review(id, "MAKER-1", "PASS", true))).hasMessageContaining("스스로");
    assertThatThrownBy(() -> service.review(ADMIN, id, review(id, "REVIEW-1", "PASS", false))).hasMessageContaining("F2R 로고");
    assertThatThrownBy(() -> service.review(ADMIN, id, review(id, "REVIEW-1", "MAYBE", true))).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 원장님 지시가 있는 처리 중 작업 when 검수 세션이 목록을 봄 then 문항 수·지시가 그대로 보여 다시 묻지 않아도 됨")
  void runningJobShowsOriginalRequest() {
    // given
    String id = (String) service.request(ADMIN, "c", expected("req-pc-0008").put("instructions", "Lesson 7·8만, 서술형 5문항 포함"))
        .get("jobId");
    service.claim(ADMIN, id, "CLOUD", "CLOUD-MAKER");

    // when
    Map<String, Object> row = (Map<String, Object>) ((List<?>) service.list(ADMIN, false, null).get("open")).get(0);

    // then
    assertThat(row).containsEntry("count", 40).containsEntry("instructions", "Lesson 7·8만, 서술형 5문항 포함");
  }

  @Test
  @DisplayName("given 원장님 지시가 있는 작업 when 제작 지시서 then 요청·지시 원문·기출 기준·로고·체크리스트가 md로, briefHash는 항상 같음")
  void briefMarkdown() {
    // given
    String id = (String) service.request(ADMIN, "c", expected("req-pc-0009").put("instructions", "서술형 5문항 포함")).get("jobId");

    // when
    Map<String, Object> first = service.brief(ADMIN, id);
    Map<String, Object> claimed = service.claim(ADMIN, id, "CLOUD", null);

    // then
    String md = (String) first.get("briefMarkdown");
    assertThat(md).startsWith("# 제작 지시서").contains("40문항").contains("> 서술형 5문항 포함").contains("past_exams")
        .contains(CoordPcTaskService.LOGO_URL).contains("- [ ] pastExamStyle").contains("- [ ] logo");
    assertThat(claimed.get("briefMarkdown")).isEqualTo(md);
    assertThat((String) claimed.get("workInstructions")).contains("BRIEF.md").contains("f2r_pc_task_brief");
    assertThat(service.brief(ADMIN, id).get("briefHash")).isEqualTo(first.get("briefHash"));
  }

  @Test
  @DisplayName("given 지시서를 읽지 않은 검수 when review then briefHash가 없거나 틀리면 거절")
  void reviewNeedsBriefHash() {
    // given
    String id = queued();
    service.claim(ADMIN, id, "CLOUD", "MAKER-1");
    ObjectNode noHash = review(id, "REVIEW-1", "PASS", true);
    noHash.remove("briefHash");

    // when & then
    assertThatThrownBy(() -> service.review(ADMIN, id, noHash)).hasMessageContaining("f2r_pc_task_brief");
    assertThatThrownBy(() -> service.review(ADMIN, id, review(id, "REVIEW-1", "PASS", true).put("briefHash", "000000000000")))
        .hasMessageContaining("briefHash");
  }

  @Test
  @DisplayName("given 실패 보고 when finish FAILED then 검수 없이 실패로 끝남")
  void failWithoutReview() {
    // given
    String id = queued();
    service.claim(ADMIN, id, "CLOUD", null);

    // when
    Map<String, Object> out = service.finish(ADMIN, id,
        JSON.createObjectNode().put("outcome", "FAILED").put("summary", "기출 자료 없음"));

    // then
    assertThat(out).containsEntry("status", CoordJob.FAILED).containsEntry("error", "기출 자료 없음");
  }

  @Test
  @DisplayName("given 대기·처리 중 when cancel then 대기 중만 취소")
  void cancelOnlyQueued() {
    // given
    String a = queued();
    String b = (String) service.request(ADMIN, "c", expected("req-pc-0006")).get("jobId");
    service.claim(ADMIN, b, "CLOUD", null);

    // when & then
    assertThat(service.cancel(ADMIN, a)).containsEntry("status", CoordJob.CANCELLED);
    assertThatThrownBy(() -> service.cancel(ADMIN, b)).isInstanceOf(IllegalStateException.class);
  }

  /** 테스트에서 시간을 앞으로 돌리는 시계. */
  static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void plusMinutes(long m) {
      now = now.plusSeconds(m * 60);
    }

    @Override
    public ZoneId getZone() {
      return KstTime.ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  ObjectNode toeic(String requestId) {
    ObjectNode a = JSON.createObjectNode().put("requestId", requestId).put("type", "EXPECTED").put("track", "TOEIC")
        .put("exam", "Part 5·6").put("count", 30).put("pastExamRef", "공개 기출 2회분 메모").put("scopeRef", "어휘 목록 3장");
    a.putArray("ranges").add("12~45").add("72-83쪽").add("92~115");
    a.putArray("itemLabels").add("PAST_ELEMENT").add("CONCEPT");
    return a;
  }

  @Test
  @DisplayName("given 공인시험(TOEIC) 공장 요청 when request then 학교 없이 대기열에 들어가고 구분·범위 구간·자료 위치·문항 표시가 plan에 담긴다")
  void factoryRequestWithoutSchool() {
    // given & when
    Map<String, Object> out = service.request(ADMIN, "WEB", "제작 현황판", toeic("req-pc-0101"));

    // then
    CoordJob job = store.get((String) out.get("jobId"));
    var factory = JSON.readTree(job.getPlanJson()).path("factory");
    assertThat(out).containsEntry("status", CoordJob.QUEUED);
    assertThat((String) out.get("title")).isEqualTo("제작 작업 · TOEIC · Part 5·6 · 예상문제(FINAL) 30문항");
    assertThat(job.getSource()).isEqualTo("WEB");
    assertThat(factory.path("track").asText()).isEqualTo("TOEIC");
    assertThat(FactoryPlan.rangeLabel(factory.path("ranges"))).isEqualTo("12~45쪽, 72~83쪽, 92~115쪽");
    assertThat(factory.path("itemLabels").size()).isEqualTo(2);
  }

  @Test
  @DisplayName("given 학교 시험 구분(국어)인데 학교 없음·모르는 구분·겹치는 범위 when request then 거절")
  void factoryRequestValidates() {
    // given & when & then
    assertThatThrownBy(() -> service.request(ADMIN, "c", toeic("req-pc-0102").put("track", "KOREAN")))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("school");
    assertThatThrownBy(() -> service.request(ADMIN, "c", toeic("req-pc-0103").put("track", "ART")))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("track");
    ObjectNode overlap = toeic("req-pc-0104");
    overlap.putArray("ranges").add("12~45").add("40~50");
    assertThatThrownBy(() -> service.request(ADMIN, "c", overlap)).hasMessageContaining("겹칩니다");
  }

  @Test
  @DisplayName("given 공장 요청 when 제작 지시서 then 구분·범위 구간·자료 위치·문항 표시와 「새로 쓰기」 원칙, 검수 3곳·공정 6단계가 들어간다")
  void factoryBrief() {
    // given
    String id = (String) service.request(ADMIN, "c", toeic("req-pc-0105")).get("jobId");

    // when
    String md = (String) service.brief(ADMIN, id).get("briefMarkdown");
    String w = (String) service.claim(ADMIN, id, "CLOUD", "MAKER-1").get("workInstructions");

    // then
    assertThat(md).contains("과목·시험 구분: TOEIC").contains("12~45쪽, 72~83쪽, 92~115쪽").contains("공개 기출 2회분 메모")
        .contains("기출 평가 요소 기반 / 개념 연습").contains(FactoryPlan.ORIGINALITY).contains("다른 세션 3곳 검수 PASS")
        .contains("① 기출 분석").contains("⑥ 게시").doesNotContain("내신 Wiki `past_exams`");
    assertThat(w).contains("f2r_pc_task_stage").contains("최소 3회").contains(FactoryPlan.ORIGINALITY).contains("원장님이 정한 곳에 저장")
        .contains("/dashboard/factory").doesNotContain("get_final_context");
  }

  @Test
  @DisplayName("given 처리 중 작업 when 제작 세션이 단계 보고 then 상태는 그대로 두고 기록만 남는다, 대기 중·모르는 단계는 거절")
  void stageReport() {
    // given
    String id = queued();
    assertThatThrownBy(() -> service.stage(ADMIN, id, JSON.createObjectNode().put("stage", "DRAFT")))
        .isInstanceOf(IllegalStateException.class);
    service.claim(ADMIN, id, "CLOUD", "MAKER-1");

    // when
    Map<String, Object> out = service.stage(ADMIN, id, JSON.createObjectNode().put("stage", "draft").put("note", "기출 3회분 평가 요소 정리 끝"));

    // then
    assertThat(out).containsEntry("status", CoordJob.RUNNING).containsEntry("stage", "DRAFT").containsEntry("stageLabel", "변형·유사 문제 제작");
    CoordJobEvent last = history.get(history.size() - 1);
    assertThat(last.getActor()).isEqualTo(FactoryStage.STAGE_ACTOR);
    assertThat(last.getMessage()).isEqualTo("DRAFT · 기출 3회분 평가 요소 정리 끝");
    assertThatThrownBy(() -> service.stage(ADMIN, id, JSON.createObjectNode().put("stage", "PUBLISH")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
