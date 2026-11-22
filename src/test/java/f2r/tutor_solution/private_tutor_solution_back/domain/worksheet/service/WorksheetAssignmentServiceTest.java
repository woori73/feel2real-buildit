package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClassMember;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyClassMemberRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyClassRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity.Worksheet;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity.WorksheetAssignment;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.repository.WorksheetAssignmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetAssignmentService.AssignRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetAssignmentService.Panel;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("WorksheetAssignmentService — 학생·반 배정과 배정 상태")
class WorksheetAssignmentServiceTest {

  static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 22, 10, 0);

  WorksheetService worksheets;
  WorksheetAssignmentRepository assignments;
  EasyStudentRepository students;
  EasyClassRepository classes;
  EasyClassMemberRepository members;
  WorksheetAssignmentService service;
  Worksheet worksheet;
  Map<Long, EasyStudent> studentById = new HashMap<>();

  @BeforeEach
  void setUp() {
    worksheets = mock(WorksheetService.class);
    assignments = mock(WorksheetAssignmentRepository.class);
    students = mock(EasyStudentRepository.class);
    classes = mock(EasyClassRepository.class);
    members = mock(EasyClassMemberRepository.class);
    service = new WorksheetAssignmentService(worksheets, assignments, students, classes, members);
    worksheet = new Worksheet(3L, 10L, "선생님", "중2 어법", null, Worksheet.Status.ACTIVE, 5, NOW);
    ReflectionTestUtils.setField(worksheet, "worksheetId", 100L);
    when(worksheets.find(3L, 100L)).thenReturn(worksheet);
    student(11L, "가", EasyStudentStatus.ENROLLED);
    student(12L, "나", EasyStudentStatus.ENROLLED);
    student(13L, "다", EasyStudentStatus.WITHDRAWN);
    when(students.findAllById(any())).thenAnswer(i -> {
      List<EasyStudent> out = new ArrayList<>();
      for (Long id : i.<Iterable<Long>>getArgument(0)) if (studentById.containsKey(id)) out.add(studentById.get(id));
      return out;
    });
    when(assignments.findByWorksheetIdAndStudentId(any(), any())).thenReturn(Optional.empty());
  }

  private void student(long id, String name, EasyStudentStatus status) {
    EasyStudent s = EasyStudent.builder().name(name).school("예시중").status(status).build();
    ReflectionTestUtils.setField(s, "studentId", id);
    studentById.put(id, s);
  }

  private EasyClass classOf(long classId, long businessId, long... memberIds) {
    EasyBusiness biz = EasyBusiness.builder().code("A").name("학원").active(true).build();
    ReflectionTestUtils.setField(biz, "businessId", businessId);
    EasyClass c = EasyClass.builder().business(biz).name("중2 내신반").build();
    ReflectionTestUtils.setField(c, "classId", classId);
    List<EasyClassMember> list = new ArrayList<>();
    for (long id : memberIds) list.add(new EasyClassMember(c, studentById.get(id), LocalDate.of(2026, 11, 1)));
    when(classes.findById(classId)).thenReturn(Optional.of(c));
    when(members.findAllByEasyClass_ClassIdAndReleasedOnIsNull(classId)).thenReturn(list);
    return c;
  }

  @Test
  @DisplayName("given 재원생 2명 when assign then 학생마다 시작 전 배정 한 줄씩 만든다")
  void assign_createsRowPerStudent() {
    // when
    int changed = service.assign(3L, 100L, new AssignRequest(List.of(11L, 12L, 11L), null), 1L);

    // then
    assertThat(changed).isEqualTo(2);
    verify(assignments, times(2)).save(any(WorksheetAssignment.class));
  }

  @Test
  @DisplayName("given 반 when assign then 지금 그 반에 있는 학생 모두에게 배정하고 반을 적는다")
  void assign_byClass() {
    // given
    classOf(5L, 3L, 11L, 12L);
    List<WorksheetAssignment> saved = new ArrayList<>();
    when(assignments.save(any())).thenAnswer(i -> {
      saved.add(i.getArgument(0));
      return i.getArgument(0);
    });

    // when
    int changed = service.assign(3L, 100L, new AssignRequest(null, 5L), 1L);

    // then
    assertThat(changed).isEqualTo(2);
    assertThat(saved).extracting(WorksheetAssignment::getClassId).containsExactly(5L, 5L);
    assertThat(saved).extracting(WorksheetAssignment::getStatus).containsOnly(WorksheetAssignment.Status.NOT_STARTED);
  }

  @Test
  @DisplayName("given 이미 배정된 학생·뺐던 학생 when assign then 배정된 학생은 그대로, 뺐던 학생은 시작 전으로 다시 배정")
  void assign_skipsActiveAndRevivesRemoved() {
    // given
    WorksheetAssignment active = new WorksheetAssignment(100L, 3L, 11L, null, 1L, NOW);
    active.changeStatus(WorksheetAssignment.Status.DONE, NOW);
    WorksheetAssignment removed = new WorksheetAssignment(100L, 3L, 12L, null, 1L, NOW);
    removed.changeStatus(WorksheetAssignment.Status.DONE, NOW);
    removed.remove(NOW);
    when(assignments.findByWorksheetIdAndStudentId(100L, 11L)).thenReturn(Optional.of(active));
    when(assignments.findByWorksheetIdAndStudentId(100L, 12L)).thenReturn(Optional.of(removed));

    // when
    int changed = service.assign(3L, 100L, new AssignRequest(List.of(11L, 12L), null), 1L);

    // then
    assertThat(changed).isEqualTo(1);
    assertThat(active.getStatus()).isEqualTo(WorksheetAssignment.Status.DONE);
    assertThat(removed.isActive()).isTrue();
    assertThat(removed.getStatus()).isEqualTo(WorksheetAssignment.Status.NOT_STARTED);
    verify(assignments, never()).save(any());
  }

  @Test
  @DisplayName("given 아무도 안 고름·퇴원생·없는 학생·다른 사업체 반·휴지통 학습지 when assign then 거절")
  void assign_rejectsInvalid() {
    // given
    classOf(6L, 4L, 11L);

    // when & then
    assertThatThrownBy(() -> service.assign(3L, 100L, new AssignRequest(List.of(), null), 1L))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("골라");
    assertThatThrownBy(() -> service.assign(3L, 100L, new AssignRequest(List.of(13L), null), 1L))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("재원");
    assertThatThrownBy(() -> service.assign(3L, 100L, new AssignRequest(List.of(99L), null), 1L))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("재원");
    assertThatThrownBy(() -> service.assign(3L, 100L, new AssignRequest(null, 6L), 1L))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("반");
    worksheet.moveToTrash(10L, NOW);
    assertThatThrownBy(() -> service.assign(3L, 100L, new AssignRequest(List.of(11L), null), 1L))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("휴지통");
    verify(assignments, never()).save(any());
  }

  @Test
  @DisplayName("given 배정 when 상태 바꾸기·빼기 then 상태가 바뀌고 줄은 남긴 채 빠진다. 다른 사업체·잘못된 상태는 거절")
  void changeStatusAndRemove() {
    // given
    WorksheetAssignment a = new WorksheetAssignment(100L, 3L, 11L, null, 1L, NOW);
    when(assignments.findById(7L)).thenReturn(Optional.of(a));

    // when & then
    service.changeStatus(3L, 7L, "IN_PROGRESS");
    assertThat(a.getStatus()).isEqualTo(WorksheetAssignment.Status.IN_PROGRESS);
    assertThat(a.getStatusChangedAt()).isNotNull();
    assertThatThrownBy(() -> service.changeStatus(3L, 7L, "FINISHED")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.changeStatus(4L, 7L, "DONE")).isInstanceOf(IllegalArgumentException.class);
    service.remove(3L, 7L);
    assertThat(a.isActive()).isFalse();
    assertThatThrownBy(() -> service.remove(3L, 7L)).isInstanceOf(IllegalArgumentException.class);
    verify(assignments, never()).delete(any());
  }

  @Test
  @DisplayName("given 한 명 배정됨 when panel then 배정된 학생, 아직 배정 안 한 재원생, 진행 중인 반을 준다")
  void panel_listsAssignedAndOptions() {
    // given
    EasyClass c = classOf(5L, 3L, 11L, 12L);
    when(classes.findAllByBusiness_BusinessIdOrderByStatusAscNameAsc(3L)).thenReturn(List.of(c));
    WorksheetAssignment a = new WorksheetAssignment(100L, 3L, 11L, 5L, 1L, NOW);
    WorksheetAssignment gone = new WorksheetAssignment(100L, 3L, 12L, null, 1L, NOW);
    gone.remove(NOW);
    when(assignments.findAllByWorksheetIdOrderByAssignmentIdAsc(100L)).thenReturn(List.of(a, gone));
    when(students.findAllByStatus(EasyStudentStatus.ENROLLED)).thenReturn(List.of(studentById.get(11L), studentById.get(12L)));

    // when
    Panel panel = service.panel(3L, 100L);

    // then
    assertThat(panel.assigned()).hasSize(1);
    assertThat(panel.assigned().get(0).studentName()).isEqualTo("가");
    assertThat(panel.assigned().get(0).className()).isEqualTo("중2 내신반");
    assertThat(panel.students()).extracting(WorksheetAssignmentService.StudentOption::studentId).containsExactly(12L);
    assertThat(panel.classes().get(0).studentCount()).isEqualTo(2);
  }
}
