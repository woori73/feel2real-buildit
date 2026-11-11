package f2r.tutor_solution.private_tutor_solution_back.domain.academyhome;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 학생명단 ↔ 학원 학생 대조. 이름은 테스트용 가상 이름이다. */
class RosterReconcilerTest {
  static RosterReconciler.Student s(long id, String name, boolean active) { return new RosterReconciler.Student(id, name, "", "", active); }

  @Test
  @DisplayName("given 여러 날짜에 같은 이름 when 대조하면 then 한 행이고 추가·기존·보류·제외 합이 원본 이름 수와 같다")
  void collapsesDuplicatesAndBalances() {
    // given
    var entries = List.of("가상가", "가상가", "가상나", "가상다", "가상라", "가상라", "가상마");
    var students = List.of(s(1, "가상나", true), s(2, "가상다", true), s(3, "가상다", true), s(4, "가상라", true), s(5, "가상마", false));
    var links = List.of(new RosterReconciler.Link("가상라", AcademyRosterStudentLink.LINKED, 4L));
    // when
    var r = RosterReconciler.reconcile(entries, students, links);
    // then
    assertThat(r.sourceEntries()).isEqualTo(7);
    assertThat(r.sourceNames()).isEqualTo(5);
    assertThat(r.rows()).extracting(RosterReconciler.Row::status).containsExactly("NEW", "HOLD", "HOLD", "EXISTING", "HOLD");
    assertThat(r.rows().get(0).occurrences()).isEqualTo(2);
    assertThat(r.rows().get(2).candidates()).hasSize(2);
    assertThat(r.balanced()).isTrue();
    assertThat(r.existing() + r.added() + r.hold() + r.excluded()).isEqualTo(5);
  }

  @Test
  @DisplayName("given 이름만 같은 학생 when 연결 확인이 없으면 then 자동으로 기존 학생에 합치지 않는다")
  void nameOnlyMatchIsNeverAutoMerged() {
    var r = RosterReconciler.reconcile(List.of("가상나"), List.of(s(1, "가상나", true)), List.of());
    assertThat(r.rows().get(0).status()).isEqualTo("HOLD");
    assertThat(r.existing()).isZero();
  }

  @Test
  @DisplayName("given 이미 등록한 학생 when 다시 대조(재처리)하면 then 추가 후보로 다시 세지 않아 원생 수가 부풀지 않는다")
  void reprocessingDoesNotInflate() {
    // given: 첫 대조에서 NEW였던 이름이 승인 후 학생으로 등록됨
    var first = RosterReconciler.reconcile(List.of("가상가"), List.of(), List.of());
    var second = RosterReconciler.reconcile(List.of("가상가"), List.of(s(9, "가상가", true)), List.of());
    // then
    assertThat(first.added()).isEqualTo(1);
    assertThat(second.added()).isZero();
    assertThat(second.hold()).isEqualTo(1);
  }

  @Test
  @DisplayName("given 제외 확인·이름 형식 아님·연결 학생 비활성 when 대조하면 then 제외 또는 보류로 남긴다")
  void excludedAndStaleLinks() {
    var r = RosterReconciler.reconcile(List.of("가상가", "123", "가상나"), List.of(s(2, "가상나", false)),
        List.of(new RosterReconciler.Link("가상가", AcademyRosterStudentLink.EXCLUDED, null), new RosterReconciler.Link("가상나", AcademyRosterStudentLink.LINKED, 2L)));
    assertThat(r.rows()).extracting(RosterReconciler.Row::status).containsExactly("EXCLUDED", "EXCLUDED", "HOLD");
    assertThat(r.rows().get(2).reason()).contains("비활성");
    assertThat(r.balanced()).isTrue();
  }

  @Test
  @DisplayName("given 빈 명단 when 대조하면 then 모든 수가 0이고 균형이 맞는다")
  void emptyRoster() {
    var r = RosterReconciler.reconcile(List.of(), List.of(s(1, "가상", true)), List.of());
    assertThat(r.sourceNames()).isZero();
    assertThat(r.balanced()).isTrue();
  }
}
