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
 * Builds synthetic BIFF8 (.xls) workbooks shaped like KB's real "개인사업자용 이용내역" download —
 * same summary rows, same 14 columns — but with entirely fake customers/merchants/amounts/approval
 * numbers. No real source data anywhere in this file or in Git.
 */
final class KbCardExcelTestFixtures {
  private KbCardExcelTestFixtures() {}

  record Row14(String usageDate, String usageTime, String customer, String cardName,
               String merchant, double domestic, double foreign, String payMethod,
               String merchantInfo, double discount, double points, String slipStatus,
               String expectedPayDate, String approvalNo) {}

  static InputStream workbook(List<Row14> dataRows) {
    return workbookWithHeader(dataRows, new String[]{"이용일", "이용시간", "이용고객명", "이용카드명",
        "이용하신곳", "국내이용금액(원)", "해외이용금액($)", "결제방법", "가맹점정보", "할인금액",
        "적립(예상)포인트리", "상태", "결제예정일", "승인번호"});
  }

  static InputStream workbookWithHeader(List<Row14> dataRows, String[] headers) {
    try (HSSFWorkbook wb = new HSSFWorkbook()) {
      Sheet sheet = wb.createSheet("sheet 1");
      text(sheet, 0, 0, "");
      Row period = sheet.createRow(1);
      text(period, 0, "조회기간");
      text(period, 1, "2026-09-01 ~ 2026-09-24");
      Row summary = sheet.createRow(2);
      text(summary, 0, "카드번호");
      text(summary, 1, "전체");
      Row headerRow = sheet.createRow(6);
      for (int c = 0; c < headers.length; c++) headerRow.createCell(c).setCellValue(headers[c]);

      int r = 7;
      for (Row14 row : dataRows) {
        Row xr = sheet.createRow(r++);
        xr.createCell(0).setCellValue(row.usageDate());
        xr.createCell(1).setCellValue(row.usageTime());
        xr.createCell(2).setCellValue(row.customer());
        xr.createCell(3).setCellValue(row.cardName());
        xr.createCell(4).setCellValue(row.merchant());
        xr.createCell(5).setCellValue(row.domestic());
        xr.createCell(6).setCellValue(row.foreign());
        xr.createCell(7).setCellValue(row.payMethod());
        xr.createCell(8).setCellValue(row.merchantInfo());
        xr.createCell(9).setCellValue(row.discount());
        xr.createCell(10).setCellValue(row.points());
        xr.createCell(11).setCellValue(row.slipStatus());
        xr.createCell(12).setCellValue(row.expectedPayDate());
        xr.createCell(13).setCellValue(row.approvalNo());
      }

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
