package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyBusinessResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWorkspaceMonthlyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.StudentOverviewRow;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * 학원 현황(재무 + 재원 학생 수업 요일)을 Claude(MCP)가 읽어 함께 의논할 수 있게 묶는다. 읽기 전용.
 *
 * <p>개인정보: 학생 실명·학교·연락처는 보내지 않는다. 학생은 이름순 가명(S01…)과 학년·반·요일·등원 횟수만 준다.
 */
@Service
public class EasyAdminOverviewService {

  static final String INSTRUCTION = "F2R 장부·출결부의 학원 현황입니다. 이번 달 재무와 학생별 수업 요일을 보고"
      + " 운영자와 할 일(미수 안내·요일별 반 편성·시간표 조정·지출 점검 등)을 의논하세요. 이 도구는 읽기만 하며 아무것도 저장하지 않습니다."
      + " 학생은 가명(S01…)이며 실명을 추측하거나 요구하지 마세요.";
  static final List<String> WEEK = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

  private final EasyTutorAccessService accessService;
  private final EasyWorkspaceService workspaceService;
  private final EasyAttendanceService attendanceService;
  private final Clock clock;

  @Autowired
  public EasyAdminOverviewService(EasyTutorAccessService accessService, EasyWorkspaceService workspaceService,
      EasyAttendanceService attendanceService) {
    this(accessService, workspaceService, attendanceService, Clock.system(EasyAttendanceService.KST));
  }

  EasyAdminOverviewService(EasyTutorAccessService accessService, EasyWorkspaceService workspaceService,
      EasyAttendanceService attendanceService, Clock clock) {
    this.accessService = accessService;
    this.workspaceService = workspaceService;
    this.attendanceService = attendanceService;
    this.clock = clock;
  }

  /** MCP 도구 read_admin_dashboard. businessId가 없으면 첫 사업체. ADMIN만. */
  public Map<String, Object> mcpOverview(AuthenticatedPrincipal p, Long businessId) {
    if (p == null || !"ADMIN".equals(p.role())) throw new AccessDeniedException("관리자만 볼 수 있습니다.");
    List<EasyBusinessResponse> businesses = accessService.allowedBusinesses(p);
    if (businesses.isEmpty()) return Map.of("instruction", INSTRUCTION, "businesses", List.of(), "message", "사업체가 없습니다.");
    Long bid = businessId != null ? businessId : businesses.get(0).businessId();
    accessService.requireAllowedBusiness(p, bid);

    YearMonth month = YearMonth.now(clock);
    EasyWorkspaceMonthlyResponse m = workspaceService.monthly(bid, month);
    List<StudentOverviewRow> rows = attendanceService.studentOverview(bid);

    Map<String, Object> finance = new LinkedHashMap<>();
    finance.put("income", m.income());
    finance.put("expense", m.expense());
    finance.put("profit", m.profit());
    finance.put("receivable", m.receivable());
    finance.put("studentCount", m.studentCount());
    finance.put("overdueStudentCount", m.overdueStudents() == null ? 0 : m.overdueStudents().size());
    finance.put("openReviewCount", m.openReviewCount());

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("instruction", INSTRUCTION);
    out.put("privacy", "실명·학교·연락처 없음 · 이름순 가명(S01…) · 읽기 전용");
    out.put("business", Map.of("businessId", bid, "name", m.businessName() == null ? "" : m.businessName()));
    out.put("businesses", businesses.stream().map(b -> Map.of("businessId", b.businessId(), "name", b.name())).toList());
    out.put("month", month.toString());
    out.put("finance", finance);
    out.put("weekdayStudentCounts", weekdayCounts(rows));
    out.put("students", pseudonymize(rows));
    out.put("weekdayRule", "최근 4주 실제 등원 요일(ATTENDANCE), 없으면 배정된 반 요일(CLASS), 둘 다 없으면 빈 값");
    out.put("url", "/easy/attendance");
    return out;
  }

  /** 요일별 수업 학생 수(월~일 순서, 0명도 포함). */
  static Map<String, Integer> weekdayCounts(List<StudentOverviewRow> rows) {
    Map<String, Integer> out = new LinkedHashMap<>();
    WEEK.forEach(d -> out.put(d, 0));
    for (StudentOverviewRow r : rows) r.weekdays().forEach(d -> out.computeIfPresent(d, (k, v) -> v + 1));
    return out;
  }

  /** 이름 대신 S01, S02 …(목록 순서). 학교는 빼고 학년·반·요일·횟수만. */
  static List<Map<String, Object>> pseudonymize(List<StudentOverviewRow> rows) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      StudentOverviewRow r = rows.get(i);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("alias", String.format("S%02d", i + 1));
      row.put("grade", r.grade());
      row.put("classes", r.classes());
      row.put("weekdays", r.weekdays());
      row.put("weekdaySource", r.weekdaySource());
      row.put("monthCheckIns", r.monthCheckIns());
      row.put("lastCheckIn", r.lastCheckIn() == null ? null : r.lastCheckIn().toString());
      out.add(row);
    }
    return out;
  }
}
