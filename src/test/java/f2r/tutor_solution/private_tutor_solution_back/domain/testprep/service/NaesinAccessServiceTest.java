package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NaesinAccessServiceTest {

  @Test
  @DisplayName("given 시험 코드, when isOpenRound면 1회(-R01)만 누구나 풀 수 있는 회차다")
  void openRoundIsOnlyR01() {
    assertThat(NaesinAccessService.isOpenRound("TP-NAESIN-APM2-2602-R01")).isTrue();
    assertThat(NaesinAccessService.isOpenRound("TP-NAESIN-APM2-2602-R02")).isFalse();
    assertThat(NaesinAccessService.isOpenRound("TP-NAESIN-APM2-2602-R11")).isFalse();
    assertThat(NaesinAccessService.isOpenRound(null)).isFalse();
  }

  @Test
  @DisplayName("given 여러 표기의 휴대전화, when digits면 숫자만 남기고 +82를 0으로 바꾼다")
  void digitsNormalizesPhone() {
    assertThat(NaesinAccessService.digits("010-1234-5678")).isEqualTo("01012345678");
    assertThat(NaesinAccessService.digits("+82 10-1234-5678")).isEqualTo("01012345678");
    assertThat(NaesinAccessService.digits(null)).isEmpty();
    assertThat(NaesinAccessService.trim(" 김 민준 ")).isEqualTo("김민준");
  }
}
