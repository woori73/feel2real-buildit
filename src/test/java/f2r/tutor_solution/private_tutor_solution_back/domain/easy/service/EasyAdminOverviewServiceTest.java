package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyBusinessResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardResponse.EasyOverdueStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWorkspaceMonthlyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.StudentOverviewRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

@DisplayName("관리자 대시보드 현황(MCP) — 재무 + 학생 수업 요일, 실명 없음")
class EasyAdminOverviewServiceTest {

  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T06:00:00Z"), EasyAttendanceService.KST);
  final AuthenticatedPrincipal admin = new AuthenticatedPrincipal(1L, "admin", "ADMIN");
  EasyTutorAccessService access = mock(EasyTutorAccessService.class);
  EasyWorkspaceService workspace = mock(EasyWorkspaceService.class);
  EasyAttendanceService attendance = mock(EasyAttendanceService.class);
  EasyAdminOverviewService service;

  static StudentOverviewRow row(String name, List<String> days, String source, int month) {
    return new StudentOverviewRow(1L, name, "비밀중학교", 8, List.of("A반"), days, source, month,
        month > 0 ? LocalDate.of(2026, 11, 6) : null);
  }

  @BeforeEach
  void setUp() {
    service = new EasyAdminOverviewService(access, workspace, attendance, CLOCK);
    when(access.allowedBusinesses(admin)).thenReturn(List.of(new EasyBusinessResponse(7L, "AE", "압구정에듀홀릭학원"),
        new EasyBusinessResponse(8L, "B", "다른 사업체")));
    when(workspace.monthly(eq(7L), any())).thenReturn(new EasyWorkspaceMonthlyResponse(
        "2026-11", 7L, "압구정에듀홀릭학원", 3_055_000, 3_883_838, -828_838, 90_000, 41, 0, 0, 0, 0, 2, 0,
        "OPEN", false, EasyWorkspaceMonthlyResponse.Sources.contract(), List.of(),
        List.of(new EasyOverdueStudent(3L, "실명학생", "비밀중학교", 90_000))));
    when(attendance.studentOverview(7L)).thenReturn(List.of(
        row("가나", List.of("MON", "WED"), "ATTENDANCE", 3),
        row("다라", List.of("WED"), "CLASS", 0),
        row("마바", List.of(), null, 0)));
  }

  @Test
  @DisplayName("given 사업체 미지정 when 읽으면 then 첫 사업체의 이번 달 재무·요일별 학생 수·가명 학생 목록을 준다")
  void readsFirstBusiness() {
    // when
    Map<String, Object> out = service.mcpOverview(admin, null);

    // then
    verify(access).requireAllowedBusiness(admin, 7L);
    verify(workspace).monthly(7L, YearMonth.of(2026, 11));
    assertThat(out.get("month")).isEqualTo("2026-11");
    @SuppressWarnings("unchecked") var finance = (Map<String, Object>) out.get("finance");
    assertThat(finance).containsEntry("income", 3_055_000L).containsEntry("expense", 3_883_838L)
        .containsEntry("receivable", 90_000L).containsEntry("overdueStudentCount", 1);
    assertThat(out.get("weekdayStudentCounts")).isEqualTo(
        Map.of("MON", 1, "TUE", 0, "WED", 2, "THU", 0, "FRI", 0, "SAT", 0, "SUN", 0));
    @SuppressWarnings("unchecked") var students = (List<Map<String, Object>>) out.get("students");
    assertThat(students).extracting(s -> s.get("alias")).containsExactly("S01", "S02", "S03");
    assertThat(students.get(0)).containsEntry("weekdays", List.of("MON", "WED")).containsEntry("monthCheckIns", 3)
        .containsEntry("lastCheckIn", "2026-11-06").containsEntry("grade", 8);
  }

  @Test
  @DisplayName("given 실명·학교가 있는 원본 when 읽으면 then 응답 어디에도 실명·학교가 없다")
  void noRealNamesOrSchools() {
    // when
    String text = service.mcpOverview(admin, 7L).toString();

    // then
    assertThat(text).doesNotContain("가나", "다라", "마바", "실명학생", "비밀중학교");
  }

  @Test
  @DisplayName("given 관리자가 아님 when 읽으면 then 거절한다")
  void adminOnly() {
    // when / then
    assertThatThrownBy(() -> service.mcpOverview(new AuthenticatedPrincipal(2L, "t", "TUTOR"), 7L))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.mcpOverview(null, 7L)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("given 사업체가 없음 when 읽으면 then 빈 목록과 안내 문장")
  void noBusiness() {
    // given
    when(access.allowedBusinesses(admin)).thenReturn(List.of());

    // when
    Map<String, Object> out = service.mcpOverview(admin, null);

    // then
    assertThat(out).containsEntry("businesses", List.of()).containsEntry("message", "사업체가 없습니다.");
  }

  @Test
  @DisplayName("given 요일이 없는 학생 when 요일별 학생 수 then 0명 요일도 월~일 순서로 모두 준다")
  void weekdayCountsKeepsOrder() {
    // when
    var counts = EasyAdminOverviewService.weekdayCounts(List.of(row("x", List.of("SUN"), "CLASS", 0)));

    // then
    assertThat(counts.keySet()).containsExactly("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    assertThat(counts.get("SUN")).isEqualTo(1);
  }
}
