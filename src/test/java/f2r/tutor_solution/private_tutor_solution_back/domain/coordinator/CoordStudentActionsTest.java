package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyStudentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.ClassView;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.WeekdayForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.WeekdayView;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyStudentService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("CoordStudentActions — 학생·반 말 명령 초안·실행·되돌리기")
class CoordStudentActionsTest {

  static final JsonMapper JSON = JsonMapper.builder().build();
  EasyStudentRepository students;
  EasyStudentService studentService;
  EasyAttendanceService attendance;
  CoordStudentActions actions;
  EasyStudent minsu;

  @BeforeEach
  void setUp() {
    students = mock(EasyStudentRepository.class);
    studentService = mock(EasyStudentService.class);
    attendance = mock(EasyAttendanceService.class);
    actions = new CoordStudentActions(students, studentService, attendance);
    minsu = EasyStudent.builder().name("김민수").school("가짜중").grade((byte) 2).phone("010-1").parentPhone("010-2")
        .status(EasyStudentStatus.ENROLLED).build();
    ReflectionTestUtils.setField(minsu, "studentId", 11L);
    when(students.findById(11L)).thenReturn(Optional.of(minsu));
    when(students.findAllByOrderByNameAsc()).thenReturn(List.of(minsu));
    when(attendance.listClasses(3L, false)).thenReturn(List.of(
        new ClassView(5L, "화목 중2", "박", null, List.of("TUE", "THU"), "16:00", "18:00", null, 10, "ACTIVE", 3),
        new ClassView(6L, "월수 중2", "박", null, List.of("MON", "WED"), null, null, null, null, "ACTIVE", 2)));
  }

  ObjectNode args() {
    return JSON.createObjectNode();
  }

  @Test
  @DisplayName("given 같은 이름 재원생 when 학생 등록 초안 then 실명은 title에만, aiTitle은 가린 이름과 동명이인 경고")
  void prepareCreate_masksAndWarnsDuplicate() {
    // given & when
    var d = actions.prepareStudent("create", args().put("name", "김민수").put("grade", 1));

    // then
    assertThat(d.kind()).isEqualTo(CoordStudentActions.STUDENT_CREATE);
    assertThat(d.title()).contains("김민수");
    assertThat(d.aiTitle()).contains("김○수", "같은 이름").doesNotContain("김민수");
  }

  @Test
  @DisplayName("given 이상한 값 when 초안 then 거절(이름 없음·바꿀 것 없음·잘못된 상태·잘못된 요일·없는 반)")
  void prepare_rejectsInvalid() {
    // given & when & then
    assertThatThrownBy(() -> actions.prepareStudent("CREATE", args())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> actions.prepareStudent("UPDATE", args().put("studentId", 11))).hasMessageContaining("바꿀 내용");
    assertThatThrownBy(() -> actions.prepareStudent("UPDATE", args().put("studentId", 11).put("status", "GONE")))
        .hasMessageContaining("재원 상태");
    assertThatThrownBy(() -> actions.prepareClass(3L, "CREATE", (ObjectNode) args().put("name", "A")
        .set("weekdays", JSON.createArrayNode().add("화")))).hasMessageContaining("요일");
    assertThatThrownBy(() -> actions.prepareClass(3L, "ASSIGN", args().put("studentId", 11).put("classId", 99)))
        .hasMessageContaining("반을 찾을 수 없습니다");
    assertThatThrownBy(() -> actions.prepareStudent("DELETE", args())).hasMessageContaining("action");
  }

  @Test
  @DisplayName("given 반 이동 초안 when 실행 후 되돌리기 then move(5→6) 후 move(6→5)")
  void moveAndUndo() {
    // given
    var d = actions.prepareClass(3L, "MOVE", args().put("studentId", 11).put("classId", 5).put("targetClassId", 6));
    JsonNode plan = JSON.valueToTree(withBid(d.plan()));

    // when
    Map<String, Object> r = actions.execute(d.kind(), plan);
    actions.undo(d.kind(), plan, JSON.valueToTree(r));

    // then
    assertThat(d.aiTitle()).isEqualTo("김○수 · 화목 중2 → 월수 중2 반 이동");
    verify(attendance).move(3L, 5L, 11L, 6L);
    verify(attendance).move(3L, 6L, 11L, 5L);
  }

  @Test
  @DisplayName("given 학생 수정 when 실행 then 연락처는 그대로, 되돌리기는 그사이 바뀌지 않았을 때만")
  void updateKeepsPhonesAndUndoChecksDrift() {
    // given
    var d = actions.prepareStudent("UPDATE", args().put("studentId", 11).put("status", "WITHDRAWN"));
    JsonNode plan = JSON.valueToTree(d.plan());

    // when
    Map<String, Object> r = actions.execute(d.kind(), plan);

    // then
    verify(studentService).update(eq(11L), eq(new EasyStudentRequest("김민수", "가짜중", (byte) 2, "010-1", "010-2",
        "WITHDRAWN", null, null)));
    // 그사이 다른 곳에서 학년이 바뀌면(after와 다름) 되돌리기 거절
    ReflectionTestUtils.setField(minsu, "grade", (byte) 5);
    assertThatThrownBy(() -> actions.undo(d.kind(), plan, JSON.valueToTree(r))).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("given 반 만들기 실행 when 그사이 학생이 배정됨 then 되돌리기 거절, 비어 있으면 반 종료")
  void classCreateUndo() {
    // given
    var d = actions.prepareClass(3L, "CREATE", args().put("name", "새반"));
    JsonNode plan = JSON.valueToTree(withBid(d.plan()));
    JsonNode result = JSON.createObjectNode().put("classId", 9);
    when(attendance.members(3L, 9L)).thenReturn(List.of(
        new EasyAttendanceService.MemberView(11L, "김민수", "가짜중", 2, "ENROLLED", null)));

    // when & then
    assertThatThrownBy(() -> actions.undo(d.kind(), plan, result)).isInstanceOf(IllegalStateException.class);
    verify(attendance, never()).endClass(anyLong(), anyLong());
    when(attendance.members(3L, 9L)).thenReturn(List.of());
    actions.undo(d.kind(), plan, result);
    verify(attendance).endClass(3L, 9L);
  }

  @Test
  @DisplayName("given 수업 요일 초안 when 실행 then 지금 revision으로 저장하고 이전 요일을 기억한다")
  void weekdays() {
    // given
    var d = actions.prepareClass(3L, "WEEKDAYS", (ObjectNode) args().put("studentId", 11)
        .set("weekdays", JSON.createArrayNode().add("thu").add("TUE")));
    when(attendance.studentWeekdays(3L, 11L)).thenReturn(new WeekdayView(List.of("MON"), 4L));
    when(attendance.updateStudentWeekdays(eq(3L), eq(11L), any())).thenReturn(new WeekdayView(List.of("TUE", "THU"), 5L));

    // when
    Map<String, Object> r = actions.execute(d.kind(), JSON.valueToTree(withBid(d.plan())));

    // then
    assertThat(d.aiTitle()).isEqualTo("김○수 · 수업 요일 → 화목");
    verify(attendance).updateStudentWeekdays(3L, 11L, new WeekdayForm(List.of("TUE", "THU"), 4L));
    assertThat(r).containsEntry("before", List.of("MON"));
  }

  @Test
  @DisplayName("given 반 목록 조회 when classId 지정 then 학생은 가린 이름만")
  void classesMasksMembers() {
    // given
    when(attendance.listClasses(3L, false)).thenReturn(List.of(
        new ClassView(5L, "화목 중2", "박", null, List.of("TUE"), "16:00", "18:00", null, 10, "ACTIVE", 1)));
    when(attendance.members(3L, 5L)).thenReturn(List.of(
        new EasyAttendanceService.MemberView(11L, "김민수", "가짜중", 2, "ENROLLED", null)));

    // when
    Map<String, Object> out = actions.classes(3L, 5L, false);

    // then
    assertThat(out.toString()).contains("김○수", "화목 중2").doesNotContain("김민수", "가짜중");
  }

  static Map<String, Object> withBid(Map<String, Object> plan) {
    var m = new java.util.LinkedHashMap<>(plan);
    m.put("businessId", 3L);
    return m;
  }
}
