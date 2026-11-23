package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service;

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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습지를 학생·반에 배정하고 배정 상태(시작 전·진행 중·완료)를 바꾼다.
 * 학생 이름이 나오므로 학생부와 같이 관리자(ADMIN) 전용 API에서만 부른다.
 */
@Service
@Transactional
public class WorksheetAssignmentService {

  private final WorksheetService worksheets;
  private final WorksheetAssignmentRepository assignments;
  private final EasyStudentRepository students;
  private final EasyClassRepository classes;
  private final EasyClassMemberRepository members;

  public WorksheetAssignmentService(WorksheetService worksheets, WorksheetAssignmentRepository assignments,
      EasyStudentRepository students, EasyClassRepository classes, EasyClassMemberRepository members) {
    this.worksheets = worksheets;
    this.assignments = assignments;
    this.students = students;
    this.classes = classes;
    this.members = members;
  }

  public record AssignRequest(List<Long> studentIds, Long classId) {}

  public record AssignedRow(Long assignmentId, Long studentId, String studentName, String school, Integer grade,
      String className, String status, String assignedAt) {}

  public record StudentOption(Long studentId, String name, String school, Integer grade) {}

  public record ClassOption(Long classId, String name, int studentCount) {}

  /** 배정 화면 한 벌: 배정된 학생, 아직 배정하지 않은 재원생, 진행 중인 반. */
  public record Panel(Long worksheetId, String title, List<AssignedRow> assigned, List<StudentOption> students,
      List<ClassOption> classes) {}

  @Transactional(readOnly = true)
  public Panel panel(Long businessId, Long worksheetId) {
    Worksheet w = worksheets.find(businessId, worksheetId);
    List<WorksheetAssignment> active = assignments.findAllByWorksheetIdOrderByAssignmentIdAsc(worksheetId).stream()
        .filter(WorksheetAssignment::isActive).toList();
    Map<Long, EasyStudent> byId = students.findAllById(active.stream().map(WorksheetAssignment::getStudentId).toList())
        .stream().collect(Collectors.toMap(EasyStudent::getStudentId, s -> s));
    List<EasyClass> activeClasses = classes.findAllByBusiness_BusinessIdOrderByStatusAscNameAsc(businessId).stream()
        .filter(c -> c.getStatus() == EasyClass.Status.ACTIVE).toList();
    Map<Long, String> classNames = activeClasses.stream().collect(Collectors.toMap(EasyClass::getClassId, EasyClass::getName));
    Set<Long> assignedIds = byId.keySet();
    List<AssignedRow> rows = active.stream().map(a -> {
      EasyStudent s = byId.get(a.getStudentId());
      return new AssignedRow(a.getAssignmentId(), a.getStudentId(), s == null ? "" : s.getName(),
          s == null ? "" : s.getSchool(), s == null ? null : grade(s),
          a.getClassId() == null ? null : classNames.get(a.getClassId()), a.getStatus().name(),
          String.valueOf(a.getAssignedAt()));
    }).toList();
    List<StudentOption> options = students.findAllByStatus(EasyStudentStatus.ENROLLED).stream()
        .filter(s -> !assignedIds.contains(s.getStudentId()))
        .sorted((a, b) -> String.valueOf(a.getName()).compareTo(String.valueOf(b.getName())))
        .map(s -> new StudentOption(s.getStudentId(), s.getName(), s.getSchool(), grade(s))).toList();
    List<ClassOption> classOptions = activeClasses.stream().map(c -> new ClassOption(c.getClassId(), c.getName(),
        members.findAllByEasyClass_ClassIdAndReleasedOnIsNull(c.getClassId()).size())).toList();
    return new Panel(worksheetId, w.getTitle(), rows, options, classOptions);
  }

  /**
   * 학생 번호와 반(그 반에 지금 있는 학생)을 합쳐 배정한다. 이미 배정된 학생은 그대로 두고,
   * 뺐던 학생은 시작 전으로 다시 배정한다. 새로 배정하거나 다시 배정한 학생 수를 돌려준다.
   */
  public int assign(Long businessId, Long worksheetId, AssignRequest req, Long by) {
    Worksheet w = worksheets.find(businessId, worksheetId);
    if (w.isDeleted()) throw new IllegalArgumentException("휴지통에 있는 학습지는 배정할 수 없습니다.");
    Set<Long> targets = new LinkedHashSet<>(req == null || req.studentIds() == null ? List.of() : req.studentIds());
    Long classId = req == null ? null : req.classId();
    if (classId != null) {
      EasyClass c = classes.findById(classId)
          .filter(x -> x.getBusiness().getBusinessId().equals(businessId) && x.getStatus() == EasyClass.Status.ACTIVE)
          .orElseThrow(() -> new IllegalArgumentException("반을 찾을 수 없습니다."));
      for (EasyClassMember m : members.findAllByEasyClass_ClassIdAndReleasedOnIsNull(c.getClassId())) {
        targets.add(m.getStudent().getStudentId());
      }
    }
    if (targets.isEmpty()) throw new IllegalArgumentException("배정할 학생이나 반을 골라 주세요.");
    Set<Long> classMemberIds = classId == null ? Set.of()
        : members.findAllByEasyClass_ClassIdAndReleasedOnIsNull(classId).stream()
            .map(m -> m.getStudent().getStudentId()).collect(Collectors.toSet());
    List<EasyStudent> found = students.findAllById(new ArrayList<>(targets));
    if (found.size() != targets.size() || found.stream().anyMatch(s -> s.getStatus() != EasyStudentStatus.ENROLLED)) {
      throw new IllegalArgumentException("재원 중인 학생만 배정할 수 있습니다.");
    }
    LocalDateTime now = LocalDateTime.now();
    int changed = 0;
    for (Long studentId : targets) {
      Long viaClass = classMemberIds.contains(studentId) ? classId : null;
      var existing = assignments.findByWorksheetIdAndStudentId(worksheetId, studentId);
      if (existing.isEmpty()) {
        assignments.save(new WorksheetAssignment(worksheetId, businessId, studentId, viaClass, by, now));
        changed++;
      } else if (!existing.get().isActive()) {
        existing.get().reassign(viaClass, by, now);
        changed++;
      }
    }
    return changed;
  }

  public void changeStatus(Long businessId, Long assignmentId, String status) {
    WorksheetAssignment.Status next;
    try {
      next = WorksheetAssignment.Status.valueOf(String.valueOf(status));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("배정 상태를 확인해 주세요.");
    }
    active(businessId, assignmentId).changeStatus(next, LocalDateTime.now());
  }

  /** 배정에서 뺀다. 줄은 남기고 removedAt만 채운다. */
  public void remove(Long businessId, Long assignmentId) {
    active(businessId, assignmentId).remove(LocalDateTime.now());
  }

  private WorksheetAssignment active(Long businessId, Long assignmentId) {
    return assignments.findById(assignmentId)
        .filter(a -> a.getBusinessId().equals(businessId) && a.isActive())
        .orElseThrow(() -> new IllegalArgumentException("배정을 찾을 수 없습니다."));
  }

  private static Integer grade(EasyStudent s) {
    return s.getGrade() == null ? null : s.getGrade().intValue();
  }
}
