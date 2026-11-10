package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAttendanceEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceRosterImportService.ApplyRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceRosterImportService.Mapping;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceRosterImportService.NameRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.ClassForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudentRosterController.RosterLesson;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 진행표 → 출결부 가져오기. 하네스는 EasyAttendanceServiceTest를 재사용한다. 모든 이름은 가짜다. */
class EasyAttendanceRosterImportServiceTest extends EasyAttendanceServiceTest {

  static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(1L, "admin", "ADMIN");
  final List<RosterLesson> lessons = new ArrayList<>();
  EasyAttendanceRosterImportService importer;

  @BeforeEach
  void setUpImporter() {
    lessons.clear();
    lessons.add(new RosterLesson(LocalDate.of(2026, 3, 2), "가나", 1));
    lessons.add(new RosterLesson(LocalDate.of(2026, 3, 4), "가나", 2));
    lessons.add(new RosterLesson(LocalDate.of(2026, 3, 2), "다라", 1));
    lessons.add(new RosterLesson(LocalDate.of(2026, 3, 3), "마바", 1));
    importer = new EasyAttendanceRosterImportService((p, year) -> List.copyOf(lessons), studentRepository,
        eventRepository, classRepository, memberRepository, businessRepository, service, () -> LocalDate.of(2026, 11, 10));
  }

  @SuppressWarnings("unchecked")
  List<NameRow> names(Map<String, Object> preview) {
    return (List<NameRow>) preview.get("names");
  }

  @Test
  @DisplayName("given 학생 원장에 가나 1명·마바 2명 when 미리보기 then 가나=연결, 다라=새로 등록, 마바=직접 고르기로 제안하고 저장하지 않는다")
  void previewSuggestions() {
    // given
    student("가나");
    student("마바");
    student("마바");

    // when
    var preview = importer.preview(ADMIN, BIZ, 2026);

    // then
    var byName = names(preview).stream().collect(java.util.stream.Collectors.toMap(NameRow::name, n -> n));
    assertThat(byName.get("가나").suggestedAction()).isEqualTo("MATCH");
    assertThat(byName.get("가나").lessonDays()).isEqualTo(2);
    assertThat(byName.get("가나").sessions()).isEqualTo(3);
    assertThat(byName.get("다라").suggestedAction()).isEqualTo("CREATE");
    assertThat(byName.get("마바").suggestedAction()).isEqualTo("CHOOSE");
    assertThat(byName.get("마바").candidates()).hasSize(2);
    assertThat(preview.get("lessonDays")).isEqualTo(4);
    assertThat(events).isEmpty();
  }

  @Test
  @DisplayName("given 연결·새로 등록·건너뛰기 선택 when 적용 then 학생 등록과 등원 기록, 두 번 적용해도 중복되지 않는다")
  void applyCreatesMatchesSkipsAndIsIdempotent() {
    // given
    Long existing = student("가나").getStudentId();
    String hash = (String) importer.preview(ADMIN, BIZ, 2026).get("previewHash");
    var request = new ApplyRequest(BIZ, 2026, hash, null, List.of(
        new Mapping("가나", "MATCH", existing), new Mapping("다라", "CREATE", null), new Mapping("마바", "SKIP", null)));

    // when
    Map<String, Object> first = importer.apply(ADMIN, request);
    Map<String, Object> second = importer.apply(ADMIN, request);

    // then
    assertThat(first).containsEntry("matchedStudents", 1).containsEntry("skippedNames", 0 + 1)
        .containsEntry("eventsCreated", 3).containsEntry("eventsAlreadyThere", 0);
    assertThat(first.get("createdStudents")).isEqualTo(1);
    assertThat(students.values()).filteredOn(s -> s.getName().equals("다라")).singleElement()
        .satisfies(s -> assertThat(s.getMemo()).contains("2026 진행표"));
    assertThat(second).containsEntry("eventsCreated", 0).containsEntry("eventsAlreadyThere", 3)
        .containsEntry("createdStudents", 0);
    assertThat(events).hasSize(3).allSatisfy(e -> {
      assertThat(e.getEventType()).isEqualTo(EasyAttendanceEvent.Type.CHECK_IN);
      assertThat(e.getSource()).isEqualTo(EasyAttendanceEvent.Source.ROSTER_IMPORT);
      assertThat(e.getNote()).contains("시각 미상");
    });
    assertThat(events).filteredOn(e -> e.getOccurredOn().equals(LocalDate.of(2026, 3, 4))).singleElement()
        .satisfies(e -> assertThat(e.getNote()).contains("수업 2건"));
  }

  @Test
  @DisplayName("given 반을 고르고 적용 when 넣기 then 학생을 첫 수업일로 반에 배정하고 반 시작 시각으로 기록한다")
  void applyWithClassAssignsAndUsesStartTime() {
    // given
    Long classId = service.createClass(BIZ, new ClassForm("가져온 반", null, null, null, "15:00", "16:00", null, null)).classId();
    String hash = (String) importer.preview(ADMIN, BIZ, 2026).get("previewHash");

    // when
    importer.apply(ADMIN, new ApplyRequest(BIZ, 2026, hash, classId, List.of(
        new Mapping("가나", "CREATE", null), new Mapping("다라", "SKIP", null), new Mapping("마바", "SKIP", null))));

    // then
    assertThat(service.members(BIZ, classId)).singleElement()
        .satisfies(m -> assertThat(m.assignedOn()).isEqualTo(LocalDate.of(2026, 3, 2)));
    assertThat(events).hasSize(2).allSatisfy(e -> {
      assertThat(e.getOccurredAt().toLocalTime()).hasToString("15:00");
      assertThat(e.getNote()).doesNotContain("시각 미상");
    });
    assertThat(service.sheet(BIZ, classId, LocalDate.of(2026, 3, 4)).get(0).events()).hasSize(1);
  }

  @Test
  @DisplayName("given 미리보기 뒤 명단이 바뀜 / 처리 방법을 안 고른 이름 when 적용 then 거절하고 아무것도 저장하지 않는다")
  void applyRejectsStaleOrIncomplete() {
    // given
    String hash = (String) importer.preview(ADMIN, BIZ, 2026).get("previewHash");
    var partial = new ApplyRequest(BIZ, 2026, hash, null, List.of(new Mapping("가나", "CREATE", null)));

    // when / then
    assertThatThrownBy(() -> importer.apply(ADMIN, partial)).hasMessageContaining("모든 이름");
    lessons.add(new RosterLesson(LocalDate.of(2026, 3, 9), "사아", 1));
    var stale = new ApplyRequest(BIZ, 2026, hash, null, List.of(
        new Mapping("가나", "SKIP", null), new Mapping("다라", "SKIP", null), new Mapping("마바", "SKIP", null),
        new Mapping("사아", "SKIP", null)));
    assertThatThrownBy(() -> importer.apply(ADMIN, stale)).hasMessageContaining("다시");
    assertThat(events).isEmpty();
    assertThat(students).isEmpty();
  }

  @Test
  @DisplayName("given 오늘 이후 날짜(예정 수업) when 미리보기·적용 then 세지 않고 넣지 않는다")
  void futureLessonsAreExcluded() {
    // given
    lessons.add(new RosterLesson(LocalDate.of(2026, 12, 1), "가나", 1));
    var preview = importer.preview(ADMIN, BIZ, 2026);

    // when
    importer.apply(ADMIN, new ApplyRequest(BIZ, 2026, (String) preview.get("previewHash"), null, List.of(
        new Mapping("가나", "CREATE", null), new Mapping("다라", "SKIP", null), new Mapping("마바", "SKIP", null))));

    // then
    assertThat(preview.get("futureLessonDays")).isEqualTo(1);
    assertThat(preview.get("lessonDays")).isEqualTo(4);
    assertThat(events).hasSize(2).noneMatch(e -> e.getOccurredOn().equals(LocalDate.of(2026, 12, 1)));
  }

  @Test
  @DisplayName("given 같은 입력 when 해시 then 같고, 내용이 다르면 다르다")
  void hashIsStable() {
    // given / when / then
    assertThat(EasyAttendanceRosterImportService.hash(List.copyOf(lessons)))
        .isEqualTo(EasyAttendanceRosterImportService.hash(List.copyOf(lessons)));
    var changed = new ArrayList<>(lessons);
    changed.set(0, new RosterLesson(LocalDate.of(2026, 3, 2), "가나", 3));
    assertThat(EasyAttendanceRosterImportService.hash(changed))
        .isNotEqualTo(EasyAttendanceRosterImportService.hash(List.copyOf(lessons)));
    assertThat(EasyAttendanceRosterImportService.sourceKey(1L, 2026, 7L, LocalDate.of(2026, 3, 2)))
        .isEqualTo("ROSTER:1:2026:7:2026-03-02");
  }
}
