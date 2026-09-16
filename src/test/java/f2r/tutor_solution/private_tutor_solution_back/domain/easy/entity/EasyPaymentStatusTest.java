package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 수납 상태 자동 계산 — 미납/일부납/완납. */
@DisplayName("EasyPaymentStatus 자동 계산")
class EasyPaymentStatusTest {

  @Test
  @DisplayName("납부 0원 → 미납")
  void unpaid() {
    assertThat(EasyPaymentStatus.of(300000L, 0L)).isEqualTo(EasyPaymentStatus.UNPAID);
  }

  @Test
  @DisplayName("일부 납부 → 일부납")
  void partial() {
    assertThat(EasyPaymentStatus.of(300000L, 100000L)).isEqualTo(EasyPaymentStatus.PARTIAL);
  }

  @Test
  @DisplayName("전액 납부 → 완납, 초과 납부도 완납")
  void paid() {
    assertThat(EasyPaymentStatus.of(300000L, 300000L)).isEqualTo(EasyPaymentStatus.PAID);
    assertThat(EasyPaymentStatus.of(300000L, 500000L)).isEqualTo(EasyPaymentStatus.PAID);
  }
}
