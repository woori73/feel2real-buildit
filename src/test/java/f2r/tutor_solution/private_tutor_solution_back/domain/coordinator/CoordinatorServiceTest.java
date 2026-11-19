package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyBusinessResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.EventView;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.StudentOverviewRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

@DisplayName("CoordinatorService — 학생 검색·출결 초안·승인")
class CoordinatorServiceTest {

  static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 16, 5);
  static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(1L, "owner", "ADMIN");
  static final String REQ = "8e4b5f07-7704-4d36-b7af-8347498a93d3";

  CoordJobRepository jobs;
  CoordJobEventRepository events;
  EasyAttendanceService attendance;
  EasyTutorAccessService access;
  Map<String, CoordJob> store;
  CoordinatorService service;

  @BeforeEach
  void setUp() {
    jobs = mock(CoordJobRepository.class);
    events = mock(CoordJobEventRepository.class);
    attendance = mock(EasyAttendanceService.class);
    access = mock(EasyTutorAccessService.class);
    store = new HashMap<>();
    when(jobs.save(any())).thenAnswer(inv -> { CoordJob j = inv.getArgument(0); store.put(j.getJobId(), j); return j; });
    when(jobs.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.<String>getArgument(0))));
    when(jobs.findByUserIdAndRequestId(anyLong(), anyString())).thenAnswer(inv -> store.values().stream()
        .filter(j -> j.getUserId().equals(inv.getArgument(0)) && j.getRequestId().equals(inv.getArgument(1))).findFirst());
    when(access.allowedBusinesses(any())).thenReturn(List.of(new EasyBusinessResponse(3L, "ACADEMY", "압구정에듀홀릭")));
    when(attendance.studentOverview(3L)).thenReturn(List.of(
        row(11L, "김민수", 2, List.of("화목 중2")), row(12L, "김민수", 3, List.of("월수 중3")), row(13L, "이수", 1, List.of())));
    service = new CoordinatorService(jobs, events, attendance, access, clock(NOW));
  }

  static Clock clock(LocalDateTime t) {
    return Clock.fixed(t.atZone(KstTime.ZONE).toInstant(), KstTime.ZONE);
  }

  static StudentOverviewRow row(Long id, String name, int grade, List<String> classes) {
    return new StudentOverviewRow(id, name, "가짜중", grade, classes, List.of("TUE"), "CLASS", 0, null);
  }

  Map<String, Object> mark(String type) {
    return service.requestAttendance(ADMIN, "MCP", "grant-1",
        new CoordinatorService.AttendanceRequest(REQ, null, 11L, type, null, null), "https://f2r.test");
  }

  @Test
  @DisplayName("given 이름 when maskName then 첫·끝 글자만 남긴다")
  void maskName() {
    // given & when & then
    assertThat(CoordinatorService.maskName("김민수")).isEqualTo("김○수");
    assertThat(CoordinatorService.maskName("이수")).isEqualTo("이○");
    assertThat(CoordinatorService.maskName("남궁민수")).isEqualTo("남○○수");
    assertThat(CoordinatorService.maskName("민")).isEqualTo("○");
    assertThat(CoordinatorService.maskName(null)).isEmpty();
  }

  @Test
  @DisplayName("given 짧거나 이상한 requestId when 확인 then 거절")
  void requireRequestId() {
    // given & when & then
    assertThat(CoordinatorService.requireRequestId(" " + REQ + " ")).isEqualTo(REQ);
    assertThatThrownBy(() -> CoordinatorService.requireRequestId("abc")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CoordinatorService.requireRequestId("a b c d e f g h")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 같은 이름 2명 when 검색 then 가린 이름·학년·반과 duplicateName=true, 실명·학교는 없다")
  void search_duplicateNames() {
    // given & when
    Map<String, Object> out = service.searchStudents(ADMIN, null, "김민수");

    // then
    assertThat(out.get("duplicateName")).isEqualTo(true);
    @SuppressWarnings("unchecked") var matches = (List<Map<String, Object>>) out.get("matches");
    assertThat(matches).hasSize(2).allSatisfy(m -> {
      assertThat(m.get("displayName")).isEqualTo("김○수");
      assertThat(m).doesNotContainKeys("name", "school");
    });
    assertThat(out.toString()).doesNotContain("김민수", "가짜중");
  }

  @Test
  @DisplayName("given 관리자 아님 when 검색 then 거절")
  void search_requiresAdmin() {
    // given
    var tutor = new AuthenticatedPrincipal(2L, "t", "TUTOR");

    // when & then
    assertThatThrownBy(() -> service.searchStudents(tutor, null, "김")).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given 오늘 등원 기록 없음 when 출결 초안 then review_required·승인 링크·AI에는 가린 이름, 기록은 하지 않는다")
  void mark_createsDraftWithoutRecording() {
    // given
    when(attendance.activeEventOn(3L, 11L, "CHECK_IN", NOW.toLocalDate())).thenReturn(Optional.empty());

    // when
    Map<String, Object> out = mark(null);

    // then
    assertThat(out.get("status")).isEqualTo("review_required");
    assertThat((String) out.get("approvalUrl")).startsWith("https://f2r.test/admin/coord/approvals?id=");
    assertThat(out.toString()).doesNotContain("김민수", "가짜중").contains("김○수");
    verify(attendance, never()).record(anyLong(), any());
    CoordJob job = store.values().iterator().next();
    assertThat(job.getPlanJson()).contains("김민수", "CHECK_IN", "2026-11-19T16:05");
    assertThat(job.getPlanHash()).isEqualTo(CoordinatorService.sha256(job.getPlanJson()));
    assertThat(job.getExpiresAt()).isEqualTo(NOW.plusMinutes(30));
  }

  @Test
  @DisplayName("given 같은 requestId when 두 번 요청 then 같은 작업 하나만")
  void mark_sameRequestIdIsIdempotent() {
    // given
    when(attendance.activeEventOn(anyLong(), anyLong(), anyString(), any())).thenReturn(Optional.empty());

    // when
    Object first = mark(null).get("jobId");
    Object second = mark(null).get("jobId");

    // then
    assertThat(second).isEqualTo(first);
    assertThat(store).hasSize(1);
  }

  @Test
  @DisplayName("given 오늘 이미 등원 when 출결 초안 then ALREADY_RECORDED, 초안을 만들지 않는다")
  void mark_alreadyRecorded() {
    // given
    when(attendance.activeEventOn(3L, 11L, "CHECK_IN", NOW.toLocalDate())).thenReturn(Optional.of(
        new EventView(77L, "CHECK_IN", "등원", NOW.minusHours(1), null, "MANUAL", null, null)));

    // when
    Map<String, Object> out = mark("CHECK_IN");

    // then
    assertThat(out.get("status")).isEqualTo("ALREADY_RECORDED");
    assertThat(out.get("eventId")).isEqualTo(77L);
    assertThat(store).isEmpty();
  }

  @Test
  @DisplayName("given 잘못된 구분·재원생 아님 when 출결 초안 then 안내와 함께 거절")
  void mark_rejectsInvalid() {
    // given & when & then
    assertThatThrownBy(() -> mark("SLEEP")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.requestAttendance(ADMIN, "MCP", null,
        new CoordinatorService.AttendanceRequest(REQ, null, 999L, null, null, null), ""))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("재원");
  }

  @Test
  @DisplayName("given 초안 when 같은 planHash로 승인 then 기록·재확인 후 completed")
  void approve_records() {
    // given
    when(attendance.activeEventOn(anyLong(), anyLong(), anyString(), any())).thenReturn(Optional.empty());
    String jobId = (String) mark(null).get("jobId");
    String hash = store.get(jobId).getPlanHash();
    var saved = new EventView(90L, "CHECK_IN", "등원", NOW, "AI", "MANUAL", null, null);
    when(attendance.record(eq(3L), any())).thenReturn(saved);
    when(attendance.studentHistory(3L, 11L)).thenReturn(List.of(saved));

    // when
    Map<String, Object> out = service.approve(ADMIN, jobId, hash, "");

    // then
    assertThat(out.get("status")).isEqualTo("completed");
    assertThat(out.toString()).contains("\"eventId\":90");
    verify(attendance).record(eq(3L), eq(new EasyAttendanceService.RecordForm(null, 11L, "CHECK_IN", "2026-11-19T16:05",
        "AI 코디네이터 요청 · 승인 owner")));
  }

  @Test
  @DisplayName("given 초안 when 다른 planHash·두 번째 승인 then 409용 IllegalState, 기록하지 않는다")
  void approve_rejectsStaleOrRepeated() {
    // given
    when(attendance.activeEventOn(anyLong(), anyLong(), anyString(), any())).thenReturn(Optional.empty());
    String jobId = (String) mark(null).get("jobId");

    // when & then
    assertThatThrownBy(() -> service.approve(ADMIN, jobId, "deadbeef", "")).isInstanceOf(IllegalStateException.class);
    service.reject(ADMIN, jobId, "");
    assertThatThrownBy(() -> service.approve(ADMIN, jobId, store.get(jobId).getPlanHash(), ""))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("이미 처리");
    verify(attendance, never()).record(anyLong(), any());
  }

  @Test
  @DisplayName("given 승인 사이에 같은 기록이 생김 when 승인 then failed, 다시 기록하지 않는다")
  void approve_duplicateAtApproval() {
    // given
    when(attendance.activeEventOn(anyLong(), anyLong(), anyString(), any())).thenReturn(Optional.empty());
    String jobId = (String) mark(null).get("jobId");
    when(attendance.activeEventOn(anyLong(), anyLong(), anyString(), any())).thenReturn(Optional.of(
        new EventView(5L, "CHECK_IN", "등원", NOW, null, "MANUAL", null, null)));

    // when
    Map<String, Object> out = service.approve(ADMIN, jobId, store.get(jobId).getPlanHash(), "");

    // then
    assertThat(out.get("status")).isEqualTo("failed");
    verify(attendance, never()).record(anyLong(), any());
  }

  @Test
  @DisplayName("given 30분 지난 초안 when 승인 then expired, 기록하지 않는다")
  void approve_expired() {
    // given
    when(attendance.activeEventOn(anyLong(), anyLong(), anyString(), any())).thenReturn(Optional.empty());
    String jobId = (String) mark(null).get("jobId");
    var later = new CoordinatorService(jobs, events, attendance, access, clock(NOW.plusMinutes(31)));

    // when & then
    assertThatThrownBy(() -> later.approve(ADMIN, jobId, store.get(jobId).getPlanHash(), ""))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("30분");
    assertThat(store.get(jobId).getStatus()).isEqualTo("expired");
    verify(attendance, never()).record(anyLong(), any());
  }

  @Test
  @DisplayName("given 다른 관리자의 초안 when 조회·승인 then 거절")
  void own_onlyOwner() {
    // given
    when(attendance.activeEventOn(anyLong(), anyLong(), anyString(), any())).thenReturn(Optional.empty());
    String jobId = (String) mark(null).get("jobId");
    var other = new AuthenticatedPrincipal(9L, "other", "ADMIN");

    // when & then
    assertThatThrownBy(() -> service.get(other, jobId, "", true)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.approve(other, jobId, "x", "")).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given 초안 when 사람용 조회 then 실명·학교·planHash가 있고 AI용 조회에는 없다")
  void view_humanVsAi() {
    // given
    when(attendance.activeEventOn(anyLong(), anyLong(), anyString(), any())).thenReturn(Optional.empty());
    String jobId = (String) mark(null).get("jobId");

    // when
    Map<String, Object> human = service.get(ADMIN, jobId, "", true);
    Map<String, Object> ai = service.get(ADMIN, jobId, "", false);

    // then
    assertThat(human.toString()).contains("김민수", "가짜중", "planHash");
    assertThat(ai.toString()).doesNotContain("김민수", "가짜중", "planHash");
  }
}
