package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.GradingForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.GradingView;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.LessonForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.LessonView;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLessonRecordService.WrongItem;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("CoordRecordActions — 학생부 말 명령(수업 기록·채점 기록) 초안·실행·되돌리기")
class CoordRecordActionsTest {

  static final JsonMapper JSON = JsonMapper.builder().build();
  EasyLessonRecordService records;
  CoordRecordActions actions;

  @BeforeEach
  void setUp() {
    records = mock(EasyLessonRecordService.class);
    EasyStudentRepository students = mock(EasyStudentRepository.class);
    EasyStudent s = EasyStudent.builder().name("김민수").status(EasyStudentStatus.ENROLLED).build();
    ReflectionTestUtils.setField(s, "studentId", 11L);
    when(students.findById(11L)).thenReturn(Optional.of(s));
    Clock clock = Clock.fixed(LocalDateTime.of(2026, 11, 20, 18, 0).atZone(KstTime.ZONE).toInstant(), KstTime.ZONE);
    actions = new CoordRecordActions(records, students, clock);
  }

  ObjectNode args() {
    return JSON.createObjectNode().put("studentId", 11);
  }

  @Test
  @DisplayName("given 날짜 없이 수업 범위·숙제 when LESSON 초안 then 오늘 날짜, AI 제목은 가린 이름")
  void lessonDraft() {
    // given & when
    var d = actions.prepare("RECORD", 3L, "lesson", args().put("lessonRange", "능률 3과 p.40~45")
        .put("homeworkCheck", "done").put("nextHomework", "p.46~47"));

    // then
    assertThat(d.kind()).isEqualTo(CoordRecordActions.LESSON_RECORD);
    assertThat(d.aiTitle()).isEqualTo("김○수 · 2026-11-20 수업 기록 · 수업 범위: 능률 3과 p.40~45 / 숙제: 다 해 옴 / 낸 숙제: p.46~47");
    assertThat(d.title()).startsWith("김민수");
  }

  @Test
  @DisplayName("given 전체 20·틀린 2문항(맞은 수 없음) when GRADING 초안 then 맞은 수 18로 계산, 제목에 점수·틀린 번호")
  void gradingDraftComputesCorrect() {
    // given
    var a = args().put("subjectArea", "문법").put("totalCount", 20).put("feedback", "관계대명사 복습");
    a.putArray("wrongItems").add(JSON.createObjectNode().put("no", "3").put("topic", "관계대명사").put("reason", "what/that"))
        .add(JSON.createObjectNode().put("no", "7").put("topic", "시제"));

    // when
    var d = actions.prepare("RECORD", 3L, "GRADING", a);

    // then
    assertThat(d.plan()).containsEntry("correctCount", 18);
    assertThat(d.aiTitle()).isEqualTo("김○수 · 2026-11-20 채점 기록 · 문법 · 18/20 · 틀린 문항 3,7 · 피드백 있음");
  }

  @Test
  @DisplayName("given 이상한 값 when 초안 then 거절(없는 action·잘못된 숙제 확인·날짜 형식·내용 없음·학생 없음)")
  void draftRejectsInvalid() {
    // given & when & then
    assertThatThrownBy(() -> actions.prepare("RECORD", 3L, "DELETE", args())).hasMessageContaining("action");
    assertThatThrownBy(() -> actions.prepare("RECORD", 3L, "LESSON", args().put("homeworkCheck", "maybe")))
        .hasMessageContaining("homeworkCheck");
    assertThatThrownBy(() -> actions.prepare("RECORD", 3L, "LESSON", args().put("lessonDate", "11월 20일").put("lessonRange", "x")))
        .hasMessageContaining("YYYY-MM-DD");
    assertThatThrownBy(() -> actions.prepare("RECORD", 3L, "LESSON", args())).hasMessageContaining("하나는");
    assertThatThrownBy(() -> actions.prepare("RECORD", 3L, "LESSON", JSON.createObjectNode())).hasMessageContaining("studentId");
  }

  @Test
  @DisplayName("given 채점 초안 when 실행 후 되돌리기 then MCP 출처로 저장, 학생부 링크(사진 올리기) 반환, 되돌리면 삭제")
  void gradingExecuteAndUndo() {
    // given
    var a = args().put("totalCount", 10).put("correctCount", 9);
    a.putArray("wrongItems").add(JSON.createObjectNode().put("no", "4"));
    var d = actions.prepare("RECORD", 3L, "GRADING", a);
    when(records.createGrading(eq(3L), eq(11L), any(), eq("MCP"))).thenReturn(
        new GradingView(55L, 11L, null, "2026-11-20", null, null, 10, 9, List.of(new WrongItem("4", null, null)), null, "MCP", List.of()));

    // when
    Map<String, Object> r = actions.execute(d.kind(), JSON.valueToTree(d.plan()));
    actions.undo(d.kind(), JSON.valueToTree(d.plan()), JSON.valueToTree(r));

    // then
    verify(records).createGrading(3L, 11L, new GradingForm(null, "2026-11-20", null, null, 10, 9,
        List.of(new WrongItem("4", null, null)), null), "MCP");
    assertThat(r).containsEntry("linkPath", "/easy/records?businessId=3&studentId=11&grading=55");
    verify(records).deleteGrading(3L, 55L);
  }

  @Test
  @DisplayName("given 수업 초안 when 실행 후 되돌리기 then 수업 기록 생성·삭제")
  void lessonExecuteAndUndo() {
    // given
    var d = actions.prepare("RECORD", 3L, "LESSON", args().put("lessonDate", "2026-11-19").put("teacherNote", "집중 좋음"));
    when(records.createLesson(eq(3L), eq(11L), any(), eq("MCP"))).thenReturn(
        new LessonView(77L, 11L, null, null, "2026-11-19", null, null, null, null, null, "집중 좋음", "MCP"));

    // when
    Map<String, Object> r = actions.execute(d.kind(), JSON.valueToTree(d.plan()));
    actions.undo(d.kind(), JSON.valueToTree(d.plan()), JSON.valueToTree(r));

    // then
    verify(records).createLesson(3L, 11L, new LessonForm(null, "2026-11-19", null, null, null, null, "집중 좋음"), "MCP");
    verify(records).deleteLesson(3L, 77L);
  }

  @Test
  @DisplayName("given 학생부 when AI용 조회 then 이름은 가리고 학교는 뺀다")
  void bookMasks() {
    // given
    when(records.studentBook(3L, 11L)).thenReturn(new java.util.LinkedHashMap<>(Map.of(
        "studentId", 11L, "name", "김민수", "school", "가짜중", "gradings", List.of())));

    // when
    Map<String, Object> out = actions.book(3L, 11L);

    // then
    assertThat(out).containsEntry("displayName", "김○수").doesNotContainKeys("name", "school");
  }
}
