package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.util.List;

/**
 * EasyShop 실데이터 import 요청 — 18개 CSV 행 단위 그대로 받는다.
 * 컬럼 계약은 docs/easy/EASYSHOP_IMPORT_PLAN.md를 따른다.
 * ledger_type별로 매출/비용/입출금을 구분하고, 사업장별로 분리 staging한다.
 */
public record EasyShopImportRequest(
    Long businessId,
    String sourceName,
    List<EasyShopRow> rows
) {
  public record EasyShopRow(
      String ledgerType,
      String date,
      String vendor,
      String account,
      String memo,
      String itemType,
      String depositAmount,
      String withdrawalAmount,
      String fee,
      String accountCategory,
      String itemDesc,
      String supplyAmount,
      String vat,
      String taxExemptSupply,
      String totalAmount,
      String cardFee,
      String receiptMethod,
      String payMethod,
      String business,
      String bizRegNo,
      String fiscalYear,
      String businessCode,
      String refNo,
      String menuPath,
      String raw,
      /**
       * CSV provenance용 원본 행번호 (optional).
       * 헤더를 제외한 첫 data row = 1. payload를 CSV 중간부터 만들더라도
       * 원본 row 번호를 그대로 넣는다 (예: CSV 2번째 행만 보내면 2).
       * 없으면 기존 동작(배치 내 순서 #row-N)을 유지한다.
       * 자동수집 externalRef 확장은 별도 필드로 하며 이 필드를 재사용하지 않는다.
       */
      Integer sourceRowNo
  ) {
    /** sourceRowNo 없이 호출하는 기존 경로용 (JSON 누락 시 Jackson도 null로 바인딩). */
    public EasyShopRow(
        String ledgerType,
        String date,
        String vendor,
        String account,
        String memo,
        String itemType,
        String depositAmount,
        String withdrawalAmount,
        String fee,
        String accountCategory,
        String itemDesc,
        String supplyAmount,
        String vat,
        String taxExemptSupply,
        String totalAmount,
        String cardFee,
        String receiptMethod,
        String payMethod,
        String business,
        String bizRegNo,
        String fiscalYear,
        String businessCode,
        String refNo,
        String menuPath,
        String raw) {
      this(ledgerType, date, vendor, account, memo, itemType, depositAmount,
          withdrawalAmount, fee, accountCategory, itemDesc, supplyAmount, vat,
          taxExemptSupply, totalAmount, cardFee, receiptMethod, payMethod, business,
          bizRegNo, fiscalYear, businessCode, refNo, menuPath, raw, null);
    }
  }
}
