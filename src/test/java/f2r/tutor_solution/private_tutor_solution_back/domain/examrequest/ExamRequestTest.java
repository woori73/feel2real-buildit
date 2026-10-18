package f2r.tutor_solution.private_tutor_solution_back.domain.examrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.entity.ExamRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.entity.ExamRequestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExamRequestTest {

  @Test
  @DisplayName("given 정상 입력 when create then 접수 상태·40문항·2만원으로 만들고 공백을 다듬는다")
  void createsReceivedRequest() {
    // given / when
    ExamRequest r = ExamRequest.create(7L, "  신사중  ", "중3", "2학기 중간", "Unit 1~3", "010-0000-0000", "  ");
    // then
    assertThat(r.getSchoolName()).isEqualTo("신사중");
    assertThat(r.getStatus()).isEqualTo(ExamRequestStatus.RECEIVED);
    assertThat(r.getQuestionCount()).isEqualTo(40);
    assertThat(r.getPriceKrw()).isEqualTo(20_000);
    assertThat(r.getMemo()).isNull();
  }

  @Test
  @DisplayName("given 학교명 공백 when create then 거부한다")
  void rejectsBlankSchool() {
    assertThatThrownBy(() -> ExamRequest.create(7L, " ", "중3", "중간", "범위", "연락처", null))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("학교명");
  }

  @Test
  @DisplayName("given 시험 범위가 1000자 초과 when create then 거부한다")
  void rejectsTooLongScope() {
    String scope = "가".repeat(ExamRequest.SCOPE_MAX + 1);
    assertThatThrownBy(() -> ExamRequest.create(7L, "학교", "중3", "중간", scope, "연락처", null))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("시험 범위");
  }

  @Test
  @DisplayName("given 사용자 없음 when create then 거부한다")
  void rejectsMissingUser() {
    assertThatThrownBy(() -> ExamRequest.create(null, "학교", "중3", "중간", "범위", "연락처", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 접수 건 when changeStatus then 상태가 바뀌고 null은 거부한다")
  void changesStatus() {
    ExamRequest r = ExamRequest.create(7L, "학교", "중3", "중간", "범위", "연락처", null);
    r.changeStatus(ExamRequestStatus.IN_PROGRESS);
    assertThat(r.getStatus()).isEqualTo(ExamRequestStatus.IN_PROGRESS);
    assertThatThrownBy(() -> r.changeStatus(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
