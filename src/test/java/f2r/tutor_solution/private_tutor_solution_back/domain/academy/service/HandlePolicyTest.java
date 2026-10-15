package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandlePolicyTest {

  @ParameterizedTest
  @ValueSource(strings = {"minji-eng", "abc", "a1b2c3", "english-master-2024"})
  void validate_passesForWellFormedHandle(String handle) {
    // given / when / then: 예외 없이 통과.
    HandlePolicy.validate(handle);
    assertThat(HandlePolicy.isAcceptable(handle)).isTrue();
  }

  @Test
  void validate_rejectsUppercase() {
    // given: 대문자 포함.
    String handle = "MinjiEng";

    // when & then
    assertThatThrownBy(() -> HandlePolicy.validate(handle))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("handle");
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "ab", "-abc", "abc-", "min_ji", "hi there"})
  void validate_rejectsMalformed(String handle) {
    // given: 너무 짧거나 앞뒤 하이픈/허용 외 문자.
    // when & then
    assertThatThrownBy(() -> HandlePolicy.validate(handle))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"www", "api", "admin", "test", "app", "lms"})
  void validate_rejectsReservedWord(String handle) {
    // given: 예약어.
    // when & then
    assertThatThrownBy(() -> HandlePolicy.validate(handle))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("예약");
    assertThat(HandlePolicy.isAcceptable(handle)).isFalse();
  }

  @Test
  void validate_rejectsBlank() {
    // given
    String handle = "  ";

    // when & then
    assertThatThrownBy(() -> HandlePolicy.validate(handle))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("필수");
  }

  @Test
  void validate_rejectsTooLong() {
    // given: 31자(상한 30 초과).
    String handle = "a".repeat(31);

    // when & then
    assertThatThrownBy(() -> HandlePolicy.validate(handle))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
