package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * Builds synthetic BIFF8 (.xls) workbooks shaped like Shinhan's real "개인사업자 이용내역" download —
 * same row layout (4 meta rows, header row, data rows, 합계 row, disclaimer) and same 11 columns —
 * but with entirely fake merchants/amounts/registration numbers. No real source data anywhere in
 * this file or in Git.
 */
final class ShinhanCardExcelTestFixtures {
  private ShinhanCardExcelTestFixtures() {}

  record Row11(String txnDate, String confirmDate, String cardHolderType, String maskedCard,
               String productType, String merchant, double amount, double supply, double vat,
               double taxExempt, String bizRegNo) {}

  static InputStream workbook(List<Row11> dataRows) {
    try (HSSFWorkbook wb = new HSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet0");
      text(sheet, 0, 0, "개인사업자 이용내역");
      text(sheet, 1, 0, "성명 : 테*트");
      text(sheet, 2, 0, "카드번호 : -");
      text(sheet, 3, 0, "기간 : 2026.09.01 ~ 2026.09.30");
      String[] headers = {"거래일", "확정일", "카드구분", "이용카드\n(뒤4자리)", "상품구분",
          "가맹점명", "이용금액", "공급가액", "부가세", "비과세금액", "사업자등록번호"};
      Row headerRow = sheet.createRow(4);
      for (int c = 0; c < headers.length; c++) headerRow.createCell(c).setCellValue(headers[c]);

      int r = 5;
      long sum = 0;
      for (Row11 row : dataRows) {
        Row xr = sheet.createRow(r++);
        xr.createCell(0).setCellValue(row.txnDate());
        xr.createCell(1).setCellValue(row.confirmDate());
        xr.createCell(2).setCellValue(row.cardHolderType());
        xr.createCell(3).setCellValue(row.maskedCard());
        xr.createCell(4).setCellValue(row.productType());
        xr.createCell(5).setCellValue(row.merchant());
        xr.createCell(6).setCellValue(row.amount());
        xr.createCell(7).setCellValue(row.supply());
        xr.createCell(8).setCellValue(row.vat());
        xr.createCell(9).setCellValue(row.taxExempt());
        xr.createCell(10).setCellValue(row.bizRegNo());
        sum += (long) row.amount();
      }
      Row totalRow = sheet.createRow(r++);
      text(totalRow, 0, "합계");
      totalRow.createCell(1).setCellValue(dataRows.size() + " 건");
      totalRow.createCell(6).setCellValue(sum);

      Row disclaimer = sheet.createRow(r);
      text(disclaimer, 0, "위 확인서 상의 내용은 작성일자를 기준으로 전산상 반영 처리된 내용입니다.");

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      wb.write(out);
      return new ByteArrayInputStream(out.toByteArray());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void text(Sheet sheet, int r, int c, String value) {
    sheet.createRow(r).createCell(c).setCellValue(value);
  }

  private static void text(Row row, int c, String value) {
    row.createCell(c).setCellValue(value);
  }
}
