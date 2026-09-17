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
      String raw
  ) {
  }
}
