package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.ShinhanCardNormalizedTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.ShinhanCardRawRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShinhanCardNormalizerTest {
  private final ShinhanCardNormalizer normalizer = new ShinhanCardNormalizer();

  private ShinhanCardRawRow row(String merchant, long amount, int sheetRow) {
    return new ShinhanCardRawRow(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21), "본인",
        "본인111*", "일시불", merchant, BigDecimal.valueOf(amount), BigDecimal.valueOf(amount),
        BigDecimal.ZERO, BigDecimal.ZERO, "104-86-30469", sheetRow);
  }

  @Test
  void preservesSignedAmountAsLong() {
    List<ShinhanCardNormalizedTransaction> out =
        normalizer.normalize(List.of(row("가맹점", -34500, 5)), 1L);
    assertThat(out.get(0).amount()).isEqualTo(-34500L);
    assertThat(out.get(0).isNegative()).isTrue();
  }

  @Test
  void assignsIncreasingOccurrenceIndexToGenuinelyRepeatedIdenticalRows() {
    // Real source has exactly this shape: four identical 교원라이프 59,900원 charges same day.
    List<ShinhanCardRawRow> raw = List.of(
        row("교원라이프", 59900, 11), row("교원라이프", 59900, 12),
        row("교원라이프", 59900, 13), row("교원라이프", 59900, 14));
    List<ShinhanCardNormalizedTransaction> out = normalizer.normalize(raw, 1L);

    assertThat(out).extracting(ShinhanCardNormalizedTransaction::occurrenceIndex)
        .containsExactly(0, 1, 2, 3);
  }

  @Test
  void differentMerchantsSameDateGetIndependentOccurrenceIndex() {
    List<ShinhanCardRawRow> raw = List.of(row("가맹점A", 10000, 5), row("가맹점B", 10000, 6));
    List<ShinhanCardNormalizedTransaction> out = normalizer.normalize(raw, 1L);
    assertThat(out).extracting(ShinhanCardNormalizedTransaction::occurrenceIndex)
        .containsExactly(0, 0);
  }

  @Test
  void carriesBusinessIdThroughAsProvidedConfiguration() {
    List<ShinhanCardNormalizedTransaction> out =
        normalizer.normalize(List.of(row("가맹점", 10000, 5)), 42L);
    assertThat(out.get(0).businessId()).isEqualTo(42L);
  }
}
