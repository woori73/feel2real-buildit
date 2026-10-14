package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.KbCardRawRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.KbCardExcelTestFixtures.Row14;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.Test;

class KbCardExcelParserTest {
  private final KbCardExcelParser parser = new KbCardExcelParser();

  private Row14 row(String date, String merchant, double domestic, double foreign,
                    String slipStatus, String payDate, String approvalNo) {
    return new Row14(date, "18:17", "홍길*",
        "KB국민 테스트카드", merchant, domestic, foreign, "일시불", "", 0.0, 0.0,
        slipStatus, payDate, approvalNo);
  }

  @Test
  void parsesRealShape_domesticPurchasedAndUnpurchasedPlusForeign() {
    // given: the real file's shape — 전표매입 domestic, 전표미매입 domestic, one foreign row
    List<Row14> rows = List.of(
        row("2026-09-23", "테스트상점A", 5900, 0, "전표매입", "2026-10-10", "90000001"),
        row("2026-09-22", "테스트상점B", 20870, 0, "전표미매입", null, "90000002"),
        row("2026-09-21", "테스트상점C", 0, 19.81, "전표매입", "2026-10-10", "90000003"));

    // when
    List<KbCardRawRow> out = parser.parse(KbCardExcelTestFixtures.workbook(rows));

    // then
    assertThat(out).hasSize(3);
    assertThat(out.get(0).usageDate()).isEqualTo(LocalDate.of(2026, 9, 23));
    assertThat(out.get(0).merchantName()).isEqualTo("테스트상점A");
    assertThat(out.get(0).expectedPaymentDate()).isEqualTo(LocalDate.of(2026, 10, 10));
    assertThat(out.get(0).slipStatus()).isEqualTo("전표매입");
    assertThat(out.get(1).expectedPaymentDate()).isNull();
    assertThat(out.get(1).slipStatus()).isEqualTo("전표미매입");
    assertThat(out.get(2).foreignAmount().doubleValue()).isEqualTo(19.81);
  }

  @Test
  void rejectsUnknownSlipStatus_failClosed() {
    List<Row14> rows = List.of(
        row("2026-09-23", "테스트상점A", 5900, 0, "취소", "2026-10-10", "90000001"));

    assertThatThrownBy(() -> parser.parse(KbCardExcelTestFixtures.workbook(rows)))
        .isInstanceOf(KbCardExcelParser.KbCardParseException.class)
        .hasMessageContaining("미확인 상태값");
  }

  @Test
  void rejectsMalformedHeader_failClosed() {
    List<Row14> rows = List.of(
        row("2026-09-23", "테스트상점A", 5900, 0, "전표매입", "2026-10-10", "90000001"));
    String[] badHeaders = {"이용일", "이용시간", "이용고객명", "이용카드명",
        "이용하신곳", "국내이용금액(원)", "해외이용금액($)", "결제방법", "가맹점정보", "할인금액",
        "적립(예상)포인트리", "상태", "결제예정일", "승인번호X"};

    assertThatThrownBy(
        () -> parser.parse(KbCardExcelTestFixtures.workbookWithHeader(rows, badHeaders)))
        .isInstanceOf(KbCardExcelParser.KbCardParseException.class)
        .hasMessageContaining("예상과 다른 KB 스키마");
  }

  @Test
  void rejectsMissingHeaderRow() {
    try (HSSFWorkbook wb = new HSSFWorkbook()) {
      wb.createSheet("sheet 1").createRow(0).createCell(0).setCellValue("조회기간");
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      wb.write(out);
      assertThatThrownBy(
          () -> parser.parse(new ByteArrayInputStream(out.toByteArray())))
          .isInstanceOf(KbCardExcelParser.KbCardParseException.class)
          .hasMessageContaining("헤더 행");
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void emptyDataSectionParsesToEmptyList() {
    List<KbCardRawRow> out = parser.parse(KbCardExcelTestFixtures.workbook(List.of()));
    assertThat(out).isEmpty();
  }

  @Test
  void errorMessagesNeverLeakRawValues() {
    // given: sensitive-looking values in every validated field
    List<Row14> rows = List.of(
        row("2026-09-23", "민감상점명", 5900, 0, "알수없는상태", "2026-10-10", "87654321"));

    // when
    Throwable thrown = null;
    try {
      parser.parse(KbCardExcelTestFixtures.workbook(rows));
    } catch (Throwable t) {
      thrown = t;
    }

    // then: no raw value in the message
    assertThat(thrown).isNotNull();
    assertThat(thrown.getMessage())
        .doesNotContain("민감상점명", "87654321", "홍길");
  }
}
