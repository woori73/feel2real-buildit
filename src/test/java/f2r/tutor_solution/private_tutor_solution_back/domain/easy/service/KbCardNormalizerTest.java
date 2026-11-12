package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.KbCardNormalizedTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.KbCardRawRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class KbCardNormalizerTest {
  private final KbCardNormalizer normalizer = new KbCardNormalizer();

  private KbCardRawRow row(String merchant, double domestic, double foreign,
                           String slipStatus, LocalDate payDate, String approvalNo, int sheetRow) {
    return new KbCardRawRow(LocalDate.of(2026, 9, 21), "18:17", "홍길*",
        "KB국민 테스트카드", merchant, BigDecimal.valueOf(domestic), BigDecimal.valueOf(foreign),
        "일시불", "", BigDecimal.ZERO, BigDecimal.ZERO, slipStatus, payDate, approvalNo, sheetRow);
  }

  @Test
  void purchasedMapsExpectedPayDateToConfirmationDate() {
    List<KbCardNormalizedTransaction> out = normalizer.normalize(
        List.of(row("가맹점", 5900, 0, "전표매입", LocalDate.of(2026, 10, 10), "90000001", 7)), 1L);

    assertThat(out.get(0).confirmationDate()).isEqualTo(LocalDate.of(2026, 10, 10));
    assertThat(out.get(0).currency()).isEqualTo("KRW");
    assertThat(out.get(0).eventAmount()).isEqualTo(5900L);
    assertThat(out.get(0).isForeign()).isFalse();
  }

  @Test
  void unpurchasedMapsToNullConfirmationDate() {
    // 전표미매입 is NOT a cancellation — just no confirmation yet. Never over-inferred.
    List<KbCardNormalizedTransaction> out = normalizer.normalize(
        List.of(row("가맹점", 5900, 0, "전표미매입", null, "90000002", 7)), 1L);

    assertThat(out.get(0).confirmationDate()).isNull();
    assertThat(out.get(0).slipStatus()).isEqualTo("전표미매입");
    assertThat(out.get(0).isNegative()).isFalse();
  }

  @Test
  void purchasedWithoutPayDateFailsClosed() {
    assertThatThrownBy(() -> normalizer.normalize(
        List.of(row("가맹점", 5900, 0, "전표매입", null, "90000001", 7)), 1L))
        .isInstanceOf(KbCardExcelParser.KbCardParseException.class);
  }

  @Test
  void foreignRowKeepsUsdCentsWithoutFxInvention() {
    List<KbCardNormalizedTransaction> out = normalizer.normalize(
        List.of(row("가맹점", 0, 19.81, "전표매입", LocalDate.of(2026, 10, 10), "90000003", 7)), 1L);

    assertThat(out.get(0).currency()).isEqualTo("USD");
    assertThat(out.get(0).eventAmount()).isEqualTo(1981L);
    assertThat(out.get(0).isForeign()).isTrue();
  }

  @Test
  void assignsIncreasingOccurrenceIndexToRepeatedIdenticalRows() {
    List<KbCardRawRow> raw = List.of(
        row("동일가맹점", 5900, 0, "전표매입", LocalDate.of(2026, 10, 10), "90000011", 7),
        row("동일가맹점", 5900, 0, "전표매입", LocalDate.of(2026, 10, 10), "90000012", 8),
        row("동일가맹점", 5900, 0, "전표매입", LocalDate.of(2026, 10, 10), "90000013", 9));
    List<KbCardNormalizedTransaction> out = normalizer.normalize(raw, 1L);

    assertThat(out).extracting(KbCardNormalizedTransaction::occurrenceIndex)
        .containsExactly(0, 1, 2);
  }

  @Test
  void carriesBusinessIdThroughAsProvidedConfiguration() {
    List<KbCardNormalizedTransaction> out = normalizer.normalize(
        List.of(row("가맹점", 10000, 0, "전표매입", LocalDate.of(2026, 10, 10), "90000001", 7)), 42L);

    assertThat(out.get(0).businessId()).isEqualTo(42L);
  }
}
