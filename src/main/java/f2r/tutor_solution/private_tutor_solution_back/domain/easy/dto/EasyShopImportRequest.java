package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.util.List;

/**
 * EasyShop 실데이터 import 요청 — Claude 추출 CSV 1건을 그대로 받는다.
 * 컬럼 계약은 docs/easy/EASYSHOP_IMPORT_PLAN.md를 따른다.
 * 사업체 코드가 섞인 파일은 사업장별로 자동 분리 staging한다 (추측 배정 없음).
 */
public record EasyShopImportRequest(
    Long businessId,
    String sourceName,
    List<EasyShopRow> rows
) {
  public record EasyShopRow(
      String date,
      String direction,
      String amount,
      String counterparty,
      String description,
      String payMethod,
      String accountName,
      String businessCode,
      String refNo,
      String raw
  ) {
  }
}
