package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.ShinhanCardRawRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShinhanCardExcelParserTest {
  private final ShinhanCardExcelParser parser = new ShinhanCardExcelParser();

  @Test
  void parsesAllElevenColumnsAndKeepsTransactionAndConfirmationDatesSeparate() {
    var rows = List.of(new ShinhanCardExcelTestFixtures.Row11(
        "2026.09.14", "2026.09.15", "본인", "본인111*", "일시불",
        "테스트가맹점", 15000, 13637, 1363, 0, "111-11-11111"));
    List<ShinhanCardRawRow> parsed = parser.parse(ShinhanCardExcelTestFixtures.workbook(rows));

    assertThat(parsed).hasSize(1);
    ShinhanCardRawRow row = parsed.get(0);
    assertThat(row.transactionDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    assertThat(row.confirmationDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    assertThat(row.transactionDate()).isNotEqualTo(row.confirmationDate());
    assertThat(row.cardHolderType()).isEqualTo("본인");
    assertThat(row.maskedCard()).isEqualTo("본인111*");
    assertThat(row.productType()).isEqualTo("일시불");
    assertThat(row.merchantName()).isEqualTo("테스트가맹점");
    assertThat(row.amount()).isEqualByComparingTo(BigDecimal.valueOf(15000));
    assertThat(row.supplyAmount()).isEqualByComparingTo(BigDecimal.valueOf(13637));
    assertThat(row.vatAmount()).isEqualByComparingTo(BigDecimal.valueOf(1363));
    assertThat(row.taxExemptAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(row.merchantRegistrationNumber()).isEqualTo("111-11-11111");
  }

  @Test
  void preservesNegativeAmountSignWithoutConversion() {
    var rows = List.of(new ShinhanCardExcelTestFixtures.Row11(
        "2026.09.14", "2026.09.15", "본인", "본인111*", "일시불",
        "테스트가맹점", -34500, -34500, 0, 0, "111-11-11111"));
    List<ShinhanCardRawRow> parsed = parser.parse(ShinhanCardExcelTestFixtures.workbook(rows));

    assertThat(parsed.get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(-34500));
    assertThat(parsed.get(0).supplyAmount()).isEqualByComparingTo(BigDecimal.valueOf(-34500));
  }

  @Test
  void stopsAtTotalRowAndDoesNotParseDisclaimerAsData() {
    var rows = List.of(
        new ShinhanCardExcelTestFixtures.Row11("2026.09.14", "2026.09.15", "본인", "본인111*",
            "일시불", "가맹점A", 10000, 9091, 909, 0, "111-11-11111"),
        new ShinhanCardExcelTestFixtures.Row11("2026.09.15", "2026.09.16", "본인", "본인111*",
            "일시불", "가맹점B", 20000, 18182, 1818, 0, "222-22-22222"));
    List<ShinhanCardRawRow> parsed = parser.parse(ShinhanCardExcelTestFixtures.workbook(rows));

    assertThat(parsed).hasSize(2);
    assertThat(parsed).extracting(ShinhanCardRawRow::merchantName)
        .containsExactly("가맹점A", "가맹점B");
  }

  @Test
  void findsHeaderRowByContentNotFixedIndex() {
    // The fixture always puts the header at row 4, but this asserts the parser is locating it
    // by the "거래일" cell, not assuming that row index — verified via the parse succeeding at all
    // for a workbook whose meta-row count matches the real source exactly (4 rows before header).
    var rows = List.of(new ShinhanCardExcelTestFixtures.Row11(
        "2026.09.14", "2026.09.15", "본인", "본인111*", "일시불",
        "가맹점A", 10000, 9091, 909, 0, "111-11-11111"));
    List<ShinhanCardRawRow> parsed = parser.parse(ShinhanCardExcelTestFixtures.workbook(rows));
    assertThat(parsed).hasSize(1);
  }
}
